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

public class ScanFocusedTagActivity extends BaseActivity implements View.OnClickListener {

    public static ScanFocusedTagActivity fa;
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    public String focusedTagEPC = "";
    private final static String TAG = "ScanListActivity";
    private static final int REQUEST_ENABLE_BT = 2;

    public BluetoothAdapter mBtAdapter = null;

    public static final String SHOW_HISTORY_CONNECTED_LIST = "showHistoryConnectedList";
    public static final String TAG_DATA = "tagData";
    public static final String TAG_EPC = "tagEpc";
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
    private Button InventoryLoop, btStop;//
    private Button settings_button;
    private TextView tv_count, tv_total, tv_time;
    private boolean isExit = false;
    private long total = 0;
    private SimpleAdapter adapter;
    private HashMap<String, String> map;
    private ArrayList<HashMap<String, String>> tagList;
    private List<String> tempDatas = new ArrayList<>();

    private long mStrTime;
    private ExecutorService executorService;

    private ConnectStatus mConnectStatus = new ConnectStatus();

    //--------------------------------------获取 解析数据-------------------------------------------------
    final int FLAG_START = 0;//开始
    final int FLAG_STOP = 1;//停止
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
                    List<UHFTAGInfo> list = ( List<UHFTAGInfo>) msg.obj;
                    addEPCToList(list);
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
        });
        addConnectStatusNotice(mConnectStatus);
        fa = this;
        if (uhf.getConnectStatus() == ConnectionStatus.DISCONNECTED)
            finish();
        remoteBTAdd = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        remoteBTName = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        focusedTagEPC = getIntent().getStringExtra(TAG_EPC);
        showToast(focusedTagEPC);
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
        setContentView(R.layout.activity2inventoryfocus);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        device_battery = (TextView) findViewById(R.id.device_battery);

        settings_button = (Button) findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);

        executorService = Executors.newFixedThreadPool(3);
        isExit = false;
        LvTags = (ListView) findViewById(R.id.LvTags);
        InventoryLoop = (Button) findViewById(R.id.InventoryLoop);
        btStop = (Button) findViewById(R.id.btStop);
        btStop.setEnabled(false);
        /*tv_count = (TextView) findViewById(R.id.tv_count);
        tv_total = (TextView) findViewById(R.id.tv_total);
        tv_time = (TextView) findViewById(R.id.tv_time);*/

        InventoryLoop.setOnClickListener(this);
        btStop.setOnClickListener(this);
        tagList = new ArrayList<HashMap<String, String>>();
        adapter = new SimpleAdapter(this, tagList, R.layout.listtag_items,
                new String[]{ScanFocusedTagActivity.TAG_DATA, ScanFocusedTagActivity.TAG_LEN, ScanFocusedTagActivity.TAG_COUNT, ScanFocusedTagActivity.TAG_RSSI},
                new int[]{R.id.TvTagUii, R.id.TvTagLen, R.id.TvTagCount,
                        R.id.TvTagRssi});
        //LvTags.setAdapter(adapter);
        clearData();
    }


    private void setViewsEnabled(boolean enabled) {
        InventoryLoop.setEnabled(enabled);
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
                Intent intent=new Intent(ScanFocusedTagActivity.this, UHFSettingsActivity.class);
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


    private void clearData() {
//        tv_count.setText("0");
//        tv_total.setText("0");
//        tv_time.setText("0s");
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
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                isScanning = false;
                btStop.setEnabled(false);
                setViewsEnabled(false);
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
     * @param
     */
    private void addEPCToList(List<UHFTAGInfo> list) {
        for(int k=0;k<list.size();k++){
            UHFTAGInfo uhftagInfo=list.get(k);
            if (!TextUtils.isEmpty(uhftagInfo.getEPC()) && uhftagInfo.getEPC().equals(focusedTagEPC)) {//ici
                int index = checkIsExist(uhftagInfo.getEPC());
                showToast(uhftagInfo.getEPC());
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("EPC:");
                stringBuilder.append(uhftagInfo.getEPC());

                map = new HashMap<String, String>();
                map.put(ScanFocusedTagActivity.TAG_EPC, uhftagInfo.getEPC());
                map.put(ScanFocusedTagActivity.TAG_DATA, stringBuilder.toString());
                map.put(ScanFocusedTagActivity.TAG_COUNT, String.valueOf(1));
                map.put(ScanFocusedTagActivity.TAG_RSSI, uhftagInfo.getRssi());
                // getAppContext().uhfQueue.offer(epc + "\t 1");
                if (index == -1) {
                    tagList.add(map);
                    tempDatas.add(uhftagInfo.getEPC());
                    tv_count.setText("" + adapter.getCount());
                } else {
                    int tagCount = Integer.parseInt(tagList.get(index).get(ScanFocusedTagActivity.TAG_COUNT), 10) + 1;
                    map.put(ScanFocusedTagActivity.TAG_COUNT, String.valueOf(tagCount));
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
