package com.example.uhf_bt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.uhf_bt.fragment.BTRenameFragment;
import com.example.uhf_bt.fragment.BarcodeFragment;
import com.example.uhf_bt.fragment.UHFEraseFragment;
import com.example.uhf_bt.fragment.UHFKillFragment;
import com.example.uhf_bt.fragment.UHFLockFragment;
import com.example.uhf_bt.fragment.UHFNewReadTagFragment;
import com.example.uhf_bt.fragment.UHFReadFragment;
import com.example.uhf_bt.fragment.UHFReadTagFragment;
import com.example.uhf_bt.fragment.UHFSetFragment;
import com.example.uhf_bt.fragment.UHFUpdataFragment;
import com.example.uhf_bt.fragment.UHFWriteFragment;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTabHost;

public class MainActivity extends BaseActivity {

    public boolean isScanning = false;
    public String remoteBTName = "";
    public String remoteBTAdd = "";
    private final static String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_SELECT_DEVICE = 1;
    public static Activity fa;

    public BluetoothDevice mDevice = null;
    private FragmentTabHost mTabHost;
    private FragmentManager fm;
    private Button btn_connect, btn_search;
    private TextView tvAddress;
    public BluetoothAdapter mBtAdapter = null;
    public RFIDWithUHFBLE uhf = RFIDWithUHFBLE.getInstance();

    public static final String SHOW_HISTORY_CONNECTED_LIST = "showHistoryConnectedList";
    public static final String TAG_DATA = "tagData";
    public static final String TAG_EPC = "tagEpc";
    public static final String TAG_TID = "tagTid";
    public static final String TAG_LEN = "tagLen";
    public static final String TAG_COUNT = "tagCount";
    public static final String TAG_RSSI = "tagRssi";

    private boolean mIsActiveDisconnect = true; // 是否主动断开连接
    private static final int RECONNECT_NUM = Integer.MAX_VALUE; // 重连次数
    private int mReConnectCount = RECONNECT_NUM; // 重新连接次数

    private Timer mDisconnectTimer = new Timer();
    private long timeCountCur; // 断开时间选择
    private long period = 1000 * 30; // 隔多少时间更新一次
    private long lastTouchTime = System.currentTimeMillis(); // 上次接触屏幕操作的时间戳

    private static final int RUNNING_DISCONNECT_TIMER = 10;

    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {

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

    private void set_activity_activate_bluetooth() {
        uhf.free();
        connectStatusList.clear();
        finish();
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fa = this;
        if (uhf.getConnectStatus() == ConnectionStatus.DISCONNECTED)
            finish();
        remoteBTAdd = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        remoteBTName = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        setContentView(R.layout.activity_main);
        initUI();
        registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);
        checkLocationEnable();// a voir
        Utils.initSound(getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        Utils.freeSound();
        unregisterReceiver(bluetoothBroadcastReceiver);
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if (!isScanning) {
            if (item.getItemId() == R.id.UHF_Battery) {
                String ver = getString(R.string.action_uhf_bat) + ":" + uhf.getBattery() + "%";
                Utils.alert(MainActivity.this, R.string.action_uhf_bat, ver, R.drawable.webtext);
            } else if (item.getItemId() == R.id.UHF_T) {
                String temp = getString(R.string.title_about_Temperature) + ":" + uhf.getTemperature() + "℃";
                Utils.alert(MainActivity.this, R.string.title_about_Temperature, temp, R.drawable.webtext);
            } else if (item.getItemId() == R.id.UHF_ver) {
                String ver = uhf.getVersion();
                Utils.alert(MainActivity.this, R.string.action_uhf_ver, ver, R.drawable.webtext);
            } else if (item.getItemId() == R.id.ble_ver) {
                HashMap<String, String> versionMap = uhf.getBluetoothVersion();
                if (versionMap != null) {
                    String verMsg = "固件版本：" + versionMap.get(RFIDWithUHFBLE.VERSION_BT_FIRMWARE)
                            + "\n硬件版本：" + versionMap.get(RFIDWithUHFBLE.VERSION_BT_HARDWARE)
                            + "\n软件版本：" + versionMap.get(RFIDWithUHFBLE.VERSION_BT_SOFTWARE);
                    Utils.alert(MainActivity.this, R.string.action_ble_ver, verMsg, R.drawable.webtext);
                }
            } else if (item.getItemId() == R.id.ble_disconnectTime) {
                View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_disconnect_time, null);
                final Spinner spDisconnectTime = view.findViewById(R.id.spDisconnectTime);
                int index = SPUtils.getInstance(getApplicationContext()).getSPInt(SPUtils.DISCONNECT_TIME_INDEX, 0);
                spDisconnectTime.setSelection(index);
                Utils.alert(this, R.string.disconnectTime, view, R.drawable.webtext, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int index = spDisconnectTime.getSelectedItemPosition();
                        long time = 1000 * 60 * 60 * index;
                        SPUtils.getInstance(getApplicationContext()).setSPInt(SPUtils.DISCONNECT_TIME_INDEX, index);
                        SPUtils.getInstance(getApplicationContext()).setSPLong(SPUtils.DISCONNECT_TIME, time);
                        switch (index) {
                            case 0:
                                //cancelDisconnectTimer();
                                break;
                            case 1:
                            case 2:
                            case 3:
                            case 4:
                            case 5:
                            case 6:
                                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                                    //cancelDisconnectTimer();
                                    //startDisconnectTimer(time);
                                }
                                break;
                        }
                    }
                });
            }
        } else {
            showToast(R.string.title_stop_read_card);
        }
        return true;
    }

    /*public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage("Voulez vous vraiment changer d'antenne ?");
        builder.setPositiveButton("Oui", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setNegativeButton("Non",new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog alert=builder.create();
        alert.show();
    }*/

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    showToast("Bluetooth has turned on ");
                } else {
                    showToast("Problem in BT Turning ON ");
                }
                break;
            default:
                break;
        }
    }


    protected void initUI() {
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        fm = getSupportFragmentManager();
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, fm, R.id.realtabcontent);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.title_inventory2)).setIndicator(getString(R.string.title_inventory2)), UHFNewReadTagFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_set)).setIndicator(getString(R.string.uhf_msg_tab_set)), UHFSetFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.title_bt_rename)).setIndicator(getString(R.string.title_bt_rename)), BTRenameFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.title_update)).setIndicator(getString(R.string.title_update)), UHFUpdataFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.title_inventory)).setIndicator(getString(R.string.title_inventory)), UHFReadTagFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.title_2d_Scan)).setIndicator(getString(R.string.title_2d_Scan)), BarcodeFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_read)).setIndicator(getString(R.string.uhf_msg_tab_read)), UHFReadFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_write)).setIndicator(getString(R.string.uhf_msg_tab_write)), UHFWriteFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_lock)).setIndicator(getString(R.string.uhf_msg_tab_lock)), UHFLockFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_kill)).setIndicator(getString(R.string.uhf_msg_tab_kill)), UHFKillFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_erase)).setIndicator(getString(R.string.uhf_msg_tab_erase)), UHFEraseFragment.class, null);


    }

    //------------------获取定位权限--------------------------------
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

    private List<IConnectStatus> connectStatusList = new ArrayList<>();

    public void addConnectStatusNotice(IConnectStatus iConnectStatus) {
        connectStatusList.add(iConnectStatus);
    }

    public void removeConnectStatusNotice(IConnectStatus iConnectStatus) {
        connectStatusList.remove(iConnectStatus);
    }

    public interface IConnectStatus {
        void getStatus(ConnectionStatus connectionStatus);
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
    }
}