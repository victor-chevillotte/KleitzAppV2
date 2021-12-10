package com.example.visio_conduits;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.example.visio_conduits.utils.NumberTool;
import com.example.visio_conduits.utils.Utils;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soup.neumorphism.NeumorphButton;
import soup.neumorphism.NeumorphImageButton;

public class ScanListActivity extends BaseActivity implements View.OnClickListener {

    public static ScanListActivity fa;
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    private final static String TAG = "ScanListActivity";
    private static final int REQUEST_ENABLE_BT = 2;

    public BluetoothAdapter mBtAdapter = null;

    public static final String SHOW_HISTORY_CONNECTED_LIST = "showHistoryConnectedList";
    public static final String TAG_DATA = "tagData";
    public static final String TAG_EPC = "tagEpc";
    public static final String TAG_LEN = "tagLen";
    public static final String TAG_COUNT = "tagCount";
    public static final String TAG_RSSI = "tagRssi";
    public static final String TAG_TYPE = "tagType";
    private TextView device_battery;
    private ProgressBar batteryPB;
    private boolean isScanning = false;

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
    private NeumorphButton  btnStart, btStop, btClear;
    private NeumorphImageButton settings_button;
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

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FLAG_STOP:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //停止成功
                        btStop.setShapeType(1);
                        btnStart.setShapeType(0);
                    } else {
                        //停止失败
                        Utils.playSound(2);
                        showToast(R.string.uhf_msg_inventory_stop_fail);
                    }
                    break;
                case FLAG_START:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //开始读取标签成功
                        btStop.setShapeType(0);
                        btnStart.setShapeType(1);
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
            @Override
            public void onKeyUp(int keycode) {
                stopInventory();
            }
        });
        addConnectStatusNotice(mConnectStatus);
        fa = this;
        if (uhf.getConnectStatus() == ConnectionStatus.DISCONNECTED)
            finish();
        remoteBTAdd = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        remoteBTName = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
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
        removeConnectStatusNotice((ScanListActivity.IConnectStatus) mConnectStatus);
        super.onDestroy();
    }

    private void initUI() {
        setContentView(R.layout.activity_uhf_scan_list);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBtAdapter.isEnabled())
            finish();
        batteryPB = (ProgressBar) findViewById(R.id.batteryPB);
        batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
        device_battery = (TextView) findViewById(R.id.device_battery);
        int precentage = uhf.getBattery();
        if (precentage >= 0) {
            device_battery.setText(precentage + "%");
            batteryPB.setProgress(precentage);
            if (precentage <= 10)
                batteryPB.setProgressTintList(ColorStateList.valueOf(Color.RED));
            else if (precentage <= 20)
                batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(255, 165, 0)));
            else
                batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
        }
        handlerRefreshBattery.postDelayed(runnable, delay);
        settings_button = (NeumorphImageButton) findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);
        executorService = Executors.newFixedThreadPool(3);
        isExit = false;
        LvTags = (ListView) findViewById(R.id.LvTags);
        btnStart = findViewById(R.id.btnStart);
        btStop =  findViewById(R.id.btStop);
        btStop.setShapeType(1);
        btClear =  findViewById(R.id.btClear);
        tv_count = (TextView) findViewById(R.id.tv_count);
        tv_total = (TextView) findViewById(R.id.tv_total);
        tv_time = (TextView) findViewById(R.id.tv_time);

        btnStart.setOnClickListener(this);
        btClear.setOnClickListener(this);
        btStop.setOnClickListener(this);
        tagList = new ArrayList<HashMap<String, String>>();
        adapter = new SimpleAdapter(this, tagList, R.layout.listtag_items,
                new String[]{ ScanListActivity.TAG_TYPE, ScanListActivity.TAG_DATA, ScanListActivity.TAG_COUNT, ScanListActivity.TAG_RSSI},
                new int[]{R.id.TvTagType, R.id.TvTagUii, R.id.TvTagCount, R.id.TvTagRssi});
        LvTags.setAdapter(adapter);
        LvTags.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Intent newIntent = new Intent(ScanListActivity.this, ScanFocusedTagActivity.class);
                Bundle b = new Bundle();
                b.putString(BluetoothDevice.EXTRA_DEVICE, remoteBTAdd);
                Bundle b2 = new Bundle();
                b2.putString(BluetoothDevice.EXTRA_DEVICE, remoteBTName);
                Bundle b3 = new Bundle();
                b2.putString(TAG_EPC, tagList.get(position).get(ScanListActivity.TAG_EPC));
                newIntent.putExtras(b);
                newIntent.putExtras(b2);
                newIntent.putExtras(b3);
                ScanListActivity.this.startActivity(newIntent);
            }
        });
        clearData();
    }

    private void setViewsEnabled(int enabled) {
        btnStart.setShapeType(enabled);
    }

    Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    int delay = 1*10000; //Delay for 1 seconds  One second = 1000 milliseconds.

    @Override
    public void onResume() {

        handlerRefreshBattery.postDelayed( runnable = new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            public void run() {
                int precentage = uhf.getBattery();
                if (precentage >= 0) {
                    device_battery.setText(precentage + "%");
                    batteryPB.setProgress(precentage);
                    if (precentage <= 10)
                        batteryPB.setProgressTintList(ColorStateList.valueOf(Color.RED));
                    else if (precentage <= 20)
                        batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(255, 165, 0)));
                    else
                        batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
                }
                handlerRefreshBattery.postDelayed(runnable, delay);
            }
        }, delay);
        super.onResume();
        setViewsEnabled(0);
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
                Intent intent=new Intent(ScanListActivity.this, UHFSettingsActivity.class);
                ScanListActivity.this.startActivity(intent);
                break;
            case R.id.btClear:
                clearData();
                break;
            case R.id.btnStart:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && !isScanning) {
                    startThread();
                }
                break;
            case R.id.btStop:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && isScanning) {
                    stopInventory();
                }
                break;
            default:
                break;
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

    class ConnectStatus implements ScanListActivity.IConnectStatus {
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
                    setViewsEnabled(0);
                }

            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                isScanning = false;
                btStop.setShapeType(0);
                setViewsEnabled(1);
            }
        }
    }

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

    private void getMode() {
        executorService.execute(getModeRunnable);
    }

    public synchronized void startThread() {
        if (isScanning) {
            return;
        }
        isScanning = true;
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
            if (!TextUtils.isEmpty(uhftagInfo.getEPC())) {
                int index = checkIsExist(uhftagInfo.getEPC());

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("EPC:");
                stringBuilder.append(uhftagInfo.getEPC());

                map = new HashMap<String, String>();
                String TagEPC = uhftagInfo.getEPC();
                if (!TagEPC.startsWith("VC2021"))
                    return;
                if (!TagEPC.startsWith("VC2021EL"))
                    map.put(ScanListActivity.TAG_TYPE, "Lumière");
                else if (!TagEPC.startsWith("VC2021EC"))
                    map.put(ScanListActivity.TAG_TYPE, "Chauffage Elec");
                else if (!TagEPC.startsWith("VC2021EP"))
                    map.put(ScanListActivity.TAG_TYPE, "Prise Elec");
                else if (!TagEPC.startsWith("VC2021EAR"))
                    map.put(ScanListActivity.TAG_TYPE, "Robinet Eau");
                else if (!TagEPC.startsWith("VC2021GR"))
                    map.put(ScanListActivity.TAG_TYPE, "Robinet Gaz");
                map.put(ScanListActivity.TAG_EPC, uhftagInfo.getEPC());
                map.put(ScanListActivity.TAG_DATA, stringBuilder.toString());
                map.put(ScanListActivity.TAG_COUNT, String.valueOf(1));
                map.put(ScanListActivity.TAG_RSSI, uhftagInfo.getRssi());
                // getAppContext().uhfQueue.offer(epc + "\t 1");
                if (index == -1) {
                    tagList.add(map);
                    tempDatas.add(uhftagInfo.getEPC());
                    tv_count.setText("" + adapter.getCount());
                } else {
                    int tagCount = Integer.parseInt(tagList.get(index).get(ScanListActivity.TAG_COUNT), 10) + 1;
                    map.put(ScanListActivity.TAG_COUNT, String.valueOf(tagCount));
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

}
