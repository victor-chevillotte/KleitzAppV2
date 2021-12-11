package com.example.visio_conduits;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.visio_conduits.utils.SPUtils;
import com.example.visio_conduits.utils.Utils;
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

    public boolean isScanningTags = false;
    public boolean mIsActiveDisconnect = true;

    private final Timer mDisconnectTimer = new Timer();
    private DisconnectTimerTask timerTask;
    private long timeCountCur;
    private final long period = 1000 * 30;
    private long lastTouchTime = System.currentTimeMillis();

    public static final String FAV_TAGS_FILE_NAME = "BTFavTagList.xml";
    public static final String FAV_DEVICES_FILE_NAME = "BTFavDeviceList.xml";


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
            if(isScanningTags) {
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

    //------------------获取定位权限--------------------------------
    private static final int ACCESS_FINE_LOCATION_PERMISSION_REQUEST = 100;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST = 101;
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_REQUEST=102;
    private static final int REQUEST_ACTION_LOCATION_SETTINGS = 3;

    public void checkLocationEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_FINE_LOCATION_PERMISSION_REQUEST);
            }
        }
        if (!isLocationEnabled()) {
            Utils.alert(this, R.string.get_location_permission, getString(R.string.tips_open_the_ocation_permission), R.drawable.webtext, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, REQUEST_ACTION_LOCATION_SETTINGS);
            });
        }
    }
    public void checkReadWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_REQUEST);
            }
        }
    }
    public boolean isLocationEnabled() {
        int locationMode;
        try {
            locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return locationMode != Settings.Secure.LOCATION_MODE_OFF;
    }
}
