package com.example.uhf_bt;

import android.bluetooth.BluetoothDevice;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf_bt.utils.FileUtils;
import com.example.uhf_bt.utils.SPUtils;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by WuShengjun on 2019/7/3.
 * Description:
 */

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public RFIDWithUHFBLE uhf = RFIDWithUHFBLE.getInstance();
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    private Toast toast;
    public BluetoothDevice mDevice = null;
    private final static String TAG = "BaseActivity";

    public boolean isScanning = false;
    public boolean mIsActiveDisconnect = true; // 是否主动断开连接

    private Timer mDisconnectTimer = new Timer();
    private DisconnectTimerTask timerTask;
    private long timeCountCur; // 断开时间选择
    private long period = 1000 * 30; // 隔多少时间更新一次
    private long lastTouchTime = System.currentTimeMillis(); // 上次接触屏幕操作的时间戳
    private static final int RUNNING_DISCONNECT_TIMER = 10;

    public void showToast(String text) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showToast(int resId) {
        showToast(getString(resId));
    }

    //------------连接状态监听-----------------------
    public List<IConnectStatus> connectStatusList = new ArrayList<>();

    public void addConnectStatusNotice(ConnectDeviceActivity.IConnectStatus iConnectStatus) {
        connectStatusList.add(iConnectStatus);
    }

    public void removeConnectStatusNotice(ConnectDeviceActivity.IConnectStatus iConnectStatus) {
        connectStatusList.remove(iConnectStatus);
    }

    public void updateConnectMessage(String oldName, String newName) {
        if (!TextUtils.isEmpty(oldName) && !TextUtils.isEmpty(newName)) {
            //tvAddress.setText(tvAddress.getText().toString().replace(oldName, newName)); ICI
            remoteBTName = newName;
        }
    }

    public interface IConnectStatus {
        void getStatus(ConnectionStatus connectionStatus);
    }

    public void startDisconnectTimer(long time) {
        timeCountCur = time;
        timerTask = new DisconnectTimerTask();
        mDisconnectTimer.schedule(timerTask, 0, period);
    }

    public void cancelDisconnectTimer() {
        timeCountCur = 0;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private class DisconnectTimerTask extends TimerTask {

        @Override
        public void run() {
            if(isScanning) {
                resetDisconnectTime();
            } else if (timeCountCur <= 0){
                disconnect(true);
            }
            timeCountCur -= period;
        }
    }

    public void disconnect(boolean isActiveDisconnect) {
        mIsActiveDisconnect = isActiveDisconnect; // 主动断开为true
        uhf.disconnect();
    }

    /**
     * 重置断开时间
     */
    public void resetDisconnectTime() {
        timeCountCur = SPUtils.getInstance(getApplicationContext()).getSPLong(SPUtils.DISCONNECT_TIME, 0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouchTime = System.currentTimeMillis();
        resetDisconnectTime();
        return super.dispatchTouchEvent(ev);
    }
}
