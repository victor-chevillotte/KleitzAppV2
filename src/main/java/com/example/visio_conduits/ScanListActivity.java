package com.example.visio_conduits;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.example.visio_conduits.utils.DBHelper;
import com.example.visio_conduits.utils.FileUtils;
import com.example.visio_conduits.utils.NumberTool;
import com.example.visio_conduits.utils.Utils;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    public static final String TAG_NAME = "tagName";
    public static final String TAG_EPC = "tagEpc";
    public static final String TAG_COUNT = "tagCount";
    public static final String TAG_RSSI = "tagRssi";
    public static final String TAG_TYPE = "tagType";
    public static final int SORT_BY_NAME = 0;
    public static final int SORT_BY_TYPE = 1;
    public static final int SORT_BY_RSSI = 2;
    public static final int SORT_BY_DETECTIONS_NUM = 3;
    public static final int SORT_BY_NEW_DETECTIONS = 4;
    private int sortType = SORT_BY_NEW_DETECTIONS;
    private TextView device_battery;
    private ProgressBar batteryPB;
    private boolean isScanningTags = false;
    private int tagNumber = 0;

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
    private ListView LvTags;
    private NeumorphButton btnStart, btStop, btClear;
    private NeumorphImageButton settings_button, btSort;
    private TextView tv_count, tv_total, tv_time;
    private boolean isExit = false;
    private long total = 0;
    private TagsAdapter tagsAdapter;
    private HashMap<String, String> map;
    private List<MyTag> tagsList;
    private List<String> tempDatas = new ArrayList<>();
    private Map<String, Integer> ValuessiValues;
    private long mStrTime;
    private ExecutorService executorService;
    private final DBHelper mydb = new DBHelper(this, "KleitzElec.db", null, 1, this);

    private ConnectStatus mConnectStatus = new ConnectStatus();

    final int FLAG_START = 0;
    final int FLAG_STOP = 1;
    final int FLAG_UHFINFO_LIST = 5;
    final int FLAG_UPDATE_TIME = 3;
    final int FLAG_GET_MODE = 4;
    final int FLAG_SUCCESS = 10;
    final int FLAG_FAIL = 11;
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
                    List<UHFTAGInfo> list = (List<UHFTAGInfo>) msg.obj;
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
        tagsList = new ArrayList<>();
        tagsAdapter = new TagsAdapter(this, tagsList);
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
        LvTags.setAdapter(tagsAdapter);
        LvTags.setOnItemClickListener(mTagsListener);
        btnStart = findViewById(R.id.btnStart);
        btStop = findViewById(R.id.btStop);
        btStop.setShapeType(1);
        btClear = findViewById(R.id.btClear);
        btSort = findViewById(R.id.btSort);
        tv_count = (TextView) findViewById(R.id.tv_count);
        tv_total = (TextView) findViewById(R.id.tv_total);
        tv_time = (TextView) findViewById(R.id.tv_time);

        btnStart.setOnClickListener(this);
        btClear.setOnClickListener(this);
        btStop.setOnClickListener(this);
        btSort.setOnClickListener(this);
        clearData();
        List<String[]> deviceFavoritesList = FileUtils.readXmlList(FAV_TAGS_FILE_NAME);
        for (String[] device : deviceFavoritesList) {
            MyTag favoriteTag = new MyTag(device[0], device[1], device[2], "", true);
            addTag(favoriteTag);
        }
    }

    private void setViewsEnabled(int enabled) {
        btnStart.setShapeType(enabled);
    }

    Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    int delay = 1 * 10000; //Delay for 1 seconds  One second = 1000 milliseconds.

    @Override
    public void onResume() {

        handlerRefreshBattery.postDelayed(runnable = new Runnable() {
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
                Intent intent = new Intent(ScanListActivity.this, UHFSettingsActivity.class);
                ScanListActivity.this.startActivity(intent);
                break;
            case R.id.btClear:
                tagNumber = 0;
                clearData();
                break;
            case R.id.btnStart:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && !isScanningTags) {
                    startThread();
                }
                break;
            case R.id.btStop:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && isScanningTags) {
                    stopInventory();
                }
                break;
            case R.id.btSort:
                PopupMenu dropDownMenu = new PopupMenu(getApplicationContext(), btSort);
                dropDownMenu.getMenuInflater().inflate(R.menu.sort, dropDownMenu.getMenu());
                dropDownMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.name:
                                sortType = SORT_BY_NAME;
                                break;
                            case R.id.type:
                                sortType = SORT_BY_TYPE;
                                break;
                            case R.id.rssi:
                                sortType = SORT_BY_RSSI;
                                break;
                            case R.id.detections:
                                sortType = SORT_BY_DETECTIONS_NUM;
                                break;
                            default :
                                break;
                        }
                        sortTagsList();
                        tagsAdapter.notifyDataSetChanged();
                        return true;
                    }
                });
                dropDownMenu.show();
                break;
            default:
                break;
        }
    }

    private void clearData() {
        tv_count.setText("0");
        tv_total.setText("0");
        tv_time.setText("0s");

        for (Iterator<MyTag> iterator = tagsList.iterator(); iterator.hasNext(); ) {
            MyTag tag = iterator.next();
            if (!tag.getIsFavorites()) {
                iterator.remove();
            }
        }
        sortTagsList();
        tempDatas.clear();
        total = 0;
        tagsAdapter.notifyDataSetChanged();
    }

    private void sortTagsList (){
        Collections.sort(tagsList, (tag1, tag2) -> {
            if (sortType == SORT_BY_NAME){
                String s1 = tag1.getName();
                String s2 = tag2.getName();
                return s1.compareToIgnoreCase(s2);
            }
            else if  (sortType == SORT_BY_TYPE){
                String s1 = tag1.getType();
                String s2 = tag2.getType();
                return s1.compareToIgnoreCase(s2);
            }
            else if  (sortType == SORT_BY_RSSI){
                String s1 = tag1.getRssi();
                String s2 = tag2.getRssi();
                return s1.compareToIgnoreCase(s2);
            }
            else if  (sortType == SORT_BY_DETECTIONS_NUM){
                int n1 = tag1.getNbrDetections();
                int n2 = tag2.getNbrDetections();
                return n2 - n1;
            }
            else if  (sortType == SORT_BY_NEW_DETECTIONS){
                return 0;
            }
            else{
                return 0;
            }
        });
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
                isScanningTags = false;
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
        if (isScanningTags) {
            return;
        }
        isScanningTags = true;
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
            long startTime = System.currentTimeMillis();
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if (list == null || list.size() == 0) {
                    SystemClock.sleep(1);
                } else {
                    Utils.playSound(1);
                    handler.sendMessage(handler.obtainMessage(FLAG_UHFINFO_LIST, list));
                }
                if (System.currentTimeMillis() - startTime > 100) {
                    startTime = System.currentTimeMillis();
                    handler.sendEmptyMessage(FLAG_UPDATE_TIME);
                }

            }
            stopInventory();
        }
    }

    private synchronized List<UHFTAGInfo> getUHFInfo() {
        List<UHFTAGInfo> list = uhf.readTagFromBufferList_EpcTidUser();
        return list;
    }

    public void saveFavoriteTags(String epc, String name, String type, Boolean remove) {
        List<String[]> list = FileUtils.readXmlList(FAV_TAGS_FILE_NAME);
        for (int k = 0; k < list.size(); k++) {
            if (epc.equals(list.get(k)[0])) {
                list.remove(list.get(k));
                if (remove) {
                    FileUtils.saveXmlList(list, FAV_TAGS_FILE_NAME);
                    return;
                } else
                    break;
            }
        }
        String[] strArr = new String[]{epc, name, type};
        list.add(0, strArr);
        FileUtils.saveXmlList(list, FAV_TAGS_FILE_NAME);
    }

    private void addEPCToList(List<UHFTAGInfo> list) {
        for (int k = 0; k < list.size(); k++) {
            UHFTAGInfo uhftagInfo = list.get(k);
            if (!TextUtils.isEmpty(uhftagInfo.getEPC()) /* || !uhftagInfo.getEPC().startsWith("AAAA")*/) {//block other tags no tours
                boolean tagFound = false;
                for (MyTag tag : tagsList) {
                    if (tag.getEPC().equals(uhftagInfo.getEPC())) {
                        tagFound = true;
                        tag.setRssi(uhftagInfo.getRssi());
                        tag.setNbrDetections();
                        tv_total.setText(String.valueOf(++total));
                        tagsAdapter.notifyDataSetChanged();
                        break;
                    }
                }
                if (!tagFound) {
                    //mEmptyList.setVisibility(View.GONE);
                    MyTag newTag = new MyTag(uhftagInfo.getEPC(), "", "", uhftagInfo.getRssi(), false);
                    addTag(newTag);
                }
                sortTagsList();
            }
        }
    }

    private void addTag(MyTag tag) {
        if (tag.getEPC().startsWith("AAAAAA"))
            tag.setType("Lumière");
        else if (tag.getEPC().startsWith("AAAAEC"))
            tag.setType("Chauffage Elec");
        else if (tag.getEPC().startsWith("VC2021EP"))
            tag.setType("Prise Elec");
        else if (tag.getEPC().startsWith("VC2021EAR"))
            tag.setType("Robinet Eau");
        else if (tag.getEPC().startsWith("VC2021GR"))
            tag.setType("Robinet Gaz");
        Cursor cursor = mydb.selectATag(tag.getEPC());
        if (cursor.moveToFirst() || cursor.getCount() != 0) {
            @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex("name"));
            @SuppressLint("Range") String room = cursor.getString(cursor.getColumnIndex("room"));
            @SuppressLint("Range") String workplace = cursor.getString(cursor.getColumnIndex("workplace"));
            tag.setName(name + " " + room);
        } else {
            tagNumber++;
            tag.setName(String.valueOf(tagNumber));
        }
        tagsList.add(0, tag);
        tv_count.setText("" + tagsAdapter.getCount());
        tagsAdapter.notifyDataSetChanged();
    }

    private AdapterView.OnItemClickListener mTagsListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            MyTag tag = tagsList.get(position);
            if (!TextUtils.isEmpty(tag.getEPC())) {
                Intent newIntent = new Intent(ScanListActivity.this, ScanFocusedTagActivity.class);
                Bundle b = new Bundle();
                b.putString(BluetoothDevice.EXTRA_DEVICE, remoteBTAdd);
                Bundle b2 = new Bundle();
                b2.putString(BluetoothDevice.EXTRA_DEVICE, remoteBTName);
                Bundle b3 = new Bundle();
                b2.putString(TAG_EPC, tag.getEPC());
                newIntent.putExtras(b);
                newIntent.putExtras(b2);
                newIntent.putExtras(b3);
                ScanListActivity.this.startActivity(newIntent);
            } else {
                showToast(R.string.invalid_bluetooth_address);
            }
        }
    };


    class MyTag {
        private String epc;
        private String name;
        private String rssi;
        private String type;
        private int nbrDetections;
        private String detectionNumber;
        private boolean isFavorites;

        public MyTag(String epc, String name, String type, String rssi, Boolean isFavorites) {
            this.epc = epc;
            this.name = name;
            this.type = type;
            this.rssi = rssi;
            this.nbrDetections = 1;
            this.isFavorites = isFavorites;
        }

        public String getEPC() {
            return epc;
        }

        public void setEPC(String epc) {
            this.epc = epc;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setRssi(String rssi) {
            this.rssi = rssi;
        }

        public String getRssi() {
            return this.rssi;
        }

        public void setNbrDetections() {
            this.nbrDetections++;
        }

        public int getNbrDetections() {
            return nbrDetections;
        }

        public Boolean getIsFavorites() {
            return isFavorites;
        }

        public void setIsFavorites(boolean isFavorites) {
            this.isFavorites = isFavorites;
        }

    }

    class TagsAdapter extends BaseAdapter {
        Context context;
        List<MyTag> tagsList;
        LayoutInflater inflater;

        public TagsAdapter(Context context, List<MyTag> tags) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.tagsList = tags;
        }

        @Override
        public int getCount() {
            return tagsList.size();
        }

        @Override
        public Object getItem(int position) {
            return tagsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.listtag_items, null);
            }
            MyTag tag = tagsList.get(position);
            final TextView tvname = ((TextView) vg.findViewById(R.id.name));
            final TextView tvtype = ((TextView) vg.findViewById(R.id.type));
            final TextView tvcount = ((TextView) vg.findViewById(R.id.count));
            final TextView tvrssi = (TextView) vg.findViewById(R.id.rssi);
            final ImageView favoritefull = (ImageView) vg.findViewById(R.id.favoritefull);
            final RelativeLayout favorite = (RelativeLayout) vg.findViewById(R.id.favorite);
            if (tag.getIsFavorites())
                favoritefull.setVisibility(View.VISIBLE);
            else
                favoritefull.setVisibility(View.GONE);

            favorite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tag.getIsFavorites()) {
                        tag.setIsFavorites(false);
                        favoritefull.setVisibility(View.GONE);
                        showToast("Favoris supprimé");
                        saveFavoriteTags(tag.getEPC(), tag.getName(), tag.getType(), true);
                    } else {
                        showToast("Favoris ajouté");
                        tag.setIsFavorites(true);
                        saveFavoriteTags(tag.getEPC(), tag.getName(), tag.getType(), false);
                    }
                }
            });
            tvrssi.setText(tag.getRssi());
            tvrssi.setTextColor(Color.BLACK);
            tvcount.setText(String.valueOf(tag.getNbrDetections()));
            tvcount.setTextColor(Color.BLACK);
            tvname.setText(tag.getName());
            tvname.setTextColor(Color.BLACK);
            tvtype.setText(tag.getType());
            tvtype.setTextColor(Color.BLACK);
            return vg;
        }
    }
}
