package io.taucoin.manager;

import io.taucoin.config.SystemProperties;
import io.taucoin.core.*;
import io.taucoin.crypto.HashUtil;
import io.taucoin.db.BlockStore;
import io.taucoin.db.ByteArrayWrapper;
import io.taucoin.listener.CompositeTaucoinListener;
import io.taucoin.listener.TaucoinListener;
import io.taucoin.net.client.PeerClient;
import io.taucoin.net.rlpx.discover.UDPListener;
import io.taucoin.sync.SyncManager;
import io.taucoin.net.peerdiscovery.PeerDiscovery;
import io.taucoin.net.rlpx.discover.NodeManager;
import io.taucoin.net.server.ChannelManager;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import static io.taucoin.config.SystemProperties.CONFIG;
import static io.taucoin.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * WorldManager is a singleton containing references to different parts of the system.
 *
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
@Singleton
public class WorldManager {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private TaucoinListener listener;

    private Blockchain blockchain;

    private Repository repository;

    private Wallet wallet;

    private PeerClient activePeer;

    private PeerDiscovery peerDiscovery;

    private BlockStore blockStore;

    private ChannelManager channelManager;

    private AdminInfo adminInfo;

    private NodeManager nodeManager;

    private SyncManager syncManager;

    private PendingState pendingState;

    private UDPListener discoveryServer;

    private volatile boolean isDiscoveryRunning = false;

    SystemProperties config = SystemProperties.CONFIG;

    @Inject
    public WorldManager(TaucoinListener listener, Blockchain blockchain, Repository repository, Wallet wallet, PeerDiscovery peerDiscovery
                        , BlockStore blockStore, ChannelManager channelManager, AdminInfo adminInfo, NodeManager nodeManager, SyncManager syncManager
                        , PendingState pendingState) {
        logger.info("World manager instantiated");
        this.listener = listener;
        this.blockchain = blockchain;
        this.repository = repository;
        this.wallet = wallet;
        this.peerDiscovery = peerDiscovery;
        this.blockStore = blockStore;
        this.channelManager = channelManager;
        this.adminInfo = adminInfo;
        this.nodeManager = nodeManager;
        this.syncManager = syncManager;
        this.pendingState = pendingState;
        this.nodeManager.setWorldManager(this);
    }

    public void init() {
        loadBlockchain();
        logger.info("chain size is {}",blockchain.getSize());
    }

    public void initSync() {

        // must be initialized after blockchain is loaded
        pendingState.init();
    }

    public void addListener(TaucoinListener listener) {
        logger.info("Ethereum listener added");
        ((CompositeTaucoinListener) this.listener).addListener(listener);
    }

    public void setDiscoveryServer(UDPListener discoveryServer) {
        this.discoveryServer = discoveryServer;
    }

    public void startPeerDiscovery() {
        /*
        if (!peerDiscovery.isStarted())
            peerDiscovery.start();
         */
        if (isDiscoveryRunning) return;

        syncManager.init();
        channelManager.init();
        discoveryServer.init();
        isDiscoveryRunning = true;
    }

    public void stopPeerDiscovery() {
        /*
        if (peerDiscovery.isStarted())
            peerDiscovery.stop();
         */
        if (!isDiscoveryRunning) return;

        if (discoveryServer.isStarted()) {
            discoveryServer.shutdown();
        }
        nodeManager.shutdown();
        isDiscoveryRunning = false;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public PeerDiscovery getPeerDiscovery() {
        return peerDiscovery;
    }

    public TaucoinListener getListener() {
        return listener;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public io.taucoin.facade.Repository getRepository() {
        return (io.taucoin.facade.Repository)repository;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setActivePeer(PeerClient peer) {
        this.activePeer = peer;
    }

    public PeerClient getActivePeer() {
        return activePeer;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public PendingState getPendingState() {
        return pendingState;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public void loadBlockchain() {

        if (!config.databaseReset())
            blockStore.load();

        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            logger.info("DB is empty - adding Genesis");

            Genesis genesis = (Genesis)Genesis.getInstance(config);
            long startTime0 = System.nanoTime();
            for (ByteArrayWrapper key : genesis.getPremine().keySet()) {
                repository.createAccount(key.getData());
                //System.out.println("consumption in create account "+(System.currentTimeMillis()-starttime));
                BigInteger power = repository.increaseforgePower(key.getData());
                logger.info("address : {} forge power : {}",Hex.toHexString(key.getData()),power);
                repository.addBalance(key.getData(), genesis.getPremine().get(key).getBalance());
            }
            long endTime0 = System.nanoTime();
            logger.info("Import accounts time: {}",((endTime0 - startTime0) / 1000000));
            logger.info("genesis block hash: {}",Hex.toHexString(Genesis.getInstance(config).getHash()));
            Object object= blockStore.getClass();
            logger.info("blockStore class : {}",((Class) object).getName());

            blockStore.saveBlock(Genesis.getInstance(config), Genesis.getInstance(config).getCumulativeDifficulty(), true);
            blockchain.setBestBlock(Genesis.getInstance(config));
            blockchain.setTotalDifficulty(Genesis.getInstance(config).getCumulativeDifficulty());

            listener.onBlock(Genesis.getInstance(config));

            logger.info("Genesis block loaded");
        } else {

            blockchain.setBestBlock(bestBlock);

            BigInteger totalDifficulty = blockStore.getTotalDifficulty();
            blockchain.setTotalDifficulty(totalDifficulty);

            logger.info("*** Loaded up to block [{}] totalDifficulty [{}] with best block hash [{}]",
                    blockchain.getBestBlock().getNumber(),
                    blockchain.getTotalDifficulty().toString(),
                    Hex.toHexString(blockchain.getBestBlock().getHash()));
        }

/* todo: return it when there is no state conflicts on the chain
        boolean dbValid = this.repository.getWorldState().validate() || bestBlock.isGenesis();
        if (!dbValid){
            logger.error("The DB is not valid for that blockchain");
            System.exit(-1); //  todo: reset the repository and blockchain
        }
*/
    }

    public void close() {
        stopPeerDiscovery();
        channelManager.shutdown();
        syncManager.stop();
        repository.close();
        blockchain.close();
    }

}
