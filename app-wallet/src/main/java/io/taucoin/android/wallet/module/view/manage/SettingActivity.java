package io.taucoin.android.wallet.module.view.manage;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import com.github.naturs.logger.Logger;
import com.mofei.tau.R;

import java.math.BigInteger;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.taucoin.android.wallet.MyApplication;
import io.taucoin.android.wallet.base.BaseActivity;
import io.taucoin.android.wallet.db.entity.KeyValue;
import io.taucoin.android.wallet.module.presenter.UserPresenter;
import io.taucoin.android.wallet.widget.InputDialog;
import io.taucoin.android.wallet.widget.ItemTextView;
import io.taucoin.foundation.net.callback.LogicObserver;

public class SettingActivity extends BaseActivity {


    @BindView(R.id.tv_trans_expiry)
    ItemTextView tvTransExpiry;
    @BindView(R.id.tv_mutable_range)
    ItemTextView tvMutableRange;
    private UserPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        ButterKnife.bind(this);
        mPresenter = new UserPresenter();
        loadView();
    }

    private void loadView() {
        KeyValue keyValue = MyApplication.getKeyValue();
        if(keyValue != null){
            tvTransExpiry.setRightText(keyValue.getTransExpiry() + "min");
            tvMutableRange.setRightText(keyValue.getMutableRange());
        }
    }

    private void updateView(KeyValue keyValue) {
        MyApplication.setKeyValue(keyValue);
        loadView();
    }

    @OnClick({R.id.tv_trans_expiry, R.id.tv_mutable_range})
    void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_trans_expiry:
                showInputDialog(0);
                break;
            case R.id.tv_mutable_range:
                showInputDialog(1);
                break;
            default:
                break;
        }
    }

    private void showInputDialog(int type){
        int inputHint = type == 0 ? R.string.setting_transaction_expiry_tip : R.string.setting_mutable_range_tip;
        new InputDialog.Builder(this)
            .setInputType(InputType.TYPE_CLASS_NUMBER)
            .setInputHint(inputHint)
            .setNegativeButton(R.string.common_cancel, (InputDialog.InputDialogListener) (dialog, text) -> dialog.cancel())
            .setPositiveButton(R.string.common_done, (InputDialog.InputDialogListener) (dialog, text) -> {
                loadView();
                try {
                    long input = new BigInteger(text).longValue();
                    if(type == 0){
                        saveTransExpiry(input);
                    }else {
                        saveMutableRange(input);
                    }
                }catch (Exception e){
                    Logger.e("new BigInteger is error", e);
                }
                dialog.cancel();
            }).create().show();
    }

    private void saveTransExpiry(long transExpiry) {
        mPresenter.saveTransExpiry(transExpiry, new LogicObserver<KeyValue>(){

            @Override
            public void handleData(KeyValue keyValue) {
                updateView(keyValue);
            }
        });
    }

    private void saveMutableRange(long mutableRange) {
        mPresenter.saveMutableRange(mutableRange, new LogicObserver<KeyValue>(){

            @Override
            public void handleData(KeyValue keyValue) {
                updateView(keyValue);
            }
        });
    }
}