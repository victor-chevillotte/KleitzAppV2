package com.example.uhf_bt;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.rscja.deviceapi.interfaces.ConnectionStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by WuShengjun on 2019/7/3.
 * Description:
 */

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public String remoteBTName = "";
    public String remoteBTAdd = "";
    private Toast toast;

    public void showToast(String text) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showToast(int resId) {
        showToast(getString(resId));
    }

    //------------连接状态监听-----------------------
    public List<IConnectStatus> connectStatusList = new ArrayList<>();

    public void addConnectStatusNotice(StartActivity.IConnectStatus iConnectStatus) {
        connectStatusList.add(iConnectStatus);
    }

    public void removeConnectStatusNotice(StartActivity.IConnectStatus iConnectStatus) {
        connectStatusList.remove(iConnectStatus);
    }

    public void updateConnectMessage(String oldName, String newName) {
        if (!TextUtils.isEmpty(oldName) && !TextUtils.isEmpty(newName)) {
            //tvAddress.setText(tvAddress.getText().toString().replace(oldName, newName));
            remoteBTName = newName;
        }
    }

    public interface IConnectStatus {
        void getStatus(ConnectionStatus connectionStatus);
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
        FileUtils.saveXmlList(list);
    }

}
