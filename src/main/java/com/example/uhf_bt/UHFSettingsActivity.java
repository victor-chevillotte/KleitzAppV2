package com.example.uhf_bt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.uhf_bt.utils.SPUtils;
import com.example.uhf_bt.utils.Utils;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

import java.util.HashMap;


public class UHFSettingsActivity extends BaseActivity implements View.OnClickListener {

    public static UHFSettingsActivity faset;
    Button btnGetPower;
    Button btnSetPower;
    Spinner spPower;
    Spinner SpinnerMode;
    Button BtSetFre;
    Button BtGetFre;
    RadioButton rbUsHop;
    RadioButton rbBRA;
    RadioButton rbOtherHop;
    Spinner spFreHop;
    Button btnSetFreHop;
    Button btnbeepOpen;
    Button btnbeepClose;
    CheckBox cbTagFocus;
    private TextView device_battery;
    private TextView device_temperature;
    private TextView device_version;
    private TextView bluetooth_version;
    private Spinner spDisconnectTime;

    private Spinner spProtocol;
    private Button btnSetProtocol;
    private Button btnGetProtocol;
    private Button btn_update_device;

    private RadioGroup rgWorkingMode;

    private CheckBox cbContinuousWave, cbAutoReconnect;

    private String[] arrayPower;

    private final static int GET_FRE = 1;
    private final static int GET_POWER = 2;
    private final static int GET_PROTOCOL = 3;
    private final static int GET_CW = 4;

    public final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        finish();
                        break;
                    case BluetoothAdapter.STATE_ON:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }

            }
        }
    };

    private Handler mHandler = new Handler() {
        @SuppressLint("HandlerLeak")
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GET_FRE:
                    int idx = (int) msg.obj;
                    if (idx != -1) {
                        int count = SpinnerMode.getCount();
                        SpinnerMode.setSelection(idx > count - 1 ? count - 1 : idx);
                    } else if (msg.arg1 == 1) {
                        showToast(R.string.uhf_msg_read_frequency_fail);
                    }
                    break;
                case GET_POWER:
                    int iPower = (int) msg.obj;
                    if(arrayPower != null && iPower > -1) {
                        for (int i = 0; i < arrayPower.length; i++) {
                            if (iPower == Integer.valueOf(arrayPower[i])) {
                                spPower.setSelection(i);
                                break;
                            }
                        }
                    } else if (msg.arg1 == 1) {
                        showToast(R.string.uhf_msg_read_power_fail);
                    }
                    break;
                case GET_PROTOCOL:
                    int pro = (int) msg.obj;
                    if (pro >= 0 && pro < spProtocol.getCount()) {
                        spProtocol.setSelection(pro);
                        if (msg.arg1 == 1)
                            showToast(R.string.uhf_msg_get_protocol_succ);
                    } else {
                        if (msg.arg1 == 1)
                            showToast(R.string.uhf_msg_get_protocol_fail);
                    }
                    break;
                case GET_CW:
                    int flag = (int) msg.obj;
                    if (flag == 1) {
                        cbContinuousWave.setChecked(true);
                        if (msg.arg1 == 1)
                            showToast(R.string.get_succ);
                    } else if (flag == 0) {
                        cbContinuousWave.setChecked(false);
                        if (msg.arg1 == 1)
                            showToast(R.string.get_succ);
                    } else {
                        if (msg.arg1 == 1)
                            showToast(R.string.get_fail);
                    }
                    break;
            }
        }
    };
    EditText etNewName;
    EditText etOldName;
    Button btSet;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      faset = this;
      setContentView(R.layout.activity_uhf_settings);
      initUI();
      IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);
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
      addConnectStatusNotice(new ConnectDeviceActivity.IConnectStatus() {
          @Override
          public void getStatus(ConnectionStatus connectionStatus) {
              if (connectionStatus == ConnectionStatus.CONNECTED) {
                  etNewName.setText(remoteBTName);
              }
          }
      });
      device_version.setText(uhf.getVersion());
      HashMap<String, String> versionMap = uhf.getBluetoothVersion();
      if (versionMap != null) {
          bluetooth_version.setText("Version du firmware：" + versionMap.get(RFIDWithUHFBLE.VERSION_BT_FIRMWARE)
                  + "\nVersion du hardware：" + versionMap.get(RFIDWithUHFBLE.VERSION_BT_HARDWARE)
                  + "\nVersion du logiciel：" + versionMap.get(RFIDWithUHFBLE.VERSION_BT_SOFTWARE));
      }
      int index = SPUtils.getInstance(getApplicationContext()).getSPInt(SPUtils.DISCONNECT_TIME_INDEX, 0);
      spDisconnectTime =(Spinner) findViewById(R.id.spDisconnectTime);
      spDisconnectTime.setSelection(index);
      spDisconnectTime.setOnItemSelectedListener(
              new AdapterView.OnItemSelectedListener() {
                  public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                      long time = 1000 * 60 * 60 * position;
                      SPUtils.getInstance(getApplicationContext()).setSPInt(SPUtils.DISCONNECT_TIME_INDEX, position);
                      SPUtils.getInstance(getApplicationContext()).setSPLong(SPUtils.DISCONNECT_TIME, time);
                      switch (position) {
                          case 0:
                              cancelDisconnectTimer();
                              break;
                          case 1:
                          case 2:
                          case 3:
                          case 4:
                          case 5:
                          case 6:
                              if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                                  cancelDisconnectTimer();
                                  startDisconnectTimer(time);
                              }
                              break;
                      }
                  }
                  public void onNothingSelected(AdapterView<?> parent) {
                  }
              });
  }

    Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    int delay = 1*1000; //Delay for 15 seconds.  One second = 1000 milliseconds.

    @Override
    public void onPause() {
        handlerRefreshBattery.removeCallbacks(runnable); //stop handler when activity not visible
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothBroadcastReceiver);
    }

  private void initUI() {
      btnGetPower = (Button) findViewById(R.id.btnGetPower);
      btnSetPower = (Button) findViewById(R.id.btnSetPower);
      btn_update_device = (Button) findViewById(R.id.btn_update_device);
      btn_update_device.setOnClickListener(this);
      spPower = (Spinner) findViewById(R.id.spPower);
      arrayPower = getResources().getStringArray(R.array.arrayPower);
      ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayPower);
      spPower.setAdapter(adapter);
      device_battery = (TextView) findViewById(R.id.device_battery);
      device_temperature = (TextView) findViewById(R.id.device_temperature);
      device_version = (TextView) findViewById(R.id.device_version);
      bluetooth_version = (TextView) findViewById(R.id.bluetooth_version);

      SpinnerMode = (Spinner) findViewById(R.id.SpinnerMode);
      BtSetFre = (Button) findViewById(R.id.BtSetFre);
      BtGetFre = (Button) findViewById(R.id.BtGetFre);

      rbUsHop = (RadioButton) findViewById(R.id.rbUsHop);
      rbBRA = (RadioButton) findViewById(R.id.rbBRA);
      rbOtherHop = (RadioButton) findViewById(R.id.rbOtherHop);
      spFreHop = (Spinner) findViewById(R.id.spFreHop);
      btnSetFreHop = (Button) findViewById(R.id.btnSetFreHop);

      btnbeepOpen = (Button) findViewById(R.id.btnbeepOpen);
      btnbeepClose = (Button) findViewById(R.id.btnbeepClose);
      cbTagFocus= (CheckBox) findViewById(R.id.cbTagFocus);

      cbTagFocus.setOnClickListener(this);
      rbOtherHop.setOnClickListener(this);
      rbUsHop.setOnClickListener(this);
      rbBRA.setOnClickListener(this);
      btnSetFreHop.setOnClickListener(this);
      btnGetPower.setOnClickListener(this);
      btnSetPower.setOnClickListener(this);
      BtSetFre.setOnClickListener(this);
      BtGetFre.setOnClickListener(this);

      btnbeepOpen.setOnClickListener(this);
      btnbeepClose.setOnClickListener(this);

      spProtocol = (Spinner) findViewById(R.id.spProtocol);
      btnSetProtocol = (Button) findViewById(R.id.btnSetProtocol);
      btnSetProtocol.setOnClickListener(this);
      btnGetProtocol = (Button) findViewById(R.id.btnGetProtocol);
      btnGetProtocol.setOnClickListener(this);

      rgWorkingMode = findViewById(R.id.rgWorkingMode);
      rgWorkingMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(RadioGroup group, int checkedId) {
              if(checkedId == R.id.rbReadTime) {
                  setWorkingMode(0);
              } else if(checkedId == R.id.rbOffline) {
                  setWorkingMode(1);
              }
          }
      });

      cbContinuousWave = (CheckBox) findViewById(R.id.cbContinuousWave);
      cbContinuousWave.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
              int flag = cbContinuousWave.isChecked() ? 1 : 0;
              setCW(flag, true);
          }
      });

      boolean reconnect = SPUtils.getInstance(this.getApplicationContext()).getSPBoolean(SPUtils.AUTO_RECONNECT, false);
      cbAutoReconnect = (CheckBox) findViewById(R.id.cbAutoReconnect);
      cbAutoReconnect.setChecked(reconnect);
      cbAutoReconnect.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
              SPUtils.getInstance(getApplicationContext()).setSPBoolean(SPUtils.AUTO_RECONNECT, cbAutoReconnect.isChecked());
          }
      });
  }

  @Override
  public void onResume() {
      super.onResume();
      new Thread() {
          @Override
          public void run() {
              getFre(false);
              getPower(false);
              getProtocol(false);
              getCW(false);
          }
      }.start();
      handlerRefreshBattery.postDelayed( runnable = new Runnable() {
          public void run() {
              device_battery.setText(uhf.getBattery() + "%");
              device_temperature.setText(uhf.getTemperature() + "℃");
              handlerRefreshBattery.postDelayed(runnable, delay);
          }
      }, delay);

  }

  @Override
  public void onClick(View view) {
      switch (view.getId()) {
          case R.id.btnGetPower:
              getPower(true);
              break;
          case R.id.btn_update_device:
              Intent intent=new Intent(this, UHFUpdateDeviceActivity.class);
              this.startActivity(intent);
              finish();
              break;
          case R.id.btnSetPower:
              setPower();
              break;
          case R.id.BtGetFre:
              getFre(true);
              break;
          case R.id.BtSetFre:
              setFre();
              break;
          case R.id.btnSetFreHop:
              setFre2();
              break;
          case R.id.rbUsHop:
              OnClick_rbUsHop();
              break;
          case R.id.rbBRA:
              OnClick_rbBRA();
              break;
          case R.id.rbOtherHop:
              OnClick_rbOtherHop();
              break;
          case R.id.btnSetProtocol:
              setProtocol();
              break;
          case R.id.btnGetProtocol:
              getProtocol(true);
              break;
          case R.id.btnbeepClose:
              if (uhf.setBeep(false)) {
                  showToast(R.string.setting_succ);
              } else {
                  showToast(R.string.setting_fail);
              }
              break;
          case R.id.btnbeepOpen:
              if (uhf.setBeep(true)) {
                  showToast(R.string.setting_succ);
              } else {
                  showToast(R.string.setting_fail);
              }
              break;
          case R.id.cbTagFocus:
              if (uhf.setTagFocus(cbTagFocus.isChecked())) {
                  showToast(R.string.setting_succ);
              } else {
                  showToast(R.string.setting_fail);
              }
              break;
          default:
              break;
      }
  }

  private void sendMessage(int what, Object obj, int arg1) {
      Message msg = mHandler.obtainMessage(what, obj);
      msg.arg1 = arg1;
      mHandler.sendMessage(msg);
  }

  private void getPower(boolean showToast) {
      int iPower = uhf.getPower();
      sendMessage(GET_POWER, iPower, showToast ? 1 : 0);
  }

  private void setPower() {
      int iPower = Integer.valueOf(spPower.getSelectedItem().toString());
      if (uhf.setPower(iPower)) {
          showToast(R.string.uhf_msg_set_power_succ);
      } else {
          showToast(R.string.uhf_msg_set_power_fail);
      }
  }

  public void getFre(boolean showToast) {
      int idx = uhf.getFrequencyMode();
      switch (idx) {
          case 0x01:
              idx = 0;
              break;
          case 0x02:
              idx = 1;
              break;
          case 0x04:
              idx = 2;
              break;
          case 0x08:
              idx = 3;
              break;
          case 0x016:
              idx = 4;
              break;
          case 0x032:
              idx = 5;
              break;
      }
      sendMessage(GET_FRE, idx, showToast ? 1 : 0);
  }

  private void setFre() {
      byte f = 0;
      switch (SpinnerMode.getSelectedItemPosition()) {
          case 0:
              f = 0x01;
              break;
          case 1:
              f = 0x02;
              break;
          case 2:
              f = 0x04;
              break;
          case 3:
              f = 0x08;
              break;
          case 4:
              f = 0x016;
              break;
          case 5:
              f = 0x032;
              break;
      }
      if (uhf.setFrequencyMode(f)) {
          showToast(R.string.uhf_msg_set_frequency_succ);
      } else {
          showToast(R.string.uhf_msg_set_frequency_fail);
      }
  }

  private void setFre2() {
      if (uhf.setFreHop(new Float(spFreHop.getSelectedItem().toString().trim()).floatValue())) {
          showToast(R.string.uhf_msg_set_frequency_succ);
      } else {
          showToast(R.string.uhf_msg_set_frequency_fail);
      }
  }

  /**
   * 设置协议
   *
   * @return
   */
    private boolean setProtocol() {
        if (uhf.setProtocol(spProtocol.getSelectedItemPosition())) {
            showToast(R.string.uhf_msg_set_protocol_succ);
            return true;
        } else {
            showToast(R.string.uhf_msg_get_protocol_fail);
        }
        return false;
    }

    /**
     * 获取协议
     *
     * @param showToast
     * @return
     */
    private void getProtocol(boolean showToast) {
        int pro = uhf.getProtocol();
        sendMessage(GET_PROTOCOL, pro, showToast ? 1 : 0);
    }

    /**
     * 设置连续波
     *
     * @param flag
     * @param showToast
     */
    private void setCW(int flag, boolean showToast) {
        boolean res = uhf.setCW(flag);
        if (showToast) {
            if (res) {
                showToast(getString(R.string.setting_succ));
            } else {
                showToast(getString(R.string.setting_fail));
            }
        }
    }

    /**
     * 获取连续波
     *
     * @param showToast
     */
    private void getCW(boolean showToast) {
        int flag = uhf.getCW();
        sendMessage(GET_CW, flag, showToast ? 1 : 0);
    }

    /**
     * 设置工作模式
     * @param mode 实时：0，脱机：1
     */
    private void setWorkingMode(int mode) {
        if(uhf.setR6Workmode(mode)) {
            showToast(R.string.setting_succ);
        } else {
            showToast(R.string.setting_fail);
        }
    }

    public void OnClick_rbUsHop() {
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.arrayFreHop_us, android.R.layout.simple_spinner_item);
        spFreHop.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    public void OnClick_rbBRA() {
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.arrayFreHop_bra, android.R.layout.simple_spinner_item);
        spFreHop.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    public void OnClick_rbOtherHop() {
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.arrayFreHop, android.R.layout.simple_spinner_item);
        spFreHop.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }
}
