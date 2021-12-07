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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.uhf_bt.utils.FileUtils;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback;
import com.rscja.deviceapi.interfaces.ScanBTCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ConnectDeviceActivity extends BaseActivity implements View.OnClickListener {

    ConnectDeviceActivity.BTStatus btStatus = new ConnectDeviceActivity.BTStatus();
    private static final int RECONNECT_NUM = 0; // 重连次数
    private int mReConnectCount = RECONNECT_NUM; // 重新连接次数
    private SwipeRefreshLayout swipeContainer;
    private Boolean disconnecting = false;
    private final static String ACTIVATEBLE = "ACTIVATEBLE";
    public static final String SHOW_HISTORY_CONNECTED_LIST = "showHistoryConnectedList";
    private static final int REQUEST_ENABLE_BT = 2;
    public BluetoothAdapter mBtAdapter = null;
    private Button btn_activate_bluetooth;
    private ProgressBar spinner;

    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {

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
                        set_activity_connect_device();
                        break;
                }

            }
        }
    };

    private TextView mEmptyList;
    public static final String TAG = "DeviceListActivity";
    private String tryingToConnectAddress = "";

    private List<MyDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private Map<String, Integer> devRssiValues;
    private static final long SCAN_PERIOD = 5000; //10 seconds

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
        mEmptyList = (TextView) findViewById(R.id.empty);
        spinner = (ProgressBar) findViewById(R.id.progressBar1);

        devRssiValues = new HashMap<String, Integer>();
        deviceList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(this, deviceList);

        Button btnClearHistory = findViewById(R.id.btnClearHistory);
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileUtils.clearXmlList();
                clearDeviceList(true);
                mEmptyList.setVisibility(View.VISIBLE);
            }

        });
        Button btnRefresh = findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearDeviceList(false);
            }

        });
        mScanning = true;
        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swiperefreshlayout);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!mScanning){
                    clearDeviceList(false);
                }
                else
                    swipeContainer.setRefreshing(false);
            }
        });
        //boolean isHistoryList = getIntent().getBooleanExtra(ConnectDeviceActivity.SHOW_HISTORY_CONNECTED_LIST, false);
        List<String[]> deviceHistoryList = FileUtils.readXmlList();
        for (String[] device : deviceHistoryList) {
            MyDevice myDevice = new MyDevice(device[0], device[1], true);
            addDevice(myDevice, 0);
        }
        mEmptyList.setText(R.string.scanning);
        scanLeDevice(true);
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
                    spinner.setVisibility(View.GONE);
                }
            }, SCAN_PERIOD);
            swipeContainer.setRefreshing(false);
            mScanning = true;
            uhf.startScanBTDevices(new ScanBTCallback() {
                @Override
                public void getDevices(final BluetoothDevice bluetoothDevice, final int rssi, byte[] bytes) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            spinner.setVisibility(View.VISIBLE);
                            if (bluetoothDevice.getName() != null) {
                                MyDevice myDevice = new MyDevice(bluetoothDevice.getAddress(), bluetoothDevice.getName(), false);
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

        Collections.sort(deviceList, new Comparator<MyDevice>() {
            @Override
            public int compare(MyDevice device1, MyDevice device2) {
                if (!device1.getIsHistory() && !device2.getIsHistory()) {
                    String s1 = device1.getName();
                    String s2 = device2.getName();
                    return s1.compareToIgnoreCase(s2);
                } else
                    return 0;
            }
        });
        deviceAdapter.notifyDataSetChanged();
    }

    private void clearDeviceList(boolean history) {
        scanLeDevice(false);
        if (history && tryingToConnectAddress == "")
        {
            for (Iterator<MyDevice> iterator = deviceList.iterator(); iterator.hasNext(); ) {
                MyDevice value = iterator.next();
                if (value.getIsHistory() && value.getAddress() != remoteBTAdd) {
                    iterator.remove();
                }
            }
        }
        else if (tryingToConnectAddress == "")
        {
            for (Iterator<MyDevice> iterator = deviceList.iterator(); iterator.hasNext(); ) {
                MyDevice value = iterator.next();
                if (!value.getIsHistory() && value.getAddress() != remoteBTAdd) {
                    iterator.remove();
                }
            }
        }
        scanLeDevice(true);
        deviceAdapter.notifyDataSetChanged();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            uhf.stopScanBTDevices();
            spinner.setVisibility(View.GONE);
            MyDevice device = deviceList.get(position);
            String address = device.getAddress().trim();
            if (!TextUtils.isEmpty(address)) {
                String deviceAddress = device.getAddress();
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && deviceAddress.equals(remoteBTAdd)) {
                    tryingToConnectAddress = "";
                    deviceAdapter.notifyDataSetChanged();
                    Intent newIntent = new Intent(ConnectDeviceActivity.this, ScanListActivity.class);
                    Bundle b = new Bundle();
                    b.putString(BluetoothDevice.EXTRA_DEVICE, deviceAddress);
                    Bundle b2 = new Bundle();
                    b2.putString(BluetoothDevice.EXTRA_DEVICE, device.getName());
                    newIntent.putExtras(b);
                    newIntent.putExtras(b2);
                    ConnectDeviceActivity.this.startActivity(newIntent);
                } else if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    tryingToConnectAddress = "";
                    disconnecting = true;
                    deviceAdapter.notifyDataSetChanged();
                    disconnect(true);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    tryingToConnectAddress = deviceAddress;
                } else if (tryingToConnectAddress == "" && uhf.getConnectStatus() != ConnectionStatus.CONNECTING) {
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    tryingToConnectAddress = deviceAddress;
                    deviceAdapter.notifyDataSetChanged();
                    connect(deviceAddress);
                } else
                    showToast("Veuillez attendre la fin de la connexion precedente");
            } else {
                showToast(R.string.invalid_bluetooth_address);
            }
        }
    };

    public void connect(String deviceAddress) {
        if (uhf.getConnectStatus() == ConnectionStatus.CONNECTING) {
            showToast("Veuillez attendre la fin de la connexion precedente");
        } else {
            uhf.connect(deviceAddress, btStatus);
        }
    }

    public void saveConnectedDevice(String address, String name) {
        List<String[]> list = FileUtils.readXmlList();
        for (int k = 0; k < list.size(); k++) {
            if (address.equals(list.get(k)[0])) {
                list.remove(list.get(k));
                break;
            }
        }
        String[] strArr = new String[]{address, name};

        list.add(0, strArr);
        Log.d("gggg", String.valueOf(list));

        FileUtils.saveXmlList(list);
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
                        tryingToConnectAddress = "";
                        deviceAdapter.notifyDataSetChanged();
                        showToast(R.string.connect_success);
                        Intent newIntent = new Intent(ConnectDeviceActivity.this, ScanListActivity.class);
                        Bundle b = new Bundle();
                        b.putString(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
                        Bundle b2 = new Bundle();
                        b2.putString(BluetoothDevice.EXTRA_DEVICE, device.getName());
                        newIntent.putExtras(b);
                        newIntent.putExtras(b2);
                        uhf.stopScanBTDevices();
                        spinner.setVisibility(View.GONE);
                        ConnectDeviceActivity.this.startActivity(newIntent);
                        if (!TextUtils.isEmpty(remoteBTAdd)) {
                            saveConnectedDevice(remoteBTAdd, remoteBTName);
                        }
                        mIsActiveDisconnect = true;
                    } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                        if (disconnecting) {
                            showToast("Antenne " + remoteBTName + " déconnectée");
                            remoteBTName = "";
                            remoteBTAdd = "";
                            disconnecting = false;
                            deviceAdapter.notifyDataSetChanged();
                            connect(tryingToConnectAddress);
                        } else {
                            tryingToConnectAddress = "";
                            remoteBTName = "";
                            remoteBTAdd = "";
                            disconnecting = false;
                            deviceAdapter.notifyDataSetChanged();
                            showToast("Echec de connexion à " + mDevice.getName());
                        }
                        if (ScanListActivity.fa != null)
                            ScanListActivity.fa.finish();
                        if (UHFUpdateDeviceActivity.faup != null)
                            UHFUpdateDeviceActivity.faup.finish();
                        if (UHFSettingsActivity.faset != null)
                            UHFSettingsActivity.faset.finish();
                        if (ScanFocusedTagActivity.fa != null)
                            ScanFocusedTagActivity.fa.finish();

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
    public void onResume() {
        super.onResume();
        scanLeDevice(true);
    }

    class MyDevice {
        private String address;
        private String name;
        private boolean isHistory;
        private int bondState;

        public MyDevice() {

        }

        public MyDevice(String address, String name, Boolean isHistory) {
            this.address = address;
            this.name = name;
            this.isHistory = isHistory;
        }

        public String getAddress() {
            return address;
        }

        public Boolean getIsHistory() {
            return isHistory;
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
                if (rssival > -60)
                    tvrssi.setText("A proximité");
                else
                    tvrssi.setText("Eloigné");
                //tvrssi.setText(String.format("Rssi = %d", rssival));
                tvrssi.setTextColor(Color.BLACK);
                tvrssi.setVisibility(View.VISIBLE);
            } else
                tvrssi.setText("Historique");
            tvrssi.setTextColor(Color.BLACK);
            tvrssi.setVisibility(View.VISIBLE);
            if (remoteBTAdd == String.valueOf(devices.get(position).getAddress()))
                tvrssi.setText("Connecté");
            else if (tryingToConnectAddress == String.valueOf(devices.get(position).getAddress()))
                tvrssi.setText("Connexion...");

            tvname.setText(device.getName());
            tvname.setTextColor(Color.BLACK);
            tvadd.setText(device.getAddress());
            tvadd.setTextColor(Color.BLACK);
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
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

