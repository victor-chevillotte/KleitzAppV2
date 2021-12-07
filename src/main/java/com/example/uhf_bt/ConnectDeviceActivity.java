package com.example.uhf_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf_bt.utils.FileUtils;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback;
import com.rscja.deviceapi.interfaces.ScanBTCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConnectDeviceActivity extends BaseActivity implements View.OnClickListener {

    ConnectDeviceActivity.BTStatus btStatus = new ConnectDeviceActivity.BTStatus();
    private static final int RECONNECT_NUM = 1; // 重连次数
    private int mReConnectCount = RECONNECT_NUM; // 重新连接次数
    private TextView tvAddress;

    private final static String ACTIVATEBLE = "ACTIVATEBLE";
    public static final String SHOW_HISTORY_CONNECTED_LIST = "showHistoryConnectedList";
    private static final int REQUEST_ENABLE_BT = 2;
    public BluetoothAdapter mBtAdapter = null;
    private Button btn_activate_bluetooth;

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
                        set_activity_connect_device();
                        break;
                }

            }
        }
    };

    private TextView mEmptyList;
    public static final String TAG = "DeviceListActivity";

    private TextView tvTitle;

    private List<MyDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private Map<String, Integer> devRssiValues;
    private static final long SCAN_PERIOD = 10000; //10 seconds

    private Handler mHandler = new Handler();
    private boolean mScanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uhf.init(getApplicationContext());
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter.isEnabled())
            set_activity_connect_device();
        else
            set_activity_activate_bluetooth();
        IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);

        /*Intent intent=new Intent(ConnectDeviceActivity.this,ScanListActivity.class);
        ConnectDeviceActivity.this.startActivity(intent);
        ConnectDeviceActivity.this.finish();*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uhf.stopScanBTDevices();
        unregisterReceiver(bluetoothBroadcastReceiver);
        uhf.free();
        connectStatusList.clear();
        cancelDisconnectTimer();
        android.os.Process.killProcess(Process.myPid());
    }

    private void set_activity_activate_bluetooth() {
        uhf.disconnect();
        uhf.free();
        setContentView(R.layout.activity_activate_bluetooth);
        btn_activate_bluetooth = (Button) findViewById(R.id.btn_activate_bluetooth);
        btn_activate_bluetooth.setOnClickListener(this);
    }


    private void set_activity_connect_device() {
        setContentView(R.layout.activity_connect_device);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        init();
    }

    private void init() {
        tvAddress = (TextView) findViewById(R.id.title_devices);
        tvTitle = findViewById(R.id.title_devices);
        mEmptyList = (TextView) findViewById(R.id.empty);

        devRssiValues = new HashMap<String, Integer>();
        deviceList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(this, deviceList);

        Button btnClearHistory = findViewById(R.id.btnClearHistory);
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileUtils.clearXmlList();
                deviceList.clear();
                deviceAdapter.notifyDataSetChanged();
                mEmptyList.setVisibility(View.VISIBLE);
            }

        });

        boolean isHistoryList = getIntent().getBooleanExtra(ConnectDeviceActivity.SHOW_HISTORY_CONNECTED_LIST, false);
        if (isHistoryList) {
            tvTitle.setText(R.string.history_connected_device);
            mEmptyList.setText(R.string.no_history);
            List<String[]> deviceList = FileUtils.readXmlList();
            for (String[] device : deviceList) {
                MyDevice myDevice = new MyDevice(device[0], device[1]);
                addDevice(myDevice, 0);
            }
        } else { // 搜索蓝牙设备
            tvTitle.setText(R.string.select_device);
            mEmptyList.setText(R.string.scanning);
            btnClearHistory.setVisibility(View.GONE);
            scanLeDevice(true);
        }

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(deviceAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);
    }

    private void scanLeDevice(final boolean enable) {

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    uhf.stopScanBTDevices();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            uhf.startScanBTDevices(new ScanBTCallback() {
                @Override
                public void getDevices(final BluetoothDevice bluetoothDevice, final int rssi, byte[] bytes) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (bluetoothDevice.getName() != null) {
                                MyDevice myDevice = new MyDevice(bluetoothDevice.getAddress(), bluetoothDevice.getName());
                                addDevice(myDevice, rssi);
                            }
                        }
                    });
                }
            });
        } else {
            mScanning = false;
            uhf.stopScanBTDevices();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        uhf.stopScanBTDevices();
    }

    private void addDevice(MyDevice device, int rssi) {
        boolean deviceFound = false;
        for (MyDevice listDev : deviceList) {
            if (listDev.getAddress().equals(device.getAddress())) {
                deviceFound = true;
                break;
            }
        }
        devRssiValues.put(device.getAddress(), rssi);
        if (!deviceFound) {
            deviceList.add(device);
            mEmptyList.setVisibility(View.GONE);
        }

        // 根据信号强度重新排序
        Collections.sort(deviceList, new Comparator<MyDevice>() {
            @Override
            public int compare(MyDevice device1, MyDevice device2) {
                String key1 = device1.getAddress();
                String key2 = device2.getAddress();
                int v1 = devRssiValues.get(key1);
                int v2 = devRssiValues.get(key2);
                if (v1 > v2) {
                    return -1;
                } else if (v1 < v2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        if (!deviceFound) {
            deviceAdapter.notifyDataSetChanged();
        }
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            uhf.stopScanBTDevices();
            MyDevice device = deviceList.get(position);
            String address = device.getAddress().trim();
            if(!TextUtils.isEmpty(address)) {
                String deviceAddress = device.getAddress();
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && deviceAddress.equals(remoteBTAdd)) {
                    Intent newIntent = new Intent(ConnectDeviceActivity.this, ScanListActivity.class);
                    Bundle b = new Bundle();
                    b.putString(BluetoothDevice.EXTRA_DEVICE, deviceAddress);
                    Bundle b2 = new Bundle();
                    b2.putString(BluetoothDevice.EXTRA_DEVICE, device.getName());
                    newIntent.putExtras(b);
                    newIntent.putExtras(b2);
                    uhf.stopScanBTDevices();
                    ConnectDeviceActivity.this.startActivity(newIntent);
                }
                else if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED)
                {
                    disconnect(true);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    connect(deviceAddress);
                }
                else {
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    connect(deviceAddress);
                }
            } else {
                showToast(R.string.invalid_bluetooth_address);
            }
        }
    };

    public void connect(String deviceAddress) {
        if (uhf.getConnectStatus() == ConnectionStatus.CONNECTING) {
            showToast("Veuillez attendre la fin de la connexion precedente");
        } else {
            //tvAddress.setText(String.format("%s(%s)\nconnecting", mDevice.getName(), deviceAddress));
            uhf.connect(deviceAddress, btStatus);
        }
    }

    private boolean shouldShowDisconnected() {
        return mIsActiveDisconnect || mReConnectCount == 0;
    }

    class BTStatus implements ConnectionStatusCallback<Object> {
        @Override
        public void getStatus(final ConnectionStatus connectionStatus, final Object device1) {
            runOnUiThread(new Runnable() {
                public void run() {
                    BluetoothDevice device = (BluetoothDevice) device1;

                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        remoteBTName = device.getName();
                        remoteBTAdd = device.getAddress();

                        tvAddress.setText(String.format("%s(%s)\nconnected", remoteBTName, remoteBTAdd));
                        showToast(R.string.connect_success);
                        Intent newIntent = new Intent(ConnectDeviceActivity.this, ScanListActivity.class);
                        Bundle b = new Bundle();
                        b.putString(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
                        Bundle b2 = new Bundle();
                        b2.putString(BluetoothDevice.EXTRA_DEVICE, device.getName());
                        newIntent.putExtras(b);
                        newIntent.putExtras(b2);
                        uhf.stopScanBTDevices();
                        ConnectDeviceActivity.this.startActivity(newIntent);
                        if (!TextUtils.isEmpty(remoteBTAdd)) {
                            saveConnectedDevice(remoteBTAdd, remoteBTName);
                        }

                        mIsActiveDisconnect = true;
                    } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                        if (device != null) {
                            remoteBTName = device.getName();
                            remoteBTAdd = device.getAddress();
                            if (shouldShowDisconnected()) {
                                tvAddress.setText(String.format("%s(%s)\ndisconnected", remoteBTName, remoteBTAdd));
                            }
                        } else {
                            if (shouldShowDisconnected())
                                tvAddress.setText("disconnected");
                        }
                        if (shouldShowDisconnected())
                        {
                            showToast("Antenne déconnectée");
                            if (ScanListActivity.fa != null)
                                ScanListActivity.fa.finish();
                            if (UHFUpdateDeviceActivity.faup != null)
                                UHFUpdateDeviceActivity.faup.finish();
                            if (UHFSettingsActivity.faset != null)
                                UHFSettingsActivity.faset.finish();
                            if (ScanFocusedTagActivity.fa != null)
                                ScanFocusedTagActivity.fa.finish();
                        }
                        /*boolean reconnect = SPUtils.getInstance(getApplicationContext()).getSPBoolean(SPUtils.AUTO_RECONNECT, false);
                        if (mDevice != null && reconnect) {
                            reConnect(mDevice.getAddress()); // 重连
                        }*/
                    }

                    for (ConnectDeviceActivity.IConnectStatus iConnectStatus : connectStatusList) {
                        if (iConnectStatus != null) {
                            iConnectStatus.getStatus(connectionStatus);
                        }
                    }
                }
            });
        }
    }

    private void reConnect(String deviceAddress) {
        if (!mIsActiveDisconnect && mReConnectCount > 0) {
            mReConnectCount--;
            connect(deviceAddress);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_activate_bluetooth:
                    activateBluetooth();
                break;
            default:
                break;
        }
    }

    private void activateBluetooth() {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    showToast("Le bluetooth a bien été activé !");
                } else {
                    showToast("Erreur lors de l'activation du bluetooth a bien été activé !");
                }
                break;
            default:
                break;
        }
    }

    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    public void onResume(){
        super.onResume();
        scanLeDevice(true);
    }

    class MyDevice {
        private String address;
        private String name;
        private int bondState;

        public MyDevice() {

        }

        public MyDevice(String address, String name) {
            this.address = address;
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getBondState() {
            return bondState;
        }

        public void setBondState(int bondState) {
            this.bondState = bondState;
        }
    }

    class DeviceAdapter extends BaseAdapter {
        Context context;
        List<MyDevice> devices;
        LayoutInflater inflater;

        public DeviceAdapter(Context context, List<MyDevice> devices) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.devices = devices;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
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
                vg = (ViewGroup) inflater.inflate(R.layout.device_element, null);
            }

            MyDevice device = devices.get(position);
            final TextView tvadd = ((TextView) vg.findViewById(R.id.address));
            final TextView tvname = ((TextView) vg.findViewById(R.id.name));
            final TextView tvpaired = (TextView) vg.findViewById(R.id.paired);
            final TextView tvrssi = (TextView) vg.findViewById(R.id.rssi);

            int rssival = devRssiValues.get(device.getAddress()).intValue();
            if (rssival != 0) {
                tvrssi.setText(String.format("Rssi = %d", rssival));
                tvrssi.setTextColor(Color.BLACK);
                tvrssi.setVisibility(View.VISIBLE);
            }

            tvname.setText(device.getName());
            tvname.setTextColor(Color.BLACK);
            tvadd.setText(device.getAddress());
            tvadd.setTextColor(Color.BLACK);
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "device::" + device.getName());
                tvpaired.setText(R.string.paired);
                tvpaired.setTextColor(Color.RED);
                tvpaired.setVisibility(View.VISIBLE);
            } else {
                tvpaired.setVisibility(View.GONE);
            }
            return vg;
        }
    }
}

