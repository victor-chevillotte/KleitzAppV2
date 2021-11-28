package com.example.uhf_bt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.uhf_bt.BaseActivity;
import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.StartActivity;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


public class BTRenameActivity extends BaseActivity {
    static boolean isExit_ = false;
    EditText etNewName;
    EditText etOldName;
    Button btSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_btrename);
        isExit_ = false;
        etNewName = (EditText) findViewById(R.id.etNewName);
        etOldName = (EditText) findViewById(R.id.etOldName);
        etNewName.setText(remoteBTName);
        etOldName.setEnabled(false);
        etOldName.setVisibility(View.GONE);
        btSet = (Button) findViewById(R.id.btSet);
        btSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = etNewName.getText().toString();
                if (newName != null && newName.length() == 0) {
                    showToast("Veuillez indiquer un nom valide.");
                } else {
                    boolean result = uhf.setRemoteBluetoothName(newName);
                    if (result) {
                        updateConnectMessage(remoteBTName, newName);
                        saveConnectedDevice(remoteBTAdd, newName);
                        showToast("Antenne renommée avec succès");
                    } else {
                        showToast("Echec du renommage.");
                    }
                }
            }
        });
        addConnectStatusNotice(new StartActivity.IConnectStatus(){
            @Override
            public void getStatus(ConnectionStatus connectionStatus) {
              if(connectionStatus==ConnectionStatus.CONNECTED){
                  etNewName.setText(remoteBTName);
              }
            }
        });
    }
}
