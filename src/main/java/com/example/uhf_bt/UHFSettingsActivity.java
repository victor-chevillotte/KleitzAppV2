package com.example.uhf_bt;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.rscja.deviceapi.interfaces.ConnectionStatus;


public class UHFSettingsActivity extends BaseActivity implements View.OnClickListener {

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

    private Spinner spProtocol;
    private Button btnSetProtocol;
    private Button btnGetProtocol;

    private RadioGroup rgWorkingMode;

    private CheckBox cbContinuousWave, cbAutoReconnect;

    private String[] arrayPower;

    private final static int GET_FRE = 1;
    private final static int GET_POWER = 2;
    private final static int GET_PROTOCOL = 3;
    private final static int GET_CW = 4;
    private Handler mHandler = new Handler() {
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


    /*  @Override
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
  }*/
  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_uhf_settings);
      initUI();
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

  private void initUI() {
      btnGetPower = (Button) findViewById(R.id.btnGetPower);
      btnSetPower = (Button) findViewById(R.id.btnSetPower);

      spPower = (Spinner) findViewById(R.id.spPower);
      arrayPower = getResources().getStringArray(R.array.arrayPower);
      ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayPower);
      spPower.setAdapter(adapter);

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
  }

  @Override
  public void onClick(View view) {
      switch (view.getId()) {
          case R.id.btnGetPower:
              getPower(true);
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
