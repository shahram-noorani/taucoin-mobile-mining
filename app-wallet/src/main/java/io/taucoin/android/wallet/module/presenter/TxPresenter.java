/**
 * Copyright 2018 Taucoin Core Developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.taucoin.android.wallet.module.presenter;

import com.github.naturs.logger.Logger;

import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;
import java.util.List;

import io.taucoin.android.wallet.MyApplication;
import io.taucoin.android.wallet.base.TransmitKey;
import io.taucoin.android.wallet.db.entity.KeyValue;
import io.taucoin.android.wallet.db.entity.TransactionHistory;
import io.taucoin.android.wallet.module.bean.RawTxList;
import io.taucoin.android.wallet.module.model.ITxModel;
import io.taucoin.android.wallet.module.model.TxModel;
import io.taucoin.android.wallet.module.view.main.iview.ISendReceiveView;
import io.taucoin.android.wallet.net.callback.TAUObserver;
import io.taucoin.core.Transaction;
import io.taucoin.foundation.net.callback.DataResult;
import io.taucoin.foundation.net.callback.LogicObserver;
import io.taucoin.foundation.net.exception.CodeException;
import io.taucoin.foundation.util.StringUtil;
import sun.misc.BASE64Encoder;

public class TxPresenter {
    private ISendReceiveView mSendReceiveView;
    private ITxModel mTxModel;

    public TxPresenter() {
        mTxModel = new TxModel();
    }

    public TxPresenter(ISendReceiveView sendView) {
        mTxModel = new TxModel();
        mSendReceiveView = sendView;
    }

    public void queryTransactionHistory(int pageNo, String time) {
        mTxModel.queryTransactionHistory(pageNo, time, new LogicObserver<List<TransactionHistory>>(){

            @Override
            public void handleData(List<TransactionHistory> transactionHistories) {
                mSendReceiveView.loadTransactionHistory(transactionHistories);
                mSendReceiveView.finishRefresh();
                mSendReceiveView.finishLoadMore();
            }
        });
    }

    public void getTxRecords(LogicObserver<Boolean> observer) {
        Logger.i("getAddOuts start");
        mTxModel.getTxRecords(new TAUObserver<DataResult<RawTxList>>(){

            @Override
            public void handleError(String msg, int msgCode) {
                super.handleError(msg, msgCode);
                observer.onNext(false);
            }

            @Override
            public void handleData(DataResult<RawTxList> listDataResult) {
                super.handleData(listDataResult);
                Logger.i("getAddOuts success");
                if(listDataResult != null && listDataResult.getData() != null){
                    Logger.i("getAddOuts success");
                    mTxModel.saveTxRecords(listDataResult.getData(), observer);
                }else{
                    Logger.i("getAddOuts success = 0");
                    observer.onNext(true);
                }
            }
        });
    }

    public void handleSendTransaction(TransactionHistory txBean, LogicObserver<Boolean> observer){
        createTransaction(txBean, observer);
    }

    private void createTransaction(TransactionHistory txBean, LogicObserver<Boolean> observer){
        mTxModel.createTransaction(txBean, new LogicObserver<Transaction>() {
            @Override
            public void handleData(Transaction transaction) {
                sendRawTransaction(transaction, observer);
            }

            @Override
            public void handleError(int code, String msg) {
                observer.handleError(code, msg);
            }
        });
    }

    public void sendRawTransaction(Transaction transaction, LogicObserver<Boolean> observer){
        KeyValue keyValue = MyApplication.getKeyValue();
        if(keyValue == null) {
            observer.onError(CodeException.getError());
            return;
        }
        if(StringUtil.isSame(keyValue.getMiningState(), TransmitKey.MiningState.Start) &&
            MyApplication.getRemoteConnector().isInit()){
            MyApplication.getRemoteConnector().submitTransaction(transaction);
        }else{
            mTxModel.sendRawTransaction(transaction, observer);
        }
    }
}