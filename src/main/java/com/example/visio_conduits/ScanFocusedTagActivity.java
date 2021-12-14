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
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.visio_conduits.utils.DBHelper;
import com.example.visio_conduits.utils.Utils;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.util.ArrayList;
import java.util.List;

import soup.neumorphism.NeumorphButton;
import soup.neumorphism.NeumorphImageButton;


public class ScanFocusedTagActivity extends BaseActivity implements View.OnClickListener {

    @SuppressLint("StaticFieldLeak")
    public static ScanFocusedTagActivity fa;
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    public String focusedTagEPC = "";
    public String focusedTagName = "";
    public String focusedTagRoom = "";
    public String focusedTagWorkPlace = "";
    public BluetoothAdapter mBtAdapter = null;
    private static final int NEW_TAG_NAME = 1;
    private ProgressBar batteryPB;
    private List<MyTag> tagsList;
    private ScanListActivity.TagsAdapter tagsAdapter;

    public TextView nameTV, roomTV, workplaceTV, device_battery;

    public static final String TAG_EPC = "tagEpc";
    private final DBHelper mydb = new DBHelper(this, null, 1, this);

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
    private NeumorphButton btStart;
    private NeumorphButton btStop;
    private ProgressBar pb_distance;
    private TextView tv_FocusTagNbDetect, tv_distance;
    private boolean isExit = false;
    private long totalFocusTagDetect = 0;

    private final ConnectStatus mConnectStatus = new ConnectStatus();
    boolean isRunning = false;

    final Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    final int delay = 10000; //Delay for 1 seconds  One second = 1000 milliseconds.

    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {

        handlerRefreshBattery.postDelayed(runnable = () -> {
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
        }, delay);
        super.onResume();
        setViewsEnabled(0);
    }

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
        setContentView(R.layout.activity_uhf_scan_focused_tag);

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
        tagsList = new ArrayList<>();
        initUI();
        IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);
        Utils.initSound(getApplicationContext());
        MyTag newTag = new MyTag(focusedTagEPC, "", "", "0,0", false);
        addTag(newTag);
        stopInventory();
    }


    @Override
    protected void onDestroy() {
        Intent result = new Intent();
        setResult(Activity.RESULT_OK, result);
        Utils.freeSound();
        unregisterReceiver(bluetoothBroadcastReceiver);
        isExit = true;
        removeConnectStatusNotice(mConnectStatus);
        super.onDestroy();
    }

    @SuppressLint("Range")
    private void initUI() {
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBtAdapter.isEnabled())
            finish();
        device_battery = findViewById(R.id.device_battery);
        batteryPB = findViewById(R.id.batteryPB);
        batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
        device_battery = findViewById(R.id.device_battery);
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

        NeumorphImageButton settings_button = findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);

        isExit = false;
        btStart = findViewById(R.id.btnStart);
        btStop = findViewById(R.id.btStop);
        btStop.setShapeType(1);
        pb_distance = findViewById(R.id.progressBar);
        tv_distance = findViewById(R.id.FocusTagDistance);

        btStart.setOnClickListener(this);
        btStop.setOnClickListener(this);
        Button nameTag = findViewById(R.id.InventoryFocusAddModifyTag);
        Cursor cursor = mydb.selectATag(focusedTagEPC);
        if (cursor.moveToFirst() && cursor.getCount() != 0) {
            nameTag.setText(R.string.modify_tag_name);
            focusedTagName = cursor.getString(cursor.getColumnIndex("name"));
            focusedTagRoom = cursor.getString(cursor.getColumnIndex("room"));
            focusedTagWorkPlace = cursor.getString(cursor.getColumnIndex("workplace"));
        }
        tv_FocusTagNbDetect = findViewById(R.id.tv_FocusTagNbDetect);
        nameTV = findViewById(R.id.FocusTagName);
        nameTV.setText(focusedTagName);
        roomTV = findViewById(R.id.FocusTagRoom);
        roomTV.setText(focusedTagRoom);
        workplaceTV = findViewById(R.id.FocusTagWorkplace);
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

    @SuppressLint("Range")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NEW_TAG_NAME) {//When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                Cursor cursor = mydb.selectATag(focusedTagEPC);
                if (cursor.moveToFirst() && cursor.getCount() != 0) {
                    focusedTagName = cursor.getString(cursor.getColumnIndex("name"));
                    focusedTagRoom = cursor.getString(cursor.getColumnIndex("room"));
                    focusedTagWorkPlace = cursor.getString(cursor.getColumnIndex("workplace"));
                }
                nameTV.setText(focusedTagName);
                roomTV.setText(focusedTagRoom);
                workplaceTV.setText(focusedTagWorkPlace);
            }
        }
    }

    private void setViewsEnabled(int enabled) {
        btStart.setShapeType(enabled);
    }

    @Override
    public void onPause() {
        handlerRefreshBattery.removeCallbacks(runnable); //stop handler when activity not visible
        super.onPause();
        stopInventory();
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
            case R.id.btnStart:
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
        btStop.setShapeType(1);
        btStart.setShapeType(0);
        if (isScanningTags)
            uhf.stopInventory();
        isScanningTags = false;
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
                    setViewsEnabled(0);
                }
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                isScanningTags = false;
                btStop.setEnabled(false);
                setViewsEnabled(1);
            }
        }
    }

    public synchronized void startThread() {
        if (isScanningTags) {
            return;
        }
        isScanningTags = true;
        new TagThread().start();
    }

    class TagThread extends Thread {

        @SuppressLint("SetTextI18n")
        public void run() {
            btStop.setShapeType(0);
            btStart.setShapeType(1);
            if (uhf.startInventoryTag()) {
                loopFlag = true;
                isScanningTags = true;
            } else {
                showToast("Erreur de connexion à l'antenne.");
            }
            long startTime = System.currentTimeMillis();
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if (list == null || list.size() == 0) {
                    SystemClock.sleep(20);
                } else {
                    addEPCToList(list);
                }
                if (System.currentTimeMillis() - startTime > 100) {
                    startTime = System.currentTimeMillis();
                }
            }
        }
    }

    private synchronized List<UHFTAGInfo> getUHFInfo() {
        return uhf.readTagFromBufferList_EpcTidUser();
    }

    private void updateDisplay(MyTag tag) {
        if (tag.getEPC().equals(focusedTagEPC)) {
            int distance = (int) Double.parseDouble(tag.getRssi().replaceAll(",", "."));
            distance = -distance * 2 - 60;
            if (distance <= 0) {
                distance = 0;
            }
            tv_distance.setText(distance + " cm");
            pb_distance.setProgress(100 - distance);
            tv_FocusTagNbDetect.setText(String.valueOf(++totalFocusTagDetect));
            Utils.playSound(1);
            int savedistance = distance;
            distance = distance + 40;
            while (distance < 100) {
                SystemClock.sleep(10 + savedistance * 4);
                distance = distance + 50;
                Utils.playSound(1);
            }

        }
    }

    private void addEPCToList(List<UHFTAGInfo> list) {
        for (int k = 0; k < list.size(); k++) {
            UHFTAGInfo uhftagInfo = list.get(k);
            if (!uhftagInfo.getEPC().equals("")) {/* || !uhftagInfo.getEPC().startsWith("AAAA")*///block other tags no tours
                //boolean tagFound = false;
                for (MyTag tag : tagsList) {
                    if (tag.getEPC().equals(uhftagInfo.getEPC())) {
                        //tagFound = true;
                        tag.setRssi(uhftagInfo.getRssi());
                        tag.setNbrDetections(false);
                        runOnUiThread(() -> updateDisplay(tag));
                        break;
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
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
            //@SuppressLint("Range") String workplace = cursor.getString(cursor.getColumnIndex("workplace"));
            tag.setName(name + " " + room);
        } else {
            //tag.setName(String.valueOf(tagNumber));
            tag.setName(tag.getEPC());
        }
        tag.setNbrDetections(false);
        tagsList.add(0, tag);
    }


    static class MyTag {
        private final String epc;
        private String name;
        private String rssi;
        private String type;
        private int nbrDetections;
        private boolean isFavorites;

        public MyTag(String epc, String name, String type, String rssi, Boolean isFavorites) {
            this.epc = epc;
            this.name = name;
            this.type = type;
            this.rssi = rssi;
            this.nbrDetections = 0;
            this.isFavorites = isFavorites;
        }

        public String getEPC() {
            return epc;
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

        public void setNbrDetections(boolean reset) {
            if (reset)
                this.nbrDetections = 0;
            else
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

}



