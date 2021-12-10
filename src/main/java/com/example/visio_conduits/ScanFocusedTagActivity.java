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
import android.graphics.Color;
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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.example.visio_conduits.utils.DBHelper;
import com.example.visio_conduits.utils.Utils;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFocusedTagActivity extends BaseActivity implements View.OnClickListener {

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

    public TextView nameTV, roomTV, workplaceTV, EPCTV, device_battery;;

    public static final String TAG_DATA = "tagData";
    public static final String TAG_EPC = "tagEpc";
    public static final String TAG_LEN = "tagLen";
    public static final String TAG_COUNT = "tagCount";
    public static final String TAG_RSSI = "tagRssi";
    public static final String TAG_TYPE = "tagType";
    private final DBHelper mydb = new DBHelper(this, "KleitzElec.db", null, 1,this);

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
    private Button InventoryLoop, btStop, settings_button, nameTag;
    private ProgressBar pb_distance;
    private TextView tv_FocusTagNbDetect, tv_distance;
    private boolean isExit = false;
    private long totalFocusTagDetect = 0;
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

    @SuppressLint("Range")
    private void initUI() {
        setContentView(R.layout.activity_uhf_scan_focused_tag);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBtAdapter.isEnabled())
            finish();
        device_battery = (TextView) findViewById(R.id.device_battery);

        settings_button = (Button) findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);

        executorService = Executors.newFixedThreadPool(3);
        isExit = false;
        InventoryLoop = (Button) findViewById(R.id.InventoryLoop);
        btStop = (Button) findViewById(R.id.btStop);
        btStop.setEnabled(false);
        tv_FocusTagNbDetect = (TextView) findViewById(R.id.tv_FocusTagNbDetect);
        pb_distance=(ProgressBar) findViewById(R.id.progressBar);
        tv_distance= (TextView) findViewById(R.id.FocusTagDistance);

        InventoryLoop.setOnClickListener(this);
        btStop.setOnClickListener(this);
        tagList = new ArrayList<HashMap<String, String>>();
        adapter = new SimpleAdapter(this, tagList, R.layout.listtag_items,
                new String[]{ ScanListActivity.TAG_TYPE, ScanListActivity.TAG_DATA, ScanListActivity.TAG_COUNT, ScanListActivity.TAG_RSSI},
                new int[]{R.id.TvTagType, R.id.TvTagName, R.id.TvTagCount, R.id.TvTagRssi});
        nameTag = (Button) findViewById(R.id.InventoryFocusAddModifyTag);
        Cursor cursor = mydb.selectATag(focusedTagEPC);
        if (cursor.moveToFirst() && cursor.getCount() != 0) {
            nameTag.setText("Modifier l'étiquette");
            focusedTagName = cursor.getString(cursor.getColumnIndex("name"));
            focusedTagRoom = cursor.getString(cursor.getColumnIndex("room"));
            focusedTagWorkPlace = cursor.getString(cursor.getColumnIndex("workplace"));
        }

        EPCTV= (TextView) findViewById(R.id.FocusTagEPC);
        EPCTV.setText(focusedTagEPC);
        nameTV= (TextView) findViewById(R.id.FocusTagName);
        nameTV.setText(focusedTagName);
        roomTV= (TextView) findViewById(R.id.FocusTagRoom);
        roomTV.setText(focusedTagRoom);
        workplaceTV= (TextView) findViewById(R.id.FocusTagWorkplace);
        workplaceTV.setText(focusedTagWorkPlace);

        nameTag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(ScanFocusedTagActivity.this, AddTagNameActivity.class);
                myIntent.putExtra("uii", focusedTagEPC);
                myIntent.putExtra("name", focusedTagName);
                myIntent.putExtra("room", focusedTagRoom);
                myIntent.putExtra("workplace", focusedTagWorkPlace);
                boolean newTag=true;
                if(!focusedTagName.equals("")){
                    newTag=false;
                }
                myIntent.putExtra("newTag", newTag);
                startActivityForResult(myIntent, NEW_TAG_NAME);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case NEW_TAG_NAME:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    focusedTagName = data.getStringExtra("NewFocusedTagName");
                    focusedTagRoom = data.getStringExtra("NewFocusedTagRoom");
                    focusedTagWorkPlace = data.getStringExtra("NewFocusedTagWorkPlace");
                    nameTV.setText(focusedTagName);
                    roomTV.setText(focusedTagRoom);
                    workplaceTV.setText(focusedTagWorkPlace);
                }
                break;
            default:
                break;
        }
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
                mStrTime = System.currentTimeMillis();
                msg.arg1 = FLAG_SUCCESS;
            } else {
                msg.arg1 = FLAG_FAIL;
            }
            handler.sendMessage(msg);
            isRunning = false;//执行完成设置成false
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if(list==null || list.size()==0){
                    SystemClock.sleep(1);
                }else{
                    Utils.playSound(1);
                    handler.sendMessage(handler.obtainMessage(FLAG_UHFINFO_LIST, list));
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
    private void addEPCToList(List<UHFTAGInfo> list) throws ParseException {
        for(int k=0;k<list.size();k++){
            UHFTAGInfo uhftagInfo=list.get(k);
            if (!TextUtils.isEmpty(uhftagInfo.getEPC()) && uhftagInfo.getEPC().equals(focusedTagEPC)) {//ici
                int index = checkIsExist(uhftagInfo.getEPC());
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("EPC:");
                stringBuilder.append(uhftagInfo.getEPC());

                map = new HashMap<String, String>();
                map.put(ScanFocusedTagActivity.TAG_EPC, uhftagInfo.getEPC());
                map.put(ScanFocusedTagActivity.TAG_DATA, stringBuilder.toString());
                map.put(ScanFocusedTagActivity.TAG_COUNT, String.valueOf(1));
                map.put(ScanFocusedTagActivity.TAG_RSSI, uhftagInfo.getRssi());
                if (index == -1) {
                    tagList.add(map);
                    tempDatas.add(uhftagInfo.getEPC());
                } else {
                    int tagCount = Integer.parseInt(tagList.get(index).get(ScanFocusedTagActivity.TAG_COUNT), 10) + 1;
                    map.put(ScanFocusedTagActivity.TAG_COUNT, String.valueOf(tagCount));
                    tagList.set(index, map);
                }
                NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
                Number number = format.parse(tagList.get(0).get(ScanFocusedTagActivity.TAG_RSSI));
                int distance = (int) (- number.doubleValue() - 40);
                if (distance<=0){
                    distance=0;
                }
                tv_distance.setText(distance + " cm");
                pb_distance.setProgress( 100 - distance);
                tv_FocusTagNbDetect.setText(String.valueOf(++totalFocusTagDetect));
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
