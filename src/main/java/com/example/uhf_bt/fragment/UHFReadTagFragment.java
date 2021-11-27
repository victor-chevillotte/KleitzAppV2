package com.example.uhf_bt.fragment;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf_bt.DateUtils;
import com.example.uhf_bt.FileUtils;
import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.NumberTool;
import com.example.uhf_bt.R;
import com.example.uhf_bt.StartActivity;
import com.example.uhf_bt.Utils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import androidx.fragment.app.Fragment;


public class UHFReadTagFragment extends Fragment implements View.OnClickListener {

    private String TAG = "UHFReadTagFragment";

    private boolean loopFlag = false;
    private ListView LvTags;
    private Button InventoryLoop, btInventory, btStop;//
    private Button btInventoryPerMinute;
    private Button btClear;
    private TextView tv_count, tv_total, tv_time;
    private boolean isExit = false;
    private long total = 0;
    private StartActivity mContext;
    private SimpleAdapter adapter;
    private HashMap<String, String> tagMap = new HashMap<>();
    private List<String> tempDatas = new ArrayList<>();
    private ArrayList<HashMap<String, String>> tagList;

    private ConnectStatus mConnectStatus = new ConnectStatus();

    //--------------------------------------获取 解析数据-------------------------------------------------
    final int FLAG_START = 0;//开始
    final int FLAG_STOP = 1;//停止
    final int FLAG_UPDATE_TIME = 2; // 更新时间
    final int FLAG_UHFINFO = 3;
    final int FLAG_UHFINFO_LIST = 5;
    final int FLAG_SUCCESS = 10;//成功
    final int FLAG_FAIL = 11;//失败

    boolean isRuning = false;
    private long mStrTime;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FLAG_STOP:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //停止成功
                        btClear.setEnabled(true);
                        btStop.setEnabled(false);
                        InventoryLoop.setEnabled(true);
                        btInventory.setEnabled(true);
                        btInventoryPerMinute.setEnabled(true);
                    } else {
                        //停止失败
                        Utils.playSound(2);
                        Toast.makeText(mContext, R.string.uhf_msg_inventory_stop_fail, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case FLAG_UHFINFO_LIST:
                    List<UHFTAGInfo> list = ( List<UHFTAGInfo>) msg.obj;
                    addEPCToList(list,true);
                    break;
                case FLAG_START:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        //开始读取标签成功
                        btClear.setEnabled(false);
                        btStop.setEnabled(true);
                        InventoryLoop.setEnabled(false);
                        btInventory.setEnabled(false);
                        btInventoryPerMinute.setEnabled(false);
                    } else {
                        //开始读取标签失败
                        Utils.playSound(2);
                    }
                    break;
                case FLAG_UPDATE_TIME:
                    float useTime = (System.currentTimeMillis() - mStrTime) / 1000.0F;
                    tv_time.setText(NumberTool.getPointDouble(loopFlag ? 1 : 3, useTime) + "s");
                    break;
                case FLAG_UHFINFO:
                    UHFTAGInfo info = (UHFTAGInfo) msg.obj;
                    addEPCToList(info);
//                    addEPCToList(info, false);
                    break;
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_uhfread_tag, container, false);
        initFilter(view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        mContext = (StartActivity) getActivity();
        init();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopInventory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isExit = true;
        mContext.removeConnectStatusNotice(mConnectStatus);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btClear:
                clearData();
                break;
            case R.id.btInventoryPerMinute:
                inventoryPerMinute();
                break;
            case R.id.InventoryLoop:
                startThread();
                break;
            case R.id.btInventory:
                inventory();
                break;
            case R.id.btStop:
                if (mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    stopInventory();
                }
                break;
        }
    }

    private void init() {
        isExit = false;
        mContext.addConnectStatusNotice(mConnectStatus);
        LvTags = (ListView) mContext.findViewById(R.id.LvTags);
        btInventory = (Button) mContext.findViewById(R.id.btInventory);
        InventoryLoop = (Button) mContext.findViewById(R.id.InventoryLoop);
        btStop = (Button) mContext.findViewById(R.id.btStop);
        btStop.setEnabled(false);
        btClear = (Button) mContext.findViewById(R.id.btClear);
        tv_count = (TextView) mContext.findViewById(R.id.tv_count);
        tv_total = (TextView) mContext.findViewById(R.id.tv_total);
        tv_time = (TextView) mContext.findViewById(R.id.tv_time);

        InventoryLoop.setOnClickListener(this);
        btInventory.setOnClickListener(this);
        btClear.setOnClickListener(this);
        btStop.setOnClickListener(this);

        btInventoryPerMinute = mContext.findViewById(R.id.btInventoryPerMinute);
        btInventoryPerMinute.setOnClickListener(this);

        tagList = new ArrayList<>();
        adapter = new SimpleAdapter(mContext, tagList, R.layout.listtag_items,
                new String[]{MainActivity.TAG_DATA, MainActivity.TAG_LEN, MainActivity.TAG_COUNT, MainActivity.TAG_RSSI},
                new int[]{R.id.TvTagUii, R.id.TvTagLen, R.id.TvTagCount,
                        R.id.TvTagRssi});
        LvTags.setAdapter(adapter);
        mContext.uhf.setKeyEventCallback(new KeyEventCallback() {
            @Override
            public void onKeyDown(int keycode) {
                Log.d(TAG, "  keycode =" + keycode + "   ,isExit=" + isExit);
                if (!isExit && mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    if(loopFlag) {
                        stopInventory();
                    } else {
                        startThread();
                    }
                }
            }
        });
        clearData();
    }

    private CheckBox cbFilter;
    private ViewGroup layout_filter;
    private Button btnSetFilter;
    private void initFilter(View view) {
        layout_filter = (ViewGroup) view.findViewById(R.id.layout_filter);
        layout_filter.setVisibility(View.GONE);
        cbFilter = (CheckBox) view.findViewById(R.id.cbFilter);
        cbFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                layout_filter.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        final EditText etLen = (EditText) view.findViewById(R.id.etLen);
        final EditText etPtr = (EditText) view.findViewById(R.id.etPtr);
        final EditText etData = (EditText) view.findViewById(R.id.etData);
        final RadioButton rbEPC = (RadioButton) view.findViewById(R.id.rbEPC);
        final RadioButton rbTID = (RadioButton) view.findViewById(R.id.rbTID);
        final RadioButton rbUser = (RadioButton) view.findViewById(R.id.rbUser);
        btnSetFilter = (Button) view.findViewById(R.id.btSet);

        btnSetFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int filterBank = RFIDWithUHFUART.Bank_EPC;
                if (rbEPC.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_EPC;
                } else if (rbTID.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_TID;
                } else if (rbUser.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_USER;
                }
                if (etLen.getText().toString() == null || etLen.getText().toString().isEmpty()) {
                    showToast("数据长度不能为空");
                    return;
                }
                if (etPtr.getText().toString() == null || etPtr.getText().toString().isEmpty()) {
                    showToast("起始地址不能为空");
                    return;
                }
                int ptr = Utils.toInt(etPtr.getText().toString(), 0);
                int len = Utils.toInt(etLen.getText().toString(), 0);
                String data = etData.getText().toString().trim();
                if (len > 0 && !TextUtils.isEmpty(data)) {
                    String rex = "[\\da-fA-F]*"; //匹配正则表达式，数据为十六进制格式
                    if (!data.matches(rex)) {
                        showToast("过滤的数据必须是十六进制数据");
//                        mContext.playSound(2);
                        return;
                    }

                    int l = data.replace(" ", "").length();
                    if (len <= l * 4) {
                        if(l % 2 != 0)
                            data += "0";
                    } else {
                        showToast(R.string.uhf_msg_set_filter_fail2);
                        return;
                    }

                    if (mContext.uhf.setFilter(filterBank, ptr, len, data)) {
                        showToast(R.string.uhf_msg_set_filter_succ);
                    } else {
                        showToast(R.string.uhf_msg_set_filter_fail);
                    }
                } else {
                    //禁用过滤
                    String dataStr = "00";
                    if (mContext.uhf.setFilter(RFIDWithUHFUART.Bank_EPC, 0, 0, dataStr)
                            && mContext.uhf.setFilter(RFIDWithUHFUART.Bank_TID, 0, 0, dataStr)
                            && mContext.uhf.setFilter(RFIDWithUHFUART.Bank_USER, 0, 0, dataStr)) {
                        showToast(R.string.msg_disable_succ);
                    } else {
                        showToast(R.string.msg_disable_fail);
                    }
                }
                cbFilter.setChecked(false);
            }
        });

        rbEPC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbEPC.isChecked()) {
                    etPtr.setText("32");
                }
            }
        });
        rbTID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbTID.isChecked()) {
                    etPtr.setText("0");
                }
            }
        });
        rbUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbUser.isChecked()) {
                    etPtr.setText("0");
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            InventoryLoop.setEnabled(true);
            btInventory.setEnabled(true);
            btInventoryPerMinute.setEnabled(true);

            cbFilter.setEnabled(true);
        } else {
            InventoryLoop.setEnabled(false);
            btInventory.setEnabled(false);
            btInventoryPerMinute.setEnabled(false);

            cbFilter.setChecked(false);
            cbFilter.setEnabled(false);
        }
    }

    private void clearData() {
        total = 0;
        tv_count.setText("0");
        tv_total.setText("0");
        tv_time.setText("0s");
        tagList.clear();
        tempDatas.clear();
        adapter.notifyDataSetChanged();
    }

    /**
     * 停止识别
     */
    private void stopInventory() {
        loopFlag = false;
        cancelInventoryTask();
        boolean result = mContext.uhf.stopInventory();
        if(mContext.isScanning) {
            ConnectionStatus connectionStatus = mContext.uhf.getConnectStatus();
            Message msg = handler.obtainMessage(FLAG_STOP);
            if (result || connectionStatus == ConnectionStatus.DISCONNECTED) {
                msg.arg1 = FLAG_SUCCESS;
            } else {
                msg.arg1 = FLAG_FAIL;
            }
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                //在连接的情况下，结束之后继续接收未接收完的数据
                //getUHFInfoEx();
            }
            mContext.isScanning = false;
            handler.sendMessage(msg);
        }
    }

    class ConnectStatus implements StartActivity.IConnectStatus {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                if (!loopFlag) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    InventoryLoop.setEnabled(true);
                    btInventory.setEnabled(true);
                    btInventoryPerMinute.setEnabled(true);
                }

                cbFilter.setEnabled(true);
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                mContext.isScanning = false;
                btClear.setEnabled(true);
                btStop.setEnabled(false);
                InventoryLoop.setEnabled(false);
                btInventory.setEnabled(false);
                btInventoryPerMinute.setEnabled(false);

                cbFilter.setChecked(false);
                cbFilter.setEnabled(false);
            }
        }
    }

    public void startThread() {
        if (isRuning) {
            return;
        }
        isRuning = true;
        cbFilter.setChecked(false);
        new TagThread().start();
    }

    class TagThread extends Thread {

        public void run() {
            Message msg = handler.obtainMessage(FLAG_START);
            if (mContext.uhf.startInventoryTag()) {
                loopFlag = true;
                mContext.isScanning = true;
                mStrTime = System.currentTimeMillis();
                msg.arg1 = FLAG_SUCCESS;
            } else {
                msg.arg1 = FLAG_FAIL;
            }
            handler.sendMessage(msg);
            isRuning = false;//执行完成设置成false
            long startTime=System.currentTimeMillis();
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if(list==null || list.size()==0){
                    SystemClock.sleep(1);
                }else{
                    Utils.playSound(1);
                    handler.sendMessage(handler.obtainMessage(FLAG_UHFINFO_LIST, list));
                }
                if(System.currentTimeMillis()-startTime>100){
                    startTime=System.currentTimeMillis();
                    handler.sendEmptyMessage(FLAG_UPDATE_TIME);
                }

            }
            stopInventory();
        }
    }

    private synchronized   List<UHFTAGInfo> getUHFInfo() {
        List<UHFTAGInfo> list = mContext.uhf.readTagFromBufferList();
        return list;
    }

    /**
     * 添加EPC到列表中
     * @param uhftagInfo
     */
    private void addEPCToList(UHFTAGInfo uhftagInfo) {
        addEPCToList(uhftagInfo, true);
    }
    private void addEPCToList(List<UHFTAGInfo> list,boolean isRepeat) {
        for(int k=0;k<list.size();k++){
            UHFTAGInfo uhftagInfo=list.get(k);
            if (!TextUtils.isEmpty(uhftagInfo.getEPC())) {

                int index = checkIsExist(uhftagInfo.getEPC());

                stringBuilder.setLength(0);
                stringBuilder.append("EPC:");
                stringBuilder.append(uhftagInfo.getEPC());
                if (!TextUtils.isEmpty(uhftagInfo.getTid())) {
                    stringBuilder.append("\r\nTID:");
                    stringBuilder.append(uhftagInfo.getTid());
                }
                if (!TextUtils.isEmpty(uhftagInfo.getUser())) {
                    stringBuilder.append("\r\nUSER:");
                    stringBuilder.append(uhftagInfo.getUser());
                }


                tagMap = new HashMap<>();
                tagMap.put(MainActivity.TAG_EPC, uhftagInfo.getEPC());
                tagMap.put(MainActivity.TAG_DATA, stringBuilder.toString());
                tagMap.put(MainActivity.TAG_COUNT, String.valueOf(1));
                tagMap.put(MainActivity.TAG_RSSI, uhftagInfo.getRssi());

                if (index == -1) {
                    tagList.add(tagMap);
                    tempDatas.add(uhftagInfo.getEPC());
                    tv_count.setText(String.valueOf(adapter.getCount()));
                    tv_total.setText(String.valueOf(++total));

                } else if(isRepeat) {
                    int tagCount = Integer.parseInt(tagList.get(index).get(MainActivity.TAG_COUNT), 10) + 1;
                    tagMap.put(MainActivity.TAG_COUNT, String.valueOf(tagCount));
                    tagList.set(index, tagMap);
                    tv_total.setText(String.valueOf(++total));
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
    private StringBuilder stringBuilder = new StringBuilder();
    /**
     * 添加EPC到列表中
     * @param uhftagInfo
     * @param isRepeat 是否重复添加
     */
    private void addEPCToList(UHFTAGInfo uhftagInfo, boolean isRepeat) {
        if (!TextUtils.isEmpty(uhftagInfo.getEPC())) {

            int index = checkIsExist(uhftagInfo.getEPC());

            stringBuilder.setLength(0);
            stringBuilder.append("EPC:");
            stringBuilder.append(uhftagInfo.getEPC());
            if (!TextUtils.isEmpty(uhftagInfo.getTid())) {
                stringBuilder.append("\r\nTID:");
                stringBuilder.append(uhftagInfo.getTid());
            }
            if (!TextUtils.isEmpty(uhftagInfo.getUser())) {
                stringBuilder.append("\r\nUSER:");
                stringBuilder.append(uhftagInfo.getUser());
            }


            tagMap = new HashMap<>();
            tagMap.put(MainActivity.TAG_EPC, uhftagInfo.getEPC());
            tagMap.put(MainActivity.TAG_DATA, stringBuilder.toString());
            tagMap.put(MainActivity.TAG_COUNT, String.valueOf(1));
            tagMap.put(MainActivity.TAG_RSSI, uhftagInfo.getRssi());

            if (index == -1) {
                tagList.add(tagMap);
                tempDatas.add(uhftagInfo.getEPC());
                tv_count.setText(String.valueOf(adapter.getCount()));
                tv_total.setText(String.valueOf(++total));
                adapter.notifyDataSetChanged();
                Utils.playSound(1);

            } else if(isRepeat) {
                int tagCount = Integer.parseInt(tagList.get(index).get(MainActivity.TAG_COUNT), 10) + 1;
                tagMap.put(MainActivity.TAG_COUNT, String.valueOf(tagCount));
                tagList.set(index, tagMap);
                tv_total.setText(String.valueOf(++total));
                adapter.notifyDataSetChanged();
                Utils.playSound(1);
            }
        }
    }

    /**
     * 判断EPC是否在列表中
     *
     * @param epc 索引
     * @return
     */
    public int checkIsExist(String epc) {
        if (TextUtils.isEmpty(epc)) {
            return -1;
        }
        return binarySearch(tempDatas, epc);
    }

    /**
     * 二分查找，找到该值在数组中的下标，否则为-1
     */
    static int binarySearch(List<String> array, String src) {
        int left = 0;
        int right = array.size() - 1;
        // 这里必须是 <=
        while (left <= right) {
            if (compareString(array.get(left), src)) {
                return left;
            } else if (left != right) {
                if (compareString(array.get(right), src))
                    return right;
            }
            left++;
            right--;
        }
        return -1;
    }

    static boolean compareString(String str1, String str2) {
        if (str1.length() != str2.length()) {
            return false;
        } else if (str1.hashCode() != str2.hashCode()) {
            return false;
        } else {
            char[] value1 = str1.toCharArray();
            char[] value2 = str2.toCharArray();
            int size = value1.length;
            for (int k = 0; k < size; k++) {
                if (value1[k] != value2[k]) {
                    return false;
                }
            }
            return true;
        }
    }

    private Timer mTimer = new Timer();
    private TimerTask mInventoryPerMinuteTask;
    private long period = 6 * 1000; // 每隔多少ms
    private String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "BluetoothReader" + File.separator;
    private String fileName;
    private void inventoryPerMinute() {
        cancelInventoryTask();
        btInventoryPerMinute.setEnabled(false);
        btInventory.setEnabled(false);
        InventoryLoop.setEnabled(false);
        btStop.setEnabled(true);
        mContext.isScanning = true;
        fileName = path + "battery_" + DateUtils.getCurrFormatDate(DateUtils.DATEFORMAT_FULL) + ".txt";
        mInventoryPerMinuteTask = new TimerTask() {
            @Override
            public void run() {
                String data = DateUtils.getCurrFormatDate(DateUtils.DATEFORMAT_FULL) + "\t电量：" + mContext.uhf.getBattery() + "%\n";
                FileUtils.writeFile(fileName, data, true);
                inventory();
            }
        };
        mTimer.schedule(mInventoryPerMinuteTask, 0, period);
    }

    private void cancelInventoryTask() {
        if(mInventoryPerMinuteTask != null) {
            mInventoryPerMinuteTask.cancel();
            mInventoryPerMinuteTask = null;
        }
    }

    private void inventory() {
        mStrTime = System.currentTimeMillis();
        UHFTAGInfo info = mContext.uhf.inventorySingleTag();
        if (info != null) {
            Message msg = handler.obtainMessage(FLAG_UHFINFO);
            msg.obj = info;
            handler.sendMessage(msg);
        }
        handler.sendEmptyMessage(FLAG_UPDATE_TIME);
    }

    private Toast mToast;
    public void showToast(String text) {
        if(mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public void showToast(int resId) {
        showToast(getString(resId));
    }
}
