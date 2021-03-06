package io.taucoin.http;

import io.taucoin.core.Block;
import io.taucoin.core.Transaction;
import io.taucoin.db.BlockStore;
import io.taucoin.facade.Blockchain;
import io.taucoin.http.discovery.PeersManager;
import io.taucoin.http.message.Message;
import io.taucoin.http.tau.message.*;
import io.taucoin.listener.TaucoinListener;
import io.taucoin.net.message.ReasonCode;
import io.taucoin.net.rlpx.Node;
import io.taucoin.net.tau.TauVersion;
import io.taucoin.sync2.ChainInfoManager;
import io.taucoin.sync2.IdleState;
import io.taucoin.sync2.SyncManager;
import io.taucoin.sync2.SyncQueue;
import io.taucoin.sync2.SyncStateEnum;
import io.taucoin.util.Utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

import static io.taucoin.sync2.SyncStateEnum.*;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * HTTP Module Core Manager.
 *
 * Main Functions:
 *     (1) Sync state change invoke getting chain information or getting hashes.
 *     (2) Handle inbound messages, etc, ChainInfoMessage, HashesMessage,
 *         GetBlocksMessage and so on.
 */
@Singleton
public class RequestManager extends SimpleChannelInboundHandler<Message>
        implements RequestQueue.MessageListener {

    protected static final Logger logger = LoggerFactory.getLogger("http");

    protected Blockchain blockchain;

    protected BlockStore blockstore;

    protected TaucoinListener listener;

    protected SyncManager syncManager;

    protected SyncQueue queue;

    protected RequestQueue requestQueue;

    protected ChainInfoManager chainInfoManager;

    protected PeersManager peersManager;

    private Object stateLock = new Object();
    private SyncStateEnum syncState = IDLE;

    private boolean commonAncestorFound = false;

    // Get some amount hashes in the following size.
    private static final long[] FORK_MAINTAIN_HASHES_AMOUNT
            = {6, 12, 24, 72, 144, 288, 288 * 10, 288 * 30};
    private int hashesAmountArrayIndex = 0;

    /**
     * Block number list sent in GetBlocksMessage,
     * useful if returned BLOCKS msg doesn't cover all sent numbers
     * or in case when peer is disconnected
     */
    private final List<Long> sentNumbers = Collections.synchronizedList(new ArrayList<Long>());

    /**
     * Queue with new blocks forged.
     */
    private BlockingQueue<Block> newBlocks = new LinkedBlockingQueue<>();

    /**
     * Queue with new transactions.
     */
    private BlockingQueue<Transaction> newTransactions = new LinkedBlockingQueue<>();

    private Thread blockDistributeThread;
    private Thread txDistributeThread;

    private AtomicBoolean started;

    @Inject
    public RequestManager(Blockchain blockchain, BlockStore blockstore, TaucoinListener listener,
            SyncManager syncManager, SyncQueue queue, RequestQueue requestQueue,
            ChainInfoManager chainInfoManager, PeersManager peersManager) {
        this.blockchain = blockchain;
        this.blockstore = blockstore;
        this.listener = listener;
        this.syncManager = syncManager;
        this.queue = queue;
        this.requestQueue = requestQueue;
        this.chainInfoManager = chainInfoManager;
        this.peersManager = peersManager;

        this.requestQueue.registerListener(this);
    }

    public void start() {
        if (started.get()) {
            return;
        }
        started.set(true);

        // Resending new blocks to network in loop
        this.blockDistributeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                newBlocksDistributeLoop();
            }
        }, "NewSyncThreadBlocks");
        this.blockDistributeThread.start();

        // Resending pending txs to newly connected peers
        this.txDistributeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                newTxDistributeLoop();
            }
        }, "NewSyncThreadTx");
        this.txDistributeThread.start();
    }

    public void stop() {
        if (blockDistributeThread != null) {
            blockDistributeThread.interrupt();
            blockDistributeThread = null;
        }
        if (txDistributeThread != null) {
            txDistributeThread.interrupt();
            txDistributeThread = null;
        }
    }

    public void changeSyncState(SyncStateEnum state) {
        synchronized(stateLock) {
            if (this.syncState == state) {
                return;
            }

            this.syncState = state;
        }

        switch (state) {
            case CHAININFO_RETRIEVING:
                startPullChainInfo();
                break;

            case HASH_RETRIEVING:
                startForkCoverage();
                break;

            case BLOCK_RETRIEVING:
                startBlockRetrieving();
                break;

            default:
                break;
        }
    }

    public SyncStateEnum getSyncState() {
        synchronized(stateLock) {
            return this.syncState;
        }
    }

    public boolean isHashRetrievingDone(){
        synchronized(stateLock) {
            return this.syncState == DONE_HASH_RETRIEVING;
        }
    }

    public boolean isHashRetrieving(){
        synchronized(stateLock) {
            return this.syncState == HASH_RETRIEVING;
        }
    }

    public boolean isChainInfoRetrievingDone(){
        synchronized(stateLock) {
            return this.syncState == DONE_CHAININFO_RETRIEVING;
        }
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, Message msg) throws InterruptedException {

        listener.trace(String.format("RequestManager invoke: [%s]", msg.getClass()));

        if (msg instanceof ChainInfoMessage) {
            processChainInfoMessage((ChainInfoMessage)msg);
        } else if (msg instanceof HashesMessage) {
            processHashesMessage((HashesMessage)msg);
        } else if (msg instanceof BlocksMessage) {
            processBlocksMessage((BlocksMessage)msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void processChainInfoMessage(ChainInfoMessage msg) {
        chainInfoManager.update(msg.getHeight(), msg.getPreviousBlockHash(),
                msg.getCurrentBlockHash(), msg.getTotalDiff());
        changeSyncState(DONE_CHAININFO_RETRIEVING);
    }

    private void startPullChainInfo() {
        GetChainInfoMessage message = new GetChainInfoMessage();
        requestQueue.sendMessage(message);
    }

    private void processHashesMessage(HashesMessage msg) {
        if (logger.isTraceEnabled()) {
            logger.trace("Processing BlockHashes, size [{}]", msg.getHashes().size());
        }

        List<byte[]> received = msg.getHashes();

        // treat empty hashes response as end of hashes sync
        if (received.isEmpty()) {
            changeSyncState(DONE_HASH_RETRIEVING);
        } else {
            if (syncState == HASH_RETRIEVING && !commonAncestorFound) {
                maintainForkCoverage(received, msg.getStartNumber(), msg.getReverse());
                return;
            }

            logger.error("Processing BlockHashes fatal error start {}, reverse {}",
                    msg.getStartNumber(), msg.getReverse());
        }
    }

    /*************************
     *     Fork Coverage     *
     *************************/

    private void startForkCoverage() {

        commonAncestorFound = false;
        hashesAmountArrayIndex = 0;

        long bestNumber = blockchain.getBestBlock().getNumber();
        byte[] bestHash = blockchain.getBestBlock().getHash();

        logger.debug("Start looking for common ancestor, height {}, hash {}",
                bestNumber, Hex.toHexString(bestHash));

        if (chainInfoManager.getHeight() == bestNumber + 1
                && Utils.hashEquals(chainInfoManager.getPreviousBlockHash(), bestHash)) {
            commonAncestorFound = true;
            pushBlockNumbers(bestNumber + 1);
        } else {
            sendGetBlockHashes(bestNumber,
                    FORK_MAINTAIN_HASHES_AMOUNT[hashesAmountArrayIndex++], true);
        }
    }

    private void maintainForkCoverage(List<byte[]> received, long startNumber,
            boolean reverse) {
        long ancestorNumber = startNumber;
        if (!reverse) {
            Collections.reverse(received);
            ancestorNumber = startNumber + received.size() - 1;
        }

        ListIterator<byte[]> it = received.listIterator();

        while (it.hasNext()) {
            byte[] hash = it.next();
            logger.info("isBlockExist {}", Hex.toHexString(hash));
            if (blockchain.isBlockExist(hash)) {
                commonAncestorFound = true;
                logger.info(
                        "common ancestor found: block.number {}, block.hash {}",
                        ancestorNumber, Hex.toHexString(hash));
                break;
            }
            ancestorNumber--;
        }

        if (commonAncestorFound) {
            pushBlockNumbers(ancestorNumber + 1);
            changeSyncState(DONE_HASH_RETRIEVING);
        } else {
            if (hashesAmountArrayIndex >= FORK_MAINTAIN_HASHES_AMOUNT.length) {
                logger.error("common ancestor is not found, drop");
                syncManager.reportBadAction(null);
                changeSyncState(DONE_HASH_RETRIEVING);
                // TODO: add this peer into black list
                return;
            } else {
                logger.info("Continue finding common ancestor {}",
                        blockchain.getBestBlock().getNumber() + 1);
                sendGetBlockHashes(blockchain.getBestBlock().getNumber(),
                        FORK_MAINTAIN_HASHES_AMOUNT[hashesAmountArrayIndex++], true);
            }
        }
    }

    private void pushBlockNumbers(long startNumber) {
        queue.addBlockNumbers(startNumber, chainInfoManager.getHeight());
    }

    protected void sendGetBlockHashes(long blockNumber, long maxBlocksAsk, boolean reverse) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    "send GetBlockHeaders, blockNumber [{}], maxBlocksAsk [{}], reverse [{}]",
                    blockNumber, maxBlocksAsk, reverse);
        }

        GetHashesMessage msg = new GetHashesMessage(blockNumber, maxBlocksAsk, reverse);
        requestQueue.sendMessage(msg);
    }

    private void processBlocksMessage(BlocksMessage msg) {
        if (logger.isDebugEnabled()) {
            logger.debug("Process blocks, size [{}]", msg.getBlocks().size());
        }

        List<Long> coveredNumbers = preprocessBlocksMessage(msg);

        List<Block> blocksList = msg.getBlocks();

        // return numbers not covered by response
        sentNumbers.removeAll(coveredNumbers);
        returnBlockNumbers();

        // TODO: record peer node id.
        // Here, just simply get random peer id to use orginal api.
        queue.addList(blocksList, peersManager.getRandomPeer().getId());

        if (syncState == BLOCK_RETRIEVING) {
            sendGetBlocks();
        }
    }

    private void startBlockRetrieving() {
        sendGetBlocks();
    }

    private void sendGetBlocks() {

        List<Long> numbers = queue.pollBlockNumbers();
        if (numbers.isEmpty()) {
            if(logger.isDebugEnabled()) {
                logger.debug("No more numbers in queue, idle");
            }
            changeSyncState(IDLE);
            return;
        }

        sentNumbers.clear();
        sentNumbers.addAll(numbers);

        long start = numbers.get(0);
        if (logger.isDebugEnabled()) {
            logger.debug("Send GetBlocksMessage, numbers.count [{}], start [{}]",
                    sentNumbers.size(), start);
        }

        GetBlocksMessage msg = new GetBlocksMessage(start, sentNumbers.size(), false);

        requestQueue.sendMessage(msg);
    }

    private List<Long> preprocessBlocksMessage(BlocksMessage msg) {
        List<Long> receivedNumbers = new ArrayList<>();
        long delta = msg.getReverse() ? -1 : 1;
        long number = msg.getStartNumber();

        for (Block b : msg.getBlocks()) {
            b.setNumber(number);
            receivedNumbers.add(number);
            number += delta;
        }

        return receivedNumbers;
    }

    private void returnBlockNumbers() {
        if (logger.isDebugEnabled()) {
            logger.debug("Return [{}] numbers back to store", sentNumbers.size());
        }

        synchronized (sentNumbers) {
            queue.returnBlockNumbers(sentNumbers);
        }
        sentNumbers.clear();
    }

    @Override
    public void onMessageTimeout(Message message) {
        logger.warn("Message timeout {}", message);

        if (message instanceof GetChainInfoMessage) {
            if (getSyncState() == CHAININFO_RETRIEVING) {
                changeSyncState(DONE_CHAININFO_RETRIEVING);
            }
        } else if (message instanceof GetHashesMessage) {
            if (getSyncState() == HASH_RETRIEVING) {
                changeSyncState(DONE_HASH_RETRIEVING);
            }
        } else if (message instanceof GetBlocksMessage) {
            returnBlockNumbers();
        }
    }

    public void submitNewBlock(Block block) {
        this.newBlocks.add(block);
    }

    public void submitNewTransaction(Transaction tx) {
        this.newTransactions.add(tx);
    }

    /**
     * Processing new blocks forged from queue
     */
    private void newBlocksDistributeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Block block = null;
            try {
                block = newBlocks.take();
                sendNewBlock(block);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                if (block != null) {
                    logger.error("Error broadcasting new block {} {}: ", block, e);
                } else {
                    logger.error("Error broadcasting unknown block", e);
                }
            }
        }
    }

    /**
     * Sends all pending txs from wallet to new active peers
     */
    private void newTxDistributeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Transaction tx = null;
            try {
                tx = newTransactions.take();
                sendNewTransaction(tx);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                if (tx != null) {
                    logger.error("Error sending transaction {}: ",  e);
                } else {
                    logger.error("Unknown error when sending transaction {}", e);
                }
            }
        }
    }

    private void sendNewBlock(Block block) {
        NewBlockMessage message = new NewBlockMessage(block.getNumber(),
                block.getCumulativeDifficulty(), block);
        requestQueue.sendMessage(message);
    }

    private void sendNewTransaction(Transaction tx) {
        NewTxMessage message = new NewTxMessage(tx);
        requestQueue.sendMessage(message);
    }
}
