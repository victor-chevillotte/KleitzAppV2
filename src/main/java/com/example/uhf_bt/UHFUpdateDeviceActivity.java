package com.example.uhf_bt;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf_bt.BaseActivity;
import com.example.uhf_bt.ConnectDeviceActivity;
import com.example.uhf_bt.ScanListActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.UHFSettingsActivity;
import com.example.uhf_bt.filebrowser.FileManagerActivity;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.Deflater;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.error.GattError;
import no.nordicsemi.android.nrftoolbox.dfu.DfuService;


public class UHFUpdateDeviceActivity extends BaseActivity implements View.OnClickListener {

    public static UHFUpdateDeviceActivity faup;
    TextView tvPath, tvMsg;
    Button btSelect;
    Button btnUpdata, btnReadVere;
    String TAG = "DeviceAPI_UHFUpdata";
    RadioButton rbSTM32, rbR2000, rbBLE;
    String version;
    ScanListActivity mContext;

    private ProgressDialog progressDialog = null;
    private String mFilePath;
    private Uri mFileStreamUri;
    private ProgressBroadcastsReceiver mProgressBroadcastReceiver;

    private HashMap<String, String> beforeVerMap;
    private HashMap<String, String> latestVerMap;

    private static final int SELECT_FILE_REQ = 11;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        faup = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_uhfupdata);
        tvPath = (TextView) findViewById(R.id.tvPath);
        tvMsg = (TextView) findViewById(R.id.tvMsg);
        btSelect = (Button) findViewById(R.id.btSelect);
        btnUpdata = (Button) findViewById(R.id.btnUpdata);
        btnReadVere = (Button) findViewById(R.id.btnReadVere);

        rbSTM32 = (RadioButton) findViewById(R.id.rbSTM32);
        rbR2000 = (RadioButton) findViewById(R.id.rbR2000);
        rbBLE = (RadioButton) findViewById(R.id.rbBLE);

        btSelect.setOnClickListener(this);
        btnUpdata.setOnClickListener(this);
        btnReadVere.setOnClickListener(this);

        registerProgressListener();
        init();
        addConnectStatusNotice(iConnectStatus);
    }

    private ConnectDeviceActivity.IConnectStatus iConnectStatus = new ConnectDeviceActivity.IConnectStatus() {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            Log.e(TAG, "reconnected>connectionStatus=" + connectionStatus);
            if(connectionStatus == ConnectionStatus.CONNECTED) {
                sleep(1000);
                latestVerMap = uhf.getBluetoothVersion();
                showBTVersion();
            } else if(connectionStatus == ConnectionStatus.DISCONNECTED) {

            }
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btSelect:
                Intent intent = new Intent(this, FileManagerActivity.class);
                startActivity(intent);
                /*if (rbBLE.isChecked()) {
                    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("application/zip");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(intent, SELECT_FILE_REQ);
                    }
                } else {
                    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("application/octet-stream");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(intent, SELECT_FILE_REQ);
                    }
                }*/
                break;
            case R.id.btnUpdata:
                update();
                break;
            case R.id.btnReadVere:
                String v = uhf.getSTM32Version();//获取版本号
                tvMsg.setText("version:" + v);
                break;
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(pathReceiver);
        unregisterProgressListener();
        removeConnectStatusNotice(iConnectStatus);
        super.onDestroy();
    }

    public void update() {
        if (!(uhf.getConnectStatus() == ConnectionStatus.CONNECTED)) {
            showToast("Veuillez d'abord connecter Bluetooth");
            return;
        }
        String filePath = tvPath.getText().toString();
        if (TextUtils.isEmpty(filePath)) {
            showToast(R.string.up_msg_sel_file);
            return;
        }
        if (!rbR2000.isChecked() && !rbSTM32.isChecked() && !rbBLE.isChecked()) {
            showToast("Veuillez sélectionner le module RF ou la mise à niveau de la carte mère");
            return;
        }
        if (rbR2000.isChecked() || rbSTM32.isChecked()) {
            if (filePath.toLowerCase().lastIndexOf(".bin") < 0) {
                showToast("Erreur de format de fichier");
                return;
            }

            File file = new File(filePath);
//            Log.e(TAG, "totalSpace=" + file.getTotalSpace() + ", freeSpace=" + file.getFreeSpace() + ", length=" + file.length());
            if(rbR2000.isChecked() && file.length() < 100 * 1024) {
                showToast("Veuillez sélectionner le bon fichier de mise à niveau");
                return;
            } else if(rbSTM32.isChecked() && file.length() >= 100 * 1024) {
                showToast("Veuillez sélectionner le bon fichier de mise à niveau");
                return;
            }

            tvMsg.setText("");
            int flag = rbR2000.isChecked() ? 0 : 1;

            if (flag == 0)
                version = uhf.getVersion();//获取版本号
            else
                version = uhf.getSTM32Version();//获取版本号
            tvMsg.setText("version:" + version);
            Log.d(TAG, "version=" + version);

            new UpdateTask(filePath, flag).execute();
        } else if (rbBLE.isChecked()) {
            if (filePath.toLowerCase().lastIndexOf(".zip") < 0) {
                showToast("Erreur de format de fichier");
                return;
            }
            updateBLE(this, mFilePath, mFileStreamUri, mDevice);
        }
    }

    class UpdateTask extends AsyncTask<String, Integer, Boolean> {

        String path = "";
        int flag;

        public UpdateTask(String path, int flag) {
            this.path = path;
            this.flag = flag;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            // mypDialog.setMessage("init...");
            String msg = "";
            if(rbR2000.isChecked()) {
                msg = "准备升级射频模块...";
            } else if(rbSTM32.isChecked()) {
                msg = "准备升级主板固件...";
            } else if(rbBLE.isChecked()) {
                msg = "准备升级蓝牙固件...";
            }
            /*eprogressDialog = new ProgressDialog(dthis);
            progressDialog.setMessage(msg);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();*/
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            boolean result = false;
            File uFile = new File(path);
            if (!uFile.exists()) {
                return false;
            }
            long uFileSize = uFile.length();
            int packageCount = (int) (uFileSize / 64);
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(path, "r");
            } catch (FileNotFoundException e) {
            }
            if (raf == null) {
                return false;
            }

            if(flag==0) {
                if (!uhf.uhfJump2Boot()) {
                    Log.d(TAG, "uhfJump2Boot 失败");
                    return false;
                }
            }else{
                if (!uhf.uhfJump2BootSTM32()) {
                    Log.d(TAG, "uhfJump2BootSTM32 失败");
                    return false;
                }
            }

            sleep(2000);
            Log.d(TAG, "UHF uhfStartUpdate");
            if (!uhf.uhfStartUpdate()) {
                Log.d(TAG, "uhfStartUpdate 失败");
                return false;
            }
            int pakeSize = 64;
            byte[] currData = new byte[(int) uFileSize];
            for (int k = 0; k < packageCount; k++) {
                int index = k * pakeSize;
                try {
                    int rsize = raf.read(currData, index, pakeSize);
//                    Log.d(TAG, "总包数量="+uFileSize+"  beginPack=" +index + " endPack=" + (index+pakeSize-1) +" rsize="+rsize);
                } catch (IOException e) {
                    stop();
                    return false;
                }
                byte[] data = Arrays.copyOfRange(currData, index, index + pakeSize);
//                Log.d(TAG,"data="+ StringUtility.bytes2HexString(data,data.length));
                if (uhf.uhfUpdating(data)) {
                    result = true;
                    publishProgress(index + pakeSize, (int) uFileSize);
                } else {
                    Log.d(TAG, "uhfUpdating 失败");
                    stop();
                    return false;
                }

            }
            if (uFileSize % pakeSize != 0) {
                int index = packageCount * pakeSize;
                int len = (int) (uFileSize % pakeSize);
                try {
                    int rsize = raf.read(currData, index, len);
                    Log.d(TAG, "beginPack=" + index + " countPack=" + len + " rsize=" + rsize);
                } catch (IOException e) {
                    stop();
                    return false;
                }
                if (uhf.uhfUpdating(Arrays.copyOfRange(currData, index, index + len))) {
                    result = true;
                    publishProgress((int) uFileSize, (int) uFileSize);
                } else {
                    Log.d(TAG, "uhfUpdating 失败");
                    stop();
                    return false;
                }
            }
            stop();
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            progressDialog.setMessage((values[0] * 100 / values[1]) + "% " + getString(R.string.app_msg_Upgrade));
            tvMsg.setText("version:" + version);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!result) {
                showToast(R.string.uhf_msg_upgrade_fail);
                tvMsg.setText(R.string.uhf_msg_upgrade_fail);
                tvMsg.setTextColor(Color.RED);
            } else {
                showToast(R.string.uhf_msg_upgrade_succ);
                tvMsg.setText(R.string.uhf_msg_upgrade_succ);
                tvMsg.setTextColor(Color.GREEN);
            }
            tvMsg.setText(tvMsg.getText() + " version=" + (flag == 0 ? uhf.getVersion() : uhf.getSTM32Version()));
            progressDialog.dismiss();
        }

        private void stop() {
            Log.d(TAG, "UHF uhfStopUpdate");
            if (!uhf.uhfStopUpdate())
                Log.d(TAG, "uhfStopUpdate 失败");
            sleep(2000);
        }
    }

    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //-----------------------------------------------------------------------------
    PathReceiver pathReceiver = new PathReceiver();

    public void init() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(FileManagerActivity.Path_ACTION);
        registerReceiver(pathReceiver, intentFilter);
    }

    private void reconnect() {
        uhf.connect(mDevice.getAddress());
    }

    public class PathReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mFilePath = intent.getStringExtra(FileManagerActivity.Path_Key);
            mFileStreamUri = Uri.fromFile(new File(mFilePath));
            tvPath.setText(mFilePath);
        }
    }

    private class ProgressBroadcastsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String address = intent.getStringExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS);
            final String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case DfuBaseService.BROADCAST_PROGRESS:
                    final int progress = intent.getIntExtra(DfuBaseService.EXTRA_DATA, 0);
                    final float speed = intent.getFloatExtra(DfuBaseService.EXTRA_SPEED_B_PER_MS, 0.0f);
                    final float avgSpeed = intent.getFloatExtra(DfuBaseService.EXTRA_AVG_SPEED_B_PER_MS, 0.0f);
                    final int currentPart = intent.getIntExtra(DfuBaseService.EXTRA_PART_CURRENT, 0);
                    final int partsTotal = intent.getIntExtra(DfuBaseService.EXTRA_PARTS_TOTAL, 0);

                    switch (progress) {
                        case DfuBaseService.PROGRESS_CONNECTING:
                            setMsg("Connecting…");
                            break;
                        case DfuBaseService.PROGRESS_STARTING:
                            setMsg("Starting DFU…");
                            break;
                        case DfuBaseService.PROGRESS_ENABLING_DFU_MODE:
                            setMsg("Starting bootloader…");
                            break;
                        case DfuBaseService.PROGRESS_VALIDATING:
                            setMsg("Validating…");
                            break;
                        case DfuBaseService.PROGRESS_DISCONNECTING:
                            setMsg("Disconnecting…");
                            break;
                        case DfuBaseService.PROGRESS_COMPLETED:
                            setMsg("Done");
                            showToast("Succès !");
                            hideDialog();
                            reconnect();
                            break;
                        case DfuBaseService.PROGRESS_ABORTED:
                            setMsg("Uploading of the application has been canceled.");
                            showToast("Annulée ");
                            hideDialog();
                            reconnect();
                            break;
                        default:
                            setProgress(progress);
                            break;
                    }
                    break;
                case DfuBaseService.BROADCAST_ERROR:
                    final int error = intent.getIntExtra(DfuBaseService.EXTRA_DATA, 0);
                    final int errorType = intent.getIntExtra(DfuBaseService.EXTRA_ERROR_TYPE, 0);
                    switch (errorType) {
                        case DfuBaseService.ERROR_TYPE_COMMUNICATION_STATE:
                            setMsg(GattError.parseConnectionError(error));
                            showToast("Echec");
                            hideDialog();
                            Log.e(TAG, String.format("error=%d,type=%d,msg=%s", error, errorType, GattError.parseConnectionError(error)));
//                            reconnect();
                            break;
                        case DfuBaseService.ERROR_TYPE_DFU_REMOTE:
                            setMsg(GattError.parseConnectionError(error));
                            showToast("Echec");
                            hideDialog();
                            Log.e(TAG, String.format("error=%d,type=%d,msg=%s", error, errorType, GattError.parseConnectionError(error)));
//                            reconnect();
                            break;
                        default:
                            setMsg(GattError.parse(error));
                            showToast("Echec");
                            Log.e(TAG, String.format("error=%d,type=%d,msg=%s", error, errorType, GattError.parse(error)));
                            hideDialog();
//                            reconnect();
                            break;
                    }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case SELECT_FILE_REQ:
                if(data != null) {
                    final Uri uri = data.getData();
                    if (uri != null && uri.getScheme().equals("content")) {
                        mFileStreamUri = uri;
                        mFilePath = getRealPathFromURI(this, mFileStreamUri);
                        tvPath.setText(mFilePath);
                    }
                }
                break;
        }
    }

    public void registerProgressListener() {
        if (mProgressBroadcastReceiver == null) {
            mProgressBroadcastReceiver = new ProgressBroadcastsReceiver();
            final IntentFilter filter = new IntentFilter();
            filter.addAction(DfuBaseService.BROADCAST_PROGRESS);
            filter.addAction(DfuBaseService.BROADCAST_ERROR);
            LocalBroadcastManager.getInstance(this).registerReceiver(mProgressBroadcastReceiver, filter);
        }
    }

    public void unregisterProgressListener() {
        if (mProgressBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mProgressBroadcastReceiver);
            mProgressBroadcastReceiver = null;
        }
    }

    //---------------------蓝牙固件升级 being--------------------------------------------------------
    public void updateBLE(Context context, String mFilePath, Uri mFileStreamUri, BluetoothDevice mSelectedDevice) {

        Log.e(TAG, "mFileStreamUri=" + mFileStreamUri);
        Log.e(TAG, "mFilePath=" + mFilePath);
        Log.e(TAG, "mSelectedDevice=" + mSelectedDevice);
        tvMsg.setText("");
        beforeVerMap = uhf.getBluetoothVersion();
        Log.e(TAG, "beforeVerMap=" + beforeVerMap);
        uhf.setStatusCallback(null);
        if (TextUtils.isEmpty(mFilePath) || mFileStreamUri == null) {
            showToast("Veuillez sélectionner le fichier de mise à jour");
            return;
        }
        if (mSelectedDevice == null) {
            showToast("Veuillez sélectionner un appareil Bluetooth");
            return;
        }
        final DfuServiceInitiator starter = new DfuServiceInitiator(mSelectedDevice.getAddress())
                .setDeviceName(mSelectedDevice.getName())
                .setKeepBond(false)
                .setForceDfu(false)
                .setForeground(false)
                .setPacketsReceiptNotificationsEnabled(false)
                .setPacketsReceiptNotificationsValue(12)
                .setDisableNotification(true)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);
        starter.setZip(mFileStreamUri, mFilePath);
        starter.start(context, DfuService.class);
        showDialog();
    }

    private void showDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(mContext);
        }
        progressDialog.setMessage("准备升级蓝牙固件...");
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
    }

    private void hideDialog() {
        if (progressDialog != null)
            progressDialog.dismiss();
        progressDialog = null;
    }

    private void setMsg(String msg) {
        if (progressDialog != null)
            progressDialog.setMessage(msg);
    }

   /* public void setProgress(int pro) {
        if (progressDialog != null)
            progressDialog.setMessage(pro + "%");
    }*/

    private void showBTVersion() {
        Log.e(TAG, "beforeVerMap=" + beforeVerMap + ", lastestVerMap=" + latestVerMap);
        if(beforeVerMap != null && latestVerMap != null) {
            tvMsg.setText("升级前固件版本：" + beforeVerMap.get(RFIDWithUHFBLE.VERSION_BT_FIRMWARE)
                    + "\n升级前硬件版本：" + beforeVerMap.get(RFIDWithUHFBLE.VERSION_BT_HARDWARE)
                    + "\n升级前软件版本：" + beforeVerMap.get(RFIDWithUHFBLE.VERSION_BT_SOFTWARE)
                    + "\n升级后固件版本：" + latestVerMap.get(RFIDWithUHFBLE.VERSION_BT_FIRMWARE)
                    + "\n升级后硬件版本：" + latestVerMap.get(RFIDWithUHFBLE.VERSION_BT_HARDWARE)
                    + "\n升级后软件版本：" + latestVerMap.get(RFIDWithUHFBLE.VERSION_BT_SOFTWARE));
        }
    }

    public String getRealPathFromURI(Context context, Uri contentURI) {
        String result;
        Cursor cursor = context.getContentResolver().query(contentURI,
                new String[] { MediaStore.Images.ImageColumns.DATA },//
                null, null, null);
        if (cursor == null)
            result = contentURI.getPath();
        else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(index);
            cursor.close();
        }
        return result;
    }
}
