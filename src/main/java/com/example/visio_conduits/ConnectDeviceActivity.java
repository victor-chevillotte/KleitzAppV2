package com.example.visio_conduits;

import android.annotation.SuppressLint;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.visio_conduits.utils.FileUtils;
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

import soup.neumorphism.NeumorphImageButton;

public class ConnectDeviceActivity extends BaseActivity implements View.OnClickListener {

    ConnectDeviceActivity.BTStatus btStatus = new ConnectDeviceActivity.BTStatus();
    private static final int REQUEST_ENABLE_BT = 2;
    public BluetoothAdapter mBtAdapter = null;

    private ProgressBar spinner;
    private SwipeRefreshLayout swipeContainer;
    private ListView newDevicesListView;
    private TextView mEmptyList, scanningDevice;

    private Boolean disconnecting = false;
    private String tryingToConnectAddress = "";
    private List<MyDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private Map<String, Integer> devRssiValues;
    private static final long SCAN_PERIOD = 5000; //5 seconds

    private final Handler mHandler = new Handler();
    private boolean mScanning;

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
        checkReadWritePermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uhf.stopScanBTDevices();
        unregisterReceiver(bluetoothBroadcastReceiver);
        uhf.free();
        cancelDisconnectTimer();
        android.os.Process.killProcess(Process.myPid());
    }

    private void set_activity_activate_bluetooth() {
        uhf.disconnect();
        uhf.free();
        setContentView(R.layout.activity_activate_bluetooth);
        Button btn_activate_bluetooth = (Button) findViewById(R.id.btn_activate_bluetooth);
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
        mEmptyList = findViewById(R.id.empty);
        spinner = findViewById(R.id.progressBar1);
        devRssiValues = new HashMap<>();
        deviceList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(this, deviceList);
        mEmptyList.setText(R.string.scanning);
        newDevicesListView = findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(deviceAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);
        scanningDevice =  findViewById(R.id.scanningDevice);
        NeumorphImageButton btnRefresh = findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(v -> clearDeviceList());
        mScanning = false;
        swipeContainer = findViewById(R.id.swiperefreshlayout);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(() -> {
            clearDeviceList();
            swipeContainer.setRefreshing(false);
        });

        //boolean isFavoritesList = getIntent().getBooleanExtra(ConnectDeviceActivity.SHOW_HISTORY_CONNECTED_LIST, false);
        List<String[]> deviceFavoritesList = FileUtils.readXmlList(FAV_DEVICES_FILE_NAME);
        for (String[] device : deviceFavoritesList) {
            MyDevice myDevice = new MyDevice(device[0], device[1], true);
            addDevice(myDevice, 0);
        }
        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            if (swipeContainer != null)
                swipeContainer.setRefreshing(false);
            if (!mScanning) {
                mScanning = true;
                mHandler.postDelayed(() -> {
                    uhf.stopScanBTDevices();
                    if (spinner != null) {
                        spinner.setVisibility(View.GONE);
                        scanningDevice.setVisibility(View.GONE);
                    }
                    mScanning = false;
                }, SCAN_PERIOD);
                scanningDevice.setVisibility(View.VISIBLE);
                spinner.setVisibility(View.VISIBLE);
                uhf.startScanBTDevices((bluetoothDevice, rssi, bytes) -> runOnUiThread(() -> {
                    if (bluetoothDevice.getName() != null) {
                        MyDevice myDevice = new MyDevice(bluetoothDevice.getAddress(), bluetoothDevice.getName(), false);
                        addDevice(myDevice, rssi);
                    }
                }));
            }
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
            newDevicesListView.setVisibility(View.VISIBLE);
        }
        deviceAdapter.notifyDataSetChanged();
    }

    private void clearDeviceList() {
        if (mScanning)
            scanLeDevice(false);
        if (tryingToConnectAddress.equals("")) {
            for (Iterator<MyDevice> iterator = deviceList.iterator(); iterator.hasNext(); ) {
                MyDevice value = iterator.next();
                if (!value.getIsFavorites() && !value.getAddress().equals(remoteBTAdd)) {
                    iterator.remove();
                }
            }
            deviceAdapter.notifyDataSetChanged();
            newDevicesListView.setVisibility(View.VISIBLE);
        }
        Collections.sort(deviceList, (device1, device2) -> {
            if (device1.getIsFavorites() && device2.getIsFavorites()) {
                String s1 = device1.getName();
                String s2 = device2.getName();
                return s1.compareToIgnoreCase(s2);
            } else
                return 0;
        });
        scanLeDevice(true);
    }

    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            uhf.stopScanBTDevices();
            spinner.setVisibility(View.GONE);
            scanningDevice.setVisibility(View.GONE);
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
                } else if (tryingToConnectAddress.equals("") && uhf.getConnectStatus() != ConnectionStatus.CONNECTING) {
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    tryingToConnectAddress = deviceAddress;
                    deviceAdapter.notifyDataSetChanged();
                    connect(deviceAddress);
                } else
                    showToast("Veuillez attendre la fin de la connexion précédente");
            } else {
                showToast(R.string.invalid_bluetooth_address);
            }
        }
    };

    public void connect(String deviceAddress) {
        if (uhf.getConnectStatus() == ConnectionStatus.CONNECTING) {
            showToast("Veuillez attendre la fin de la connexion précédente");
        } else {
            uhf.connect(deviceAddress, btStatus);
        }
    }

    public void saveFavoriteDevices(String address, String name, Boolean remove) {
        List<String[]> list = FileUtils.readXmlList(FAV_DEVICES_FILE_NAME);
        if (remove) {
            for (int k = 0; k < list.size(); k++) {
                if (address.equals(list.get(k)[0])) {
                    list.remove(list.get(k));
                    FileUtils.saveXmlList(list, FAV_DEVICES_FILE_NAME);
                    return;
                }
            }
        }
        String[] strArr = new String[]{address, name};
        list.add(0, strArr);
        FileUtils.saveXmlList(list, FAV_DEVICES_FILE_NAME);
           }

    class BTStatus implements ConnectionStatusCallback<Object> {
        @Override
        public void getStatus(final ConnectionStatus connectionStatus, final Object device1) {
            runOnUiThread(() -> {
                BluetoothDevice device = (BluetoothDevice) device1;
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    remoteBTName = device.getName();
                    remoteBTAdd = device.getAddress();
                    tryingToConnectAddress = "";
                    showToast(R.string.connect_success);
                    Intent newIntent = new Intent(ConnectDeviceActivity.this, ScanListActivity.class);
                    Bundle b = new Bundle();
                    b.putString(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
                    Bundle b2 = new Bundle();
                    b2.putString(BluetoothDevice.EXTRA_DEVICE, device.getName());
                    newIntent.putExtras(b);
                    newIntent.putExtras(b2);
                    ConnectDeviceActivity.this.startActivity(newIntent);//set favorite device
                    for (MyDevice listDev : deviceList) {
                        if (listDev.getAddress().equals(remoteBTAdd)) {
                            listDev.setIsFavorites(true);
                            saveFavoriteDevices(device.getAddress(), device.getName(), false);
                            break;
                        }
                    }
                    spinner.setVisibility(View.GONE);
                    scanningDevice.setVisibility(View.GONE);
                    uhf.stopScanBTDevices();
                    deviceAdapter.notifyDataSetChanged();
                    mIsActiveDisconnect = true;
                    clearDeviceList();

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
                }

                for (IConnectStatus iConnectStatus : connectStatusList) {
                    if (iConnectStatus != null) {
                        iConnectStatus.getStatus(connectionStatus);
                    }
                }
            });
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_activate_bluetooth) {
            activateBluetooth();
        }
    }

    private void activateBluetooth() {
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                showToast("Le bluetooth a bien été activé !");
            } else {
                showToast("Erreur lors de l'activation du bluetooth a bien été activé !");
            }
        }
    }

    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    public void onResume() {
        deviceList.clear();
        super.onResume();
        if (!mBtAdapter.isEnabled())
            set_activity_activate_bluetooth();
        else {
            List<String[]> deviceFavoritesList = FileUtils.readXmlList(FAV_DEVICES_FILE_NAME);
            for (String[] device : deviceFavoritesList) {
                MyDevice myDevice = new MyDevice(device[0], device[1], true);
                addDevice(myDevice, 0);
            }
            deviceAdapter.notifyDataSetChanged();
            scanLeDevice(true);
        }
    }

    static class MyDevice {
        private final String address;
        private String name;
        private boolean isFavorites;
        private int bondState;

        public MyDevice(String address, String name, Boolean isFavorites) {
            this.address = address;
            this.name = name;
            this.isFavorites = isFavorites;
        }

        public String getAddress() {
            return address;
        }

        public Boolean getIsFavorites() {
            return isFavorites;
        }

        public void setIsFavorites(boolean isFavorites) {
            this.isFavorites = isFavorites;
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

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.device_element, null);
            }
            MyDevice device = devices.get(position);
            final TextView tvadd =  vg.findViewById(R.id.address);
            final TextView tvname = vg.findViewById(R.id.name);
            final TextView tvrssi = vg.findViewById(R.id.rssi);
            final ImageView favoritefull = vg.findViewById(R.id.favoritefull);
            final RelativeLayout favorite = vg.findViewById(R.id.favorite);
            if (device.getIsFavorites())
                favoritefull.setVisibility(View.VISIBLE);
            else
                favoritefull.setVisibility(View.GONE);
            favorite.setOnClickListener(v -> {
                if (device.getIsFavorites()) {
                    device.setIsFavorites(false);
                    favoritefull.setVisibility(View.GONE);
                    showToast("Favoris supprimé");
                    saveFavoriteDevices(device.getAddress(), device.getName(), true);
                } else {
                    showToast("Favoris ajouté");
                    device.setIsFavorites(true);
                    saveFavoriteDevices(device.getAddress(), device.getName(), false);
                    favoritefull.setVisibility(View.VISIBLE);
                }
            });
            int rssival = devRssiValues.get(device.getAddress());
            if (rssival != 0) {
                if (rssival > -60)
                    tvrssi.setText("A proximité");
                else
                    tvrssi.setText("Eloigné");
                tvrssi.setTextColor(Color.BLACK);
                tvrssi.setVisibility(View.VISIBLE);
            } else if (device.getBondState() != BluetoothDevice.BOND_BONDED)
                tvrssi.setText("Non détecté");
            tvrssi.setTextColor(Color.BLACK);
            tvrssi.setVisibility(View.VISIBLE);
            if (remoteBTAdd.equals(device.getAddress()))
                tvrssi.setText("Connecté");
            else if (tryingToConnectAddress.equals(device.getAddress()))
                tvrssi.setText("Connexion...");

            tvname.setText(device.getName());
            tvname.setTextColor(Color.BLACK);
            tvadd.setText(device.getAddress());
            tvadd.setTextColor(Color.BLACK);
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            } else {
            }
            return vg;
        }
    }
}

