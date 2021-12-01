package com.example.uhf_bt;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.example.uhf_bt.utils.NumberTool;
import com.example.uhf_bt.utils.Utils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanTagActivity extends BaseActivity implements View.OnClickListener {

    public static ScanTagActivity fa;
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    public String tagUID = "";
    private final static String TAG = "ScanListActivity";
    private static final int REQUEST_ENABLE_BT = 2;

    public BluetoothAdapter mBtAdapter = null;

    public static final String SHOW_HISTORY_CONNECTED_LIST = "showHistoryConnectedList";
    public static final String TAG_DATA = "tagData";
    public static final String TAG_EPC = "tagEpc";
    public static final String TAG_TID = "tagTid";
    public static final String TAG_LEN = "tagLen";
    public static final String TAG_COUNT = "tagCount";
    public static final String TAG_RSSI = "tagRssi";
    private TextView device_battery;

    public final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
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
    private ListView LvTags;
    private Button InventoryLoop, btInventory, btStop;//
    private Button btClear, settings_button;
    private TextView tv_count, tv_total, tv_time;
    private boolean isExit = false;
    private long total = 0;
    private SimpleAdapter adapter;
    private HashMap<String, String> map;
    private ArrayList<HashMap<String, String>> tagList;
    private List<String> tempDatas = new ArrayList<>();
    private RadioButton rbEPC, rbEPC_TID, rbEPC_TID_USER;

    private AlertDialog mDialog;
    private EditText etUserPtr, etUserLen;

    private long mStrTime;
    private ExecutorService executorService;

    private ConnectStatus mConnectStatus = new ConnectStatus();

    //--------------------------------------获取 解析数据-------------------------------------------------
    final int FLAG_START = 0;//开始
    final int FLAG_STOP = 1;//停止
    final int FLAG_UHFINFO = 2;
    final int FLAG_UHFINFO_LIST = 5;
    final int FLAG_UPDATE_TIME = 3; // 更新时间
    final int FLAG_GET_MODE = 4; // 获取模式
    final int FLAG_SUCCESS = 10;//成功
    final int FLAG_FAIL = 11;//失败
    final int FLAG_SET_SUCC = 12;
    final int FLAG_SET_FAIL = 13;

    boolean isRunning = false;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FLAG_STOP:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //停止成功
                        btClear.setEnabled(true);
                        btStop.setEnabled(false);
                        InventoryLoop.setEnabled(true);
                        btInventory.setEnabled(true);
                    } else {
                        //停止失败
                        Utils.playSound(2);
                        showToast(R.string.uhf_msg_inventory_stop_fail);
                    }
                    break;
                case FLAG_START:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //开始读取标签成功
                        btClear.setEnabled(false);
                        btStop.setEnabled(true);
                        InventoryLoop.setEnabled(false);
                        btInventory.setEnabled(false);
                    } else {
                        //开始读取标签失败
                        Utils.playSound(2);
                    }
                    break;
                case FLAG_UHFINFO_LIST:
                    List<UHFTAGInfo> list = ( List<UHFTAGInfo>) msg.obj;
                    addEPCToList(list);
                    break;
                case FLAG_UHFINFO:
                    UHFTAGInfo info = (UHFTAGInfo) msg.obj;
                    addEPCToList(info);
                    Utils.playSound(1);
                    break;
                case FLAG_UPDATE_TIME:
                    float useTime = (System.currentTimeMillis() - mStrTime) / 1000.0F;
                    tv_time.setText(NumberTool.getPointDouble(loopFlag ? 1 : 3, useTime) + "s");
                    break;
                case FLAG_SET_SUCC:
                    showToast("success");
                    break;
                case FLAG_SET_FAIL:
                    showToast("fail");
                    break;
                case FLAG_GET_MODE:
                    byte[] data = (byte[]) msg.obj;
                    if (data != null) {
                        if (data[0] == 0) {
                            rbEPC.setChecked(true);
                        } else if (data[0] == 1) {
                            rbEPC_TID.setChecked(true);
                        } else if (data.length >= 3 && data[0] == 2) {
                            rbEPC_TID_USER.setChecked(true);
                            etUserPtr.setText(String.valueOf(data[1]));
                            etUserLen.setText(String.valueOf(data[2]));
                        } else {
                            rbEPC.setChecked(false);
                            rbEPC_TID.setChecked(false);
                            rbEPC_TID_USER.setChecked(false);
                        }
                        if (showToastFlag) showToast("success");
                    } else {
                        if (showToastFlag) showToast("fail");
                        rbEPC.setChecked(false);
                        rbEPC_TID.setChecked(false);
                        rbEPC_TID_USER.setChecked(false);
                    }
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
        Log.i(TAG, "UHFReadTagFragment.onActivityCreated");
        uhf.setKeyEventCallback(new KeyEventCallback() {
            @Override
            public void onKeyDown(int keycode) {
                Log.d(TAG, "  keycode =" + keycode + "   ,isExit=" + isExit);
                if (!isExit && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    if (loopFlag) {
                        stopInventory();
                    } else {
                        startThread();
                    }
                }
            }
        });
        addConnectStatusNotice(mConnectStatus);
        fa = this;
        if (uhf.getConnectStatus() == ConnectionStatus.DISCONNECTED)
            finish();
        remoteBTAdd = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        remoteBTName = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        tagUID = getIntent().getStringExtra("TAGUID");
        initUI();
        IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);
        //checkLocationEnable(); à mettre en place ulterieurement
        Utils.initSound(getApplicationContext());
    }


    @Override
    protected void onDestroy() {
        Utils.freeSound();
        unregisterReceiver(bluetoothBroadcastReceiver);
        isExit = true;
        removeConnectStatusNotice((IConnectStatus) mConnectStatus);
        super.onDestroy();
    }

    private void initUI() {
        setContentView(R.layout.fragment_uhf_new_read_tag);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        device_battery = (TextView) findViewById(R.id.device_battery);

        settings_button = (Button) findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);

        executorService = Executors.newFixedThreadPool(3);
        isExit = false;
        LvTags = (ListView) findViewById(R.id.LvTags);
        btInventory = (Button) findViewById(R.id.btInventory);
        InventoryLoop = (Button) findViewById(R.id.InventoryLoop);
        btStop = (Button) findViewById(R.id.btStop);
        btStop.setEnabled(false);
        btClear = (Button) findViewById(R.id.btClear);
        tv_count = (TextView) findViewById(R.id.tv_count);
        tv_total = (TextView) findViewById(R.id.tv_total);
        tv_time = (TextView) findViewById(R.id.tv_time);

        rbEPC = (RadioButton) findViewById(R.id.rbEPC);
        rbEPC.setOnClickListener(this);
        rbEPC_TID = (RadioButton) findViewById(R.id.rbEPC_TID);
        rbEPC_TID.setOnClickListener(this);
        rbEPC_TID_USER = (RadioButton) findViewById(R.id.rbEPC_TID_USER);
        rbEPC_TID_USER.setOnClickListener(this);

        InventoryLoop.setOnClickListener(this);
        btInventory.setOnClickListener(this);
        btClear.setOnClickListener(this);
        btStop.setOnClickListener(this);
        tagList = new ArrayList<HashMap<String, String>>();
        adapter = new SimpleAdapter(this, tagList, R.layout.listtag_items,
                new String[]{ScanTagActivity.TAG_DATA, ScanTagActivity.TAG_LEN, ScanTagActivity.TAG_COUNT, ScanTagActivity.TAG_RSSI},
                new int[]{R.id.TvTagUii, R.id.TvTagLen, R.id.TvTagCount,
                        R.id.TvTagRssi});
        LvTags.setAdapter(adapter);
        clearData();

        initFilter();
    }

    private CheckBox cbFilter;
    private ViewGroup layout_filter;
    private Button btnSetFilter;
    private void initFilter() {
        layout_filter = (ViewGroup) findViewById(R.id.layout_filter);
        layout_filter.setVisibility(View.GONE);
        cbFilter = (CheckBox) findViewById(R.id.cbFilter);
        cbFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                layout_filter.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        final EditText etLen = (EditText) findViewById(R.id.etLen);
        final EditText etPtr = (EditText) findViewById(R.id.etPtr);
        final EditText etData = (EditText) findViewById(R.id.etData);
        final RadioButton rbEPC = (RadioButton) findViewById(R.id.rbEPC_filter);
        final RadioButton rbTID = (RadioButton) findViewById(R.id.rbTID_filter);
        final RadioButton rbUser = (RadioButton) findViewById(R.id.rbUser_filter);
        btnSetFilter = (Button) findViewById(R.id.btSet);

        btnSetFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int filterBank = RFIDWithUHFUART.Bank_EPC;
                if (rbEPC.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_EPC;
                } else if (rbTID.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_TID;
                } else if (rbUser.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_USER;
                }
                if (etLen.getText().toString() == null || etLen.getText().toString().isEmpty()) {
                    showToast("数据长度不能为空");
                    return;
                }
                if (etPtr.getText().toString() == null || etPtr.getText().toString().isEmpty()) {
                    showToast("起始地址不能为空");
                    return;
                }
                int ptr = Utils.toInt(etPtr.getText().toString(), 0);
                int len = Utils.toInt(etLen.getText().toString(), 0);
                String data = etData.getText().toString().trim();
                if (len > 0) {
                    String rex = "[\\da-fA-F]*"; //匹配正则表达式，数据为十六进制格式
                    if (data == null || data.isEmpty() || !data.matches(rex)) {
                        showToast("过滤的数据必须是十六进制数据");
//                        playSound(2);
                        return;
                    }

                    int l = data.replace(" ", "").length();
                    if (len <= l * 4) {
                        if(l % 2 != 0)
                            data += "0";
                    } else {
                        showToast(R.string.uhf_msg_set_filter_fail2);
                        return;
                    }

                    if (uhf.setFilter(filterBank, ptr, len, data)) {
                        showToast(R.string.uhf_msg_set_filter_succ);
                    } else {
                        showToast(R.string.uhf_msg_set_filter_fail);
                    }
                } else {
                    //禁用过滤
                    String dataStr = "00";
                    if (uhf.setFilter(RFIDWithUHFUART.Bank_EPC, 0, 0, dataStr)
                            && uhf.setFilter(RFIDWithUHFUART.Bank_TID, 0, 0, dataStr)
                            && uhf.setFilter(RFIDWithUHFUART.Bank_USER, 0, 0, dataStr)) {
                        showToast(R.string.msg_disable_succ);
                    } else {
                        showToast(R.string.msg_disable_fail);
                    }
                }
                cbFilter.setChecked(false);
            }
        });

        rbEPC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbEPC.isChecked()) {
                    etPtr.setText("32");
                }
            }
        });
        rbTID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbTID.isChecked()) {
                    etPtr.setText("0");
                }
            }
        });
        rbUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbUser.isChecked()) {
                    etPtr.setText("0");
                }
            }
        });
    }


    private void setViewsEnabled(boolean enabled) {
        InventoryLoop.setEnabled(enabled);
        btInventory.setEnabled(enabled);
        cbFilter.setEnabled(enabled);
        rbEPC.setEnabled(enabled);
        rbEPC_TID.setEnabled(enabled);
        rbEPC_TID_USER.setEnabled(enabled);
    }

    Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    int delay = 1*1000; //Delay for 1 seconds  One second = 1000 milliseconds.

    @Override
    public void onResume() {
        //start handler as activity become visible

        handlerRefreshBattery.postDelayed( runnable = new Runnable() {
            public void run() {
                device_battery.setText(uhf.getBattery() + "%");

                handlerRefreshBattery.postDelayed(runnable, delay);
            }
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

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.settings_button:
                showToast("Chargement des réglages...");
                Intent intent=new Intent(ScanTagActivity.this, UHFSettingsActivity.class);
                ScanTagActivity.this.startActivity(intent);
                break;
            case R.id.btClear:
                clearData();
                break;
            case R.id.InventoryLoop:
                startThread();
                break;
            case R.id.btInventory:
                inventory();
                break;
            case R.id.btStop:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    stopInventory();
                }
                break;
            case R.id.rbEPC:
                executorService.execute(epcModeRunnable);
                break;
            case R.id.rbEPC_TID:
                executorService.execute(epcTidModeRunnable);
                break;
            case R.id.rbEPC_TID_USER:
                alertSet();
                break;
            default:
                break;
        }
    }

    private Runnable epcModeRunnable = new Runnable() {
        @Override
        public void run() {
            setMode(Mode.EPC);
        }
    };

    private Runnable epcTidModeRunnable = new Runnable() {
        @Override
        public void run() {
            setMode(Mode.EPC_TID);
        }
    };

    private Runnable epcTidUserModeRunnable = new Runnable() {
        @Override
        public void run() {
            setMode(Mode.EPC_TID_USER);
        }
    };

    public enum  Mode {
        EPC, EPC_TID, EPC_TID_USER
    }

    private void setMode(Mode mode) {
        switch (mode) {
            case EPC:
                if (uhf.setEPCMode()) {
                    handler.sendEmptyMessage(FLAG_SET_SUCC);
                } else {
                    handler.sendEmptyMessage(FLAG_SET_FAIL);
                }
                break;
            case EPC_TID:
                if (uhf.setEPCAndTIDMode()) {
                    handler.sendEmptyMessage(FLAG_SET_SUCC);
                } else {
                    handler.sendEmptyMessage(FLAG_SET_FAIL);
                }
                break;
            case EPC_TID_USER:
                String strUserPtr = etUserPtr.getText().toString();
                String strUserLen = etUserLen.getText().toString();
                int userPtr = 0;
                int userLen = 6;
                if (!TextUtils.isEmpty(strUserPtr)) {
                    userPtr = Integer.valueOf(strUserPtr);
                }
                if (!TextUtils.isEmpty(strUserLen)) {
                    userLen = Integer.valueOf(strUserLen);
                }
                if (uhf.setEPCAndTIDUserMode(userPtr, userLen)) {
                    handler.sendEmptyMessage(FLAG_SET_SUCC);
                } else {
                    handler.sendEmptyMessage(FLAG_SET_FAIL);
                }
                break;
        }
    }

    private AlertDialog getAlert(View view, String title, String message, boolean cancelable, DialogInterface.OnClickListener positiveListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setIcon(R.drawable.webtext);
        if(view != null) {
            builder.setView(view);
        } else {
            builder.setMessage(message);
        }
        builder.setCancelable(cancelable);
        if(positiveListener != null) {
            builder.setPositiveButton(R.string.ok, positiveListener);
        } else {
            builder.setNegativeButton(R.string.close, null);
        }
        return builder.create();
    }

    private void alertSet() {
        if (mDialog == null) {
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_epc_tid_user, null);
            etUserPtr = view.findViewById(R.id.etUserPtr);
            etUserLen = view.findViewById(R.id.etUserLen);
            DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    executorService.execute(epcTidUserModeRunnable);
                }
            };
            mDialog = getAlert(view, "EPC+TID+USER", null, false, positiveListener);
        }
        if (!mDialog.isShowing()) {
            mDialog.show();
        }
    }

    private void clearData() {
        tv_count.setText("0");
        tv_total.setText("0");
        tv_time.setText("0s");
        tagList.clear();
        tempDatas.clear();
        total = 0;
        adapter.notifyDataSetChanged();
    }

    /**
     * 停止识别
     */
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
        isScanning = false;
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
                    getMode(false);
                    setViewsEnabled(true);
                }

                cbFilter.setEnabled(true);
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                isScanning = false;
                btClear.setEnabled(true);
                btStop.setEnabled(false);
                setViewsEnabled(false);

                cbFilter.setChecked(false);
                cbFilter.setEnabled(false);
            }
        }
    }

    private boolean showToastFlag;
    private Runnable getModeRunnable = new Runnable() {
        @Override
        public void run() {
            if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                byte[] data = uhf.getEPCAndTIDUserMode();
                Message msg = handler.obtainMessage(FLAG_GET_MODE, data);
                handler.sendMessage(msg);
            }
        }
    };

    private void getMode(boolean showToast) {
        showToastFlag = showToast;
        executorService.execute(getModeRunnable);
    }

    private void inventory() {
        mStrTime = System.currentTimeMillis();
        UHFTAGInfo uhftagInfo = uhf.inventorySingleTag();
        if (uhftagInfo != null) {
            Message msg = handler.obtainMessage(FLAG_UHFINFO);
            msg.obj = uhftagInfo;
            handler.sendMessage(msg);
        }
        handler.sendEmptyMessage(FLAG_UPDATE_TIME);
    }

    public synchronized void startThread() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        cbFilter.setChecked(false);
        new TagThread().start();
    }

    class TagThread extends Thread {

        public void run() {
            Message msg = handler.obtainMessage(FLAG_START);
            if (uhf.startInventoryTag()) {
                loopFlag = true;
                isScanning = true;
                mStrTime = System.currentTimeMillis();
                msg.arg1 = FLAG_SUCCESS;
            } else {
                msg.arg1 = FLAG_FAIL;
            }
            handler.sendMessage(msg);
            isRunning = false;//执行完成设置成false
            long startTime=System.currentTimeMillis();
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if(list==null || list.size()==0){
                    SystemClock.sleep(1);
                }else{
                    Utils.playSound(1);
                    handler.sendMessage(handler.obtainMessage(FLAG_UHFINFO_LIST, list));
                }
                if(System.currentTimeMillis()-startTime>100){
                    startTime=System.currentTimeMillis();
                    handler.sendEmptyMessage(FLAG_UPDATE_TIME);
                }

            }
            stopInventory();
        }
    }

    private synchronized   List<UHFTAGInfo> getUHFInfo() {
        List<UHFTAGInfo> list = uhf.readTagFromBufferList_EpcTidUser();
        return list;
    }

    /**
     * 添加EPC到列表中
     *
     * @param uhftagInfo
     */
    private void addEPCToList(UHFTAGInfo uhftagInfo) {
        if (!TextUtils.isEmpty(uhftagInfo.getEPC()) && uhftagInfo.getEPC() != tagUID) {
            int index = checkIsExist(uhftagInfo.getEPC());

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("EPC:");
            stringBuilder.append(uhftagInfo.getEPC());
            if (!TextUtils.isEmpty(uhftagInfo.getTid())) {
                stringBuilder.append("\r\nTID:");
                stringBuilder.append(uhftagInfo.getTid());
            }
            if (!TextUtils.isEmpty(uhftagInfo.getUser())) {
                stringBuilder.append("\r\nUSER:");
                stringBuilder.append(uhftagInfo.getUser());
            }

            map = new HashMap<String, String>();
            map.put(ScanTagActivity.TAG_EPC, uhftagInfo.getEPC());
            map.put(ScanTagActivity.TAG_DATA, stringBuilder.toString());
            map.put(ScanTagActivity.TAG_COUNT, String.valueOf(1));
            map.put(ScanTagActivity.TAG_RSSI, uhftagInfo.getRssi());
            // getAppContext().uhfQueue.offer(epc + "\t 1");
            if (index == -1) {
                tagList.add(map);
                tempDatas.add(uhftagInfo.getEPC());
                tv_count.setText("" + adapter.getCount());
            } else {
                int tagCount = Integer.parseInt(tagList.get(index).get(ScanTagActivity.TAG_COUNT), 10) + 1;
                map.put(ScanTagActivity.TAG_COUNT, String.valueOf(tagCount));
                tagList.set(index, map);
            }
            tv_total.setText(String.valueOf(++total));
            adapter.notifyDataSetChanged();
        }
    }
    private void addEPCToList(List<UHFTAGInfo> list) {
        for(int k=0;k<list.size();k++){
            UHFTAGInfo uhftagInfo=list.get(k);
            if (!TextUtils.isEmpty(uhftagInfo.getEPC())) {
                int index = checkIsExist(uhftagInfo.getEPC());

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("EPC:");
                stringBuilder.append(uhftagInfo.getEPC());
                if (!TextUtils.isEmpty(uhftagInfo.getTid())) {
                    stringBuilder.append("\r\nTID:");
                    stringBuilder.append(uhftagInfo.getTid());
                }
                if (!TextUtils.isEmpty(uhftagInfo.getUser())) {
                    stringBuilder.append("\r\nUSER:");
                    stringBuilder.append(uhftagInfo.getUser());
                }

                map = new HashMap<String, String>();
                map.put(ScanTagActivity.TAG_EPC, uhftagInfo.getEPC());
                map.put(ScanTagActivity.TAG_DATA, stringBuilder.toString());
                map.put(ScanTagActivity.TAG_COUNT, String.valueOf(1));
                map.put(ScanTagActivity.TAG_RSSI, uhftagInfo.getRssi());
                // getAppContext().uhfQueue.offer(epc + "\t 1");
                if (index == -1) {
                    tagList.add(map);
                    tempDatas.add(uhftagInfo.getEPC());
                    tv_count.setText("" + adapter.getCount());
                } else {
                    int tagCount = Integer.parseInt(tagList.get(index).get(ScanTagActivity.TAG_COUNT), 10) + 1;
                    map.put(ScanTagActivity.TAG_COUNT, String.valueOf(tagCount));
                    tagList.set(index, map);
                }
                tv_total.setText(String.valueOf(++total));
            }
        }
        adapter.notifyDataSetChanged();
    }
    /**
     * 判断EPC是否在列表中
     *
     * @param epc 索引
     * @return
     */
    public int checkIsExist(String epc) {
        if (TextUtils.isEmpty(epc)) {
            return -1;
        }
        return binarySearch(tempDatas, epc);
    }

    /**
     * 二分查找，找到该值在数组中的下标，否则为-1
     */
    static int binarySearch(List<String> array, String src) {
        int left = 0;
        int right = array.size() - 1;
        // 这里必须是 <=
        while (left <= right) {
            if (compareString(array.get(left), src)) {
                return left;
            } else if (left != right) {
                if (compareString(array.get(right), src))
                    return right;
            }
            left++;
            right--;
        }
        return -1;
    }

    static boolean compareString(String str1, String str2) {
        if (str1.length() != str2.length()) {
            return false;
        } else if (str1.hashCode() != str2.hashCode()) {
            return false;
        } else {
            char[] value1 = str1.toCharArray();
            char[] value2 = str2.toCharArray();
            int size = value1.length;
            for (int k = 0; k < size; k++) {
                if (value1[k] != value2[k]) {
                    return false;
                }
            }
            return true;
        }
    }
    /* A mettre en place ulterieurement
    private static final int ACCESS_FINE_LOCATION_PERMISSION_REQUEST = 100;
    private static final int REQUEST_ACTION_LOCATION_SETTINGS = 3;

    private void checkLocationEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_FINE_LOCATION_PERMISSION_REQUEST);
            }
        }
        if (!isLocationEnabled()) {
            Utils.alert(this, R.string.get_location_permission, getString(R.string.tips_open_the_ocation_permission), R.drawable.webtext, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(intent, REQUEST_ACTION_LOCATION_SETTINGS);
                }
            });
        }
    }

    private boolean isLocationEnabled() {
        int locationMode = 0;
        String locationProviders;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } else {
            locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }*/

}
