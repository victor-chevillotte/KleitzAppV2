package com.example.visio_conduits;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.visio_conduits.utils.DBHelper;
import com.example.visio_conduits.utils.Utils;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.text.ParseException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFocusedTagActivity extends BaseActivity implements View.OnClickListener {

    @SuppressLint("StaticFieldLeak")
    public static ScanFocusedTagActivity fa;
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    public String focusedTagEPC = "";
    public String focusedTagName = "";
    public String focusedTagRoom = "";
    public String focusedTagWorkPlace = "";
    private final static String TAG = "ScanListActivity";
    public BluetoothAdapter mBtAdapter = null;
    private static final int NEW_TAG_NAME = 1;

    public TextView nameTV, roomTV, workplaceTV, EPCTV, device_battery;

    public static final String TAG_EPC = "tagEpc";
    private final DBHelper mydb = new DBHelper(this, "KleitzElec.db", null, 1, this);

    public final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        set_activity_activate_bluetooth();
                        break;
                    case BluetoothAdapter.STATE_ON:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }

            }
        }
    };
    private boolean loopFlag = false;
    private Button InventoryLoop;
    private Button btStop;
    private ProgressBar pb_distance;
    private TextView tv_FocusTagNbDetect, tv_distance;
    private boolean isExit = false;
    private long totalFocusTagDetect = 0;

    private ExecutorService executorService;

    private final ConnectStatus mConnectStatus = new ConnectStatus();

    final int FLAG_START = 0;
    final int FLAG_STOP = 1;
    final int FLAG_UHFINFO_LIST = 5;
    final int FLAG_GET_MODE = 4;
    final int FLAG_SUCCESS = 10;
    final int FLAG_FAIL = 11;
    final int FLAG_SET_SUCC = 12;
    final int FLAG_SET_FAIL = 13;

    boolean isRunning = false;
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FLAG_STOP:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //停止成功
                        btStop.setEnabled(false);
                        InventoryLoop.setEnabled(true);
                    } else {
                        //停止失败
                        Utils.playSound(2);
                        showToast(R.string.uhf_msg_inventory_stop_fail);
                    }
                    break;
                case FLAG_START:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //开始读取标签成功
                        btStop.setEnabled(true);
                        InventoryLoop.setEnabled(false);
                    } else {
                        //开始读取标签失败
                        Utils.playSound(2);
                    }
                    break;
                case FLAG_UHFINFO_LIST:
                    List<UHFTAGInfo> list = (List<UHFTAGInfo>) msg.obj;
                    try {//ici
                        addEPCToList(list);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    break;
                case FLAG_SET_SUCC:
                    showToast("success");
                    break;
                case FLAG_SET_FAIL:
                    showToast("fail");
                    break;
            }
        }
    };


    private void set_activity_activate_bluetooth() {
        uhf.free();
        connectStatusList.clear();
        finish();
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkReadWritePermission();
        checkLocationEnable();
        uhf.setKeyEventCallback(new KeyEventCallback() {
            @Override
            public void onKeyDown(int keycode) {
                if (!isExit && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    if (loopFlag) {
                        stopInventory();
                    } else {
                        startThread();
                    }
                }
            }

            public void onKeyUp(int keycode) {
                Log.d(TAG, "  keycode =" + keycode + "   ,isExit=" + isExit);
                stopInventory();
            }
        });

        addConnectStatusNotice(mConnectStatus);
        fa = this;
        if (uhf.getConnectStatus() == ConnectionStatus.DISCONNECTED)
            finish();
        remoteBTAdd = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        remoteBTName = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        focusedTagEPC = getIntent().getStringExtra(TAG_EPC);
        initUI();
        IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);
        Utils.initSound(getApplicationContext());
    }


    @Override
    protected void onDestroy() {
        Utils.freeSound();
        unregisterReceiver(bluetoothBroadcastReceiver);
        isExit = true;
        removeConnectStatusNotice(mConnectStatus);
        super.onDestroy();
    }

    @SuppressLint("Range")
    private void initUI() {
        setContentView(R.layout.activity_uhf_scan_focused_tag);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBtAdapter.isEnabled())
            finish();
        device_battery = findViewById(R.id.device_battery);

        Button settings_button = findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);

        executorService = Executors.newFixedThreadPool(3);
        isExit = false;
        InventoryLoop = findViewById(R.id.InventoryLoop);
        btStop = findViewById(R.id.btStop);
        btStop.setEnabled(false);
        tv_FocusTagNbDetect = findViewById(R.id.tv_FocusTagNbDetect);
        pb_distance = findViewById(R.id.progressBar);
        tv_distance = findViewById(R.id.FocusTagDistance);

        InventoryLoop.setOnClickListener(this);
        btStop.setOnClickListener(this);
        Button nameTag =  findViewById(R.id.InventoryFocusAddModifyTag);
        Cursor cursor = mydb.selectATag(focusedTagEPC);
        if (cursor.moveToFirst() && cursor.getCount() != 0) {
            nameTag.setText(R.string.modify_tag_name);
            focusedTagName = cursor.getString(cursor.getColumnIndex("name"));
            focusedTagRoom = cursor.getString(cursor.getColumnIndex("room"));
            focusedTagWorkPlace = cursor.getString(cursor.getColumnIndex("workplace"));
        }

        EPCTV = findViewById(R.id.FocusTagEPC);
        EPCTV.setText(focusedTagEPC);
        nameTV =  findViewById(R.id.FocusTagName);
        nameTV.setText(focusedTagName);
        roomTV = findViewById(R.id.FocusTagRoom);
        roomTV.setText(focusedTagRoom);
        workplaceTV =  findViewById(R.id.FocusTagWorkplace);
        workplaceTV.setText(focusedTagWorkPlace);

        nameTag.setOnClickListener(v -> {
            Intent myIntent = new Intent(ScanFocusedTagActivity.this, AddTagNameActivity.class);
            myIntent.putExtra("uii", focusedTagEPC);
            myIntent.putExtra("name", focusedTagName);
            myIntent.putExtra("room", focusedTagRoom);
            myIntent.putExtra("workplace", focusedTagWorkPlace);
            boolean newTag = true;
            if (!focusedTagName.equals("")) {
                newTag = false;
            }
            myIntent.putExtra("newTag", newTag);
            startActivityForResult(myIntent, NEW_TAG_NAME);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NEW_TAG_NAME) {//When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                focusedTagName = data.getStringExtra("NewFocusedTagName");
                focusedTagRoom = data.getStringExtra("NewFocusedTagRoom");
                focusedTagWorkPlace = data.getStringExtra("NewFocusedTagWorkPlace");
                nameTV.setText(focusedTagName);
                roomTV.setText(focusedTagRoom);
                workplaceTV.setText(focusedTagWorkPlace);
            }
        }
    }

    private void setViewsEnabled(boolean enabled) {
        InventoryLoop.setEnabled(enabled);
    }

    Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    int delay = 1000; //Delay for 1 seconds  One second = 1000 milliseconds.

    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {
        //start handler as activity become visible
        handlerRefreshBattery.postDelayed(runnable = () -> {
            device_battery.setText(uhf.getBattery() + "%");

            handlerRefreshBattery.postDelayed(runnable, delay);
        }, delay);
        super.onResume();
        setViewsEnabled(true);
    }

    @Override
    public void onPause() {
        handlerRefreshBattery.removeCallbacks(runnable); //stop handler when activity not visible
        super.onPause();
        if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            stopInventory();
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.settings_button:
                showToast("Chargement des réglages...");
                Intent intent = new Intent(ScanFocusedTagActivity.this, UHFSettingsActivity.class);
                ScanFocusedTagActivity.this.startActivity(intent);
                break;
            case R.id.InventoryLoop:
                startThread();
                break;
            case R.id.btStop:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    stopInventory();
                }
                break;
            default:
                break;
        }
    }

    private void stopInventory() {
        loopFlag = false;
        ConnectionStatus connectionStatus = uhf.getConnectStatus();
        Message msg = handler.obtainMessage(FLAG_STOP);
        boolean result = uhf.stopInventory();
        if (result || connectionStatus == ConnectionStatus.DISCONNECTED) {
            msg.arg1 = FLAG_SUCCESS;
        } else {
            msg.arg1 = FLAG_FAIL;
        }
        isScanningTags = false;
        handler.sendMessage(msg);
    }

    class ConnectStatus implements IConnectStatus {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                if (!loopFlag) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    getMode();
                    setViewsEnabled(true);
                }
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                isScanningTags = false;
                btStop.setEnabled(false);
                setViewsEnabled(false);
            }
        }
    }

    private final Runnable getModeRunnable = () -> {
        if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            byte[] data = uhf.getEPCAndTIDUserMode();
            Message msg = handler.obtainMessage(FLAG_GET_MODE, data);
            handler.sendMessage(msg);
        }
    };

    private void getMode() {
        executorService.execute(getModeRunnable);
    }

    public synchronized void startThread() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        new TagThread().start();
    }

    class TagThread extends Thread {

        public void run() {
            Message msg = handler.obtainMessage(FLAG_START);
            if (uhf.startInventoryTag()) {
                loopFlag = true;
                isScanningTags = true;
                msg.arg1 = FLAG_SUCCESS;
            } else {
                msg.arg1 = FLAG_FAIL;
            }
            handler.sendMessage(msg);
            isRunning = false;//执行完成设置成false
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if (list == null || list.size() == 0) {
                    SystemClock.sleep(1);
                } else {
                    handler.sendMessage(handler.obtainMessage(FLAG_UHFINFO_LIST, list));
                    Utils.playSound(1);
                }

            }
            stopInventory();
        }
    }

    private synchronized List<UHFTAGInfo> getUHFInfo() {
        return uhf.readTagFromBufferList_EpcTidUser();
    }

    @SuppressLint("SetTextI18n")
    private void addEPCToList(List<UHFTAGInfo> list) throws ParseException {
        for (int k = 0; k < list.size(); k++) {
            UHFTAGInfo uhftagInfo = list.get(k);
            if (!TextUtils.isEmpty(uhftagInfo.getEPC()) && uhftagInfo.getEPC().equals(focusedTagEPC)) {//ici
                int distance = (int) Double.parseDouble(uhftagInfo.getRssi().replaceAll(",", "."));
                distance = - distance - 40;
                if (distance <= 0) {
                    distance = 0;
                }
                tv_distance.setText(distance + " cm");
                pb_distance.setProgress(100 - distance);
                tv_FocusTagNbDetect.setText(String.valueOf(++totalFocusTagDetect));
                break;
            }
        }
    }


}
