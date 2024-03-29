package com.example.visio_conduits;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.visio_conduits.utils.DBHelper;
import com.example.visio_conduits.utils.FileUtils;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import soup.neumorphism.NeumorphButton;
import soup.neumorphism.NeumorphImageButton;

public class ScanListActivity extends BaseActivity implements View.OnClickListener {

    @SuppressLint("StaticFieldLeak")
    public static ScanListActivity fa;
    public String remoteBTName = "";
    public String remoteBTAdd = "";

    public BluetoothAdapter mBtAdapter = null;

    public static final String TAG_EPC = "tagEpc";
    public static final int SORT_BY_NAME = 0;
    public static final int SORT_BY_TYPE = 1;
    public static final int SORT_BY_RSSI = 2;
    public static final int SORT_BY_DETECTIONS_NUM = 3;
    public static final int SORT_BY_NEW_DETECTIONS = 4;
    private String focusedTagEPC = "";
    private int sortType = SORT_BY_RSSI;
    private TextView device_battery;
    private ProgressBar batteryPB;
    private boolean isScanningTags = false;
    private int tagNumber = 0;

    public final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {

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
                        break;
                }

            }
        }
    };

    final Handler handlerRefreshBattery = new Handler();
    Runnable runnable;
    final int delay = 10000; //Delay for 1 seconds  One second = 1000 milliseconds.

    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {

        handlerRefreshBattery.postDelayed(runnable = () -> {
            int precentage = uhf.getBattery();
            if (precentage >= 0) {
                device_battery.setText(precentage + "%");
                batteryPB.setProgress(precentage);
                if (precentage <= 10)
                    batteryPB.setProgressTintList(ColorStateList.valueOf(Color.RED));
                else if (precentage <= 20)
                    batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(255, 165, 0)));
                else
                    batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
            }
            handlerRefreshBattery.postDelayed(runnable, delay);
        }, delay);
        super.onResume();
        setViewsEnabled(0);
    }

    private boolean loopFlag = false;
    private NeumorphButton btStart;
    private NeumorphButton btStop;
    private NeumorphImageButton btSort;
    private TextView tv_count, tv_total;
    private boolean isExit = false;
    private long total = 0;
    private TagsAdapter tagsAdapter;
    private List<MyTag> tagsList;
    private final DBHelper mydb = new DBHelper(this, null, 1, this);
    private static final int TAG_RENAME = 1;

    private final ConnectStatus mConnectStatus = new ConnectStatus();

    private void set_activity_activate_bluetooth() {
        uhf.free();
        connectStatusList.clear();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uhf_scan_list);
        uhf.setKeyEventCallback(new KeyEventCallback() {
            @Override
            public void onKeyDown(int keycode) {
                if (!isExit && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    if (loopFlag) {
                        stopInventory();
                    } else {
                        startThread();
                    }
                }
            }

            @Override
            public void onKeyUp(int keycode) {
                stopInventory();
            }
        });
        addConnectStatusNotice(mConnectStatus);
        fa = this;
        if (uhf.getConnectStatus() == ConnectionStatus.DISCONNECTED)
            finish();
        remoteBTAdd = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        remoteBTName = getIntent().getStringExtra(BluetoothDevice.EXTRA_DEVICE);
        tagsList = new ArrayList<>();
        tagsAdapter = new TagsAdapter(this, tagsList);
        initUI();
        IntentFilter bluetoothfilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, bluetoothfilter);
    }


    @Override
    protected void onDestroy() {
        unregisterReceiver(bluetoothBroadcastReceiver);
        isExit = true;
        removeConnectStatusNotice(mConnectStatus);
        super.onDestroy();
    }

    @SuppressLint("SetTextI18n")
    private void initUI() {
        setContentView(R.layout.activity_uhf_scan_list);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBtAdapter.isEnabled())
            finish();
        batteryPB = findViewById(R.id.batteryPB);
        batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
        device_battery = findViewById(R.id.device_battery);
        int precentage = uhf.getBattery();
        if (precentage >= 0) {
            device_battery.setText(precentage + "%");
            batteryPB.setProgress(precentage);
            if (precentage <= 10)
                batteryPB.setProgressTintList(ColorStateList.valueOf(Color.RED));
            else if (precentage <= 20)
                batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(255, 165, 0)));
            else
                batteryPB.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 175, 80)));
        }
        handlerRefreshBattery.postDelayed(runnable, delay);
        NeumorphImageButton settings_button = findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);
        isExit = false;
        ListView lvTags = findViewById(R.id.LvTags);
        lvTags.setAdapter(tagsAdapter);
        lvTags.setOnItemClickListener(mTagsListener);
        btStart = findViewById(R.id.btnStart);
        btStop = findViewById(R.id.btStop);
        btStop.setShapeType(1);
        NeumorphButton btClear = findViewById(R.id.btClear);
        btSort = findViewById(R.id.btSort);
        tv_count = findViewById(R.id.tv_count);
        tv_total = findViewById(R.id.tv_total);

        btStart.setOnClickListener(this);
        btClear.setOnClickListener(this);
        btStop.setOnClickListener(this);
        btSort.setOnClickListener(this);
        clearData();
        List<String[]> deviceFavoritesList = FileUtils.readXmlList(FAV_TAGS_FILE_NAME);
        for (String[] device : deviceFavoritesList) {
            MyTag favoriteTag = new MyTag(device[0], device[1], device[2], "Non détécté", true);
            addTag(favoriteTag);
            favoriteTag.setNbrDetections(true);
            runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
        }
    }

    private void setViewsEnabled(int enabled) {
        btStart.setShapeType(enabled);
    }

    @Override
    public void onPause() {
        handlerRefreshBattery.removeCallbacks(runnable); //stop handler when activity not visible
        super.onPause();
        stopInventory();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.settings_button:
                showToast("Chargement des réglages...");
                Intent intent = new Intent(ScanListActivity.this, UHFSettingsActivity.class);
                ScanListActivity.this.startActivity(intent);
                break;
            case R.id.btClear:
                tagNumber = 0;
                clearData();
                break;
            case R.id.btnStart:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && !isScanningTags) {
                    startThread();
                }
                break;
            case R.id.btStop:
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED && isScanningTags) {
                    stopInventory();
                }
                break;
            case R.id.btSort:
                PopupMenu dropDownMenu = new PopupMenu(getApplicationContext(), btSort);
                dropDownMenu.getMenuInflater().inflate(R.menu.sort, dropDownMenu.getMenu());
                dropDownMenu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.name:
                            sortType = SORT_BY_NAME;
                            break;
                        case R.id.type:
                            sortType = SORT_BY_TYPE;
                            break;
                        case R.id.rssi:
                            sortType = SORT_BY_RSSI;
                            break;
                        case R.id.detections:
                            sortType = SORT_BY_DETECTIONS_NUM;
                            break;
                        default:
                            break;
                    }
                    runOnUiThread(() ->  sortTagsList());
                    runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
                    return true;
                });
                dropDownMenu.show();
                break;
            default:
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void clearData() {
        tv_count.setText("0 étiquette");
        tv_total.setText("0 détection");

        for (Iterator<MyTag> iterator = tagsList.iterator(); iterator.hasNext(); ) {
            MyTag tag = iterator.next();
            if (!tag.getIsFavorites()) {
                iterator.remove();
            } else {
                tag.setNbrDetections(true);
                tag.setRssi("Non détecté");
            }
        }
        runOnUiThread(() ->  sortTagsList());
        total = 0;
        runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
    }

    private void sortTagsList() {
        Collections.sort(tagsList, (tag1, tag2) -> {
            if (tag1.getIsFavorites()) {
                /*if (tagsList.get(0) != tag1 && !tagsList.get(0).getIsFavorites())
                    return -1;
                else*/
                    return 0;
            }
            if (sortType == SORT_BY_NAME) {
                String s1 = tag1.getName();
                String s2 = tag2.getName();
                return s1.compareToIgnoreCase(s2);
            } else if (sortType == SORT_BY_TYPE) {
                String s1 = tag1.getType();
                String s2 = tag2.getType();
                return s1.compareToIgnoreCase(s2);
            } else if (sortType == SORT_BY_RSSI) {
                String s1 = tag1.getRssi();
                String s2 = tag2.getRssi();
                return s1.compareToIgnoreCase(s2);
            } else if (sortType == SORT_BY_DETECTIONS_NUM) {
                int n1 = tag1.getNbrDetections();
                int n2 = tag2.getNbrDetections();
                return n2 - n1;
            } else if (sortType == SORT_BY_NEW_DETECTIONS) {
                return 0;
            } else {
                return 0;
            }
        });
    }

    private void stopInventory() {
        loopFlag = false;
        btStop.setShapeType(1);
        btStart.setShapeType(0);
        if (isScanningTags)
            uhf.stopInventory();
        isScanningTags = false;
    }

    class ConnectStatus implements ScanListActivity.IConnectStatus {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                if (!loopFlag) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    setViewsEnabled(0);
                }

            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                loopFlag = false;
                isScanningTags = false;
                btStop.setShapeType(0);
                setViewsEnabled(1);
            }
        }
    }

    public synchronized void startThread() {
        if (isScanningTags) {
            return;
        }
        isScanningTags = true;
        new TagThread().start();
    }

    class TagThread extends Thread {

        @SuppressLint("SetTextI18n")
        public void run() {
            btStop.setShapeType(0);
            btStart.setShapeType(1);
            if (uhf.startInventoryTag()) {
                loopFlag = true;
                isScanningTags = true;
            } else {
                showToast("Erreur de connexion à l'antenne.");
            }
            while (loopFlag) {
                List<UHFTAGInfo> list = getUHFInfo();
                if (list == null || list.size() == 0) {
                    SystemClock.sleep(20);
                } else {
                    runOnUiThread(() -> addEPCToList(list));
                }
            }
        }
    }

    private synchronized List<UHFTAGInfo> getUHFInfo() {
        return uhf.readTagFromBufferList_EpcTidUser();
    }

    public void saveFavoriteTags(String epc, String name, String type, Boolean remove) {
        List<String[]> list = FileUtils.readXmlList(FAV_TAGS_FILE_NAME);
        for (int k = 0; k < list.size(); k++) {
            if (epc.equals(list.get(k)[0])) {
                list.remove(list.get(k));
                if (remove) {
                    FileUtils.saveXmlList(list, FAV_TAGS_FILE_NAME);
                    return;
                } else
                    break;
            }
        }
        String[] strArr = new String[]{epc, name, type};
        list.add(0, strArr);
        FileUtils.saveXmlList(list, FAV_TAGS_FILE_NAME);
    }

    private void addEPCToList(List<UHFTAGInfo> list) {
        for (int k = 0; k < list.size(); k++) {
            UHFTAGInfo uhftagInfo = list.get(k);
            if (!uhftagInfo.getEPC().equals("")) {/* || !uhftagInfo.getEPC().startsWith("AAAA")*///block other tags no tours
                boolean tagFound = false;
                for (MyTag tag : tagsList) {
                    if (tag.getEPC().equals(uhftagInfo.getEPC())) {
                        tagFound = true;
                        int distance = (int) Double.parseDouble(uhftagInfo.getRssi().replaceAll(",", "."));
                        distance = -distance * 2 - 60;
                        if (distance <= 0) {
                            distance = 0;
                        }
                        tag.setRssi(String.valueOf(distance));
                        tag.setNbrDetections(false);
                        ++total;
                        runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
                        break;
                    }
                }
                if (!tagFound) {
                    //mEmptyList.setVisibility(View.GONE);//ici
                    ++total;
                    int distance = (int) Double.parseDouble(uhftagInfo.getRssi().replaceAll(",", "."));
                    distance = -distance * 2 - 60;
                    if (distance <= 0) {
                        distance = 0;
                    }
                    MyTag newTag = new MyTag(uhftagInfo.getEPC(), "", "", String.valueOf(distance), false);
                    addTag(newTag);
                }
                runOnUiThread(() ->  sortTagsList());
                runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void addTag(MyTag tag) {
        if (tag.getEPC().startsWith("AAAAAA"))
            tag.setType("Lumière");
        else if (tag.getEPC().startsWith("AAAAEC"))
            tag.setType("Chauffage Elec");
        else if (tag.getEPC().startsWith("VC2021EP"))
            tag.setType("Prise Elec");
        else if (tag.getEPC().startsWith("VC2021EAR"))
            tag.setType("Robinet Eau");
        else if (tag.getEPC().startsWith("VC2021GR"))
            tag.setType("Robinet Gaz");
        Cursor cursor = mydb.selectATag(tag.getEPC());
        if (cursor.moveToFirst() || cursor.getCount() != 0) {
            @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex("name"));
            @SuppressLint("Range") String room = cursor.getString(cursor.getColumnIndex("room"));
            //@SuppressLint("Range") String workplace = cursor.getString(cursor.getColumnIndex("workplace"));
            tag.setName(name + " " + room);
        } else {
            tagNumber++;
            tag.setName(String.valueOf(tagNumber));
        }
        tag.setNbrDetections(false);
        runOnUiThread(() -> tagsList.add(0, tag));
        runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
    }

    private final AdapterView.OnItemClickListener mTagsListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            MyTag tag = tagsList.get(position);
            if (!TextUtils.isEmpty(tag.getEPC())) {
                Intent newIntent = new Intent(ScanListActivity.this, ScanFocusedTagActivity.class);
                Bundle b = new Bundle();
                b.putString(BluetoothDevice.EXTRA_DEVICE, remoteBTAdd);
                Bundle b2 = new Bundle();
                b2.putString(BluetoothDevice.EXTRA_DEVICE, remoteBTName);
                Bundle b3 = new Bundle();
                focusedTagEPC = tag.getEPC();
                b2.putString(TAG_EPC, focusedTagEPC);
                newIntent.putExtras(b);
                newIntent.putExtras(b2);
                newIntent.putExtras(b3);
                startActivityForResult(newIntent, TAG_RENAME);
            } else {
                showToast(R.string.invalid_bluetooth_address);
            }
        }
    };

    @SuppressLint("Range")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TAG_RENAME) {
            for (MyTag tag : tagsList) {
                if (tag.getEPC().equals(focusedTagEPC)) {
                    Cursor cursor = mydb.selectATag(focusedTagEPC);
                    if (cursor.moveToFirst() && cursor.getCount() != 0) {
                        String NewTagName = cursor.getString(cursor.getColumnIndex("name"));
                        String NewTagRoom = cursor.getString(cursor.getColumnIndex("room"));
                        tag.setName(NewTagName + " " + NewTagRoom);
                    }
                    runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
                    break;
                }
            }
            focusedTagEPC = "";
        }
    }


static class MyTag {
    private final String epc;
    private String name;
    private String rssi;
    private String type;
    private int nbrDetections;
    private boolean isFavorites;

    public MyTag(String epc, String name, String type, String rssi, Boolean isFavorites) {
        this.epc = epc;
        this.name = name;
        this.type = type;
        this.rssi = rssi;
        this.nbrDetections = 0;
        this.isFavorites = isFavorites;
    }

    public String getEPC() {
        return epc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setRssi(String rssi) {
        this.rssi = rssi;
    }

    public String getRssi() {
        return this.rssi;
    }

    public void setNbrDetections(boolean reset) {
        if (reset)
            this.nbrDetections = 0;
        else
            this.nbrDetections++;
    }

    public int getNbrDetections() {
        return nbrDetections;
    }

    public Boolean getIsFavorites() {
        return isFavorites;
    }

    public void setIsFavorites(boolean isFavorites) {
        this.isFavorites = isFavorites;
    }

}

class TagsAdapter extends BaseAdapter {
    Context context;
    List<MyTag> tagsList;
    LayoutInflater inflater;

    public TagsAdapter(Context context, List<MyTag> tags) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.tagsList = tags;
    }

    @Override
    public int getCount() {
        return tagsList.size();
    }

    @Override
    public Object getItem(int position) {
        return tagsList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint({"InflateParams", "SetTextI18n"})
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewGroup vg;

        if (convertView != null) {
            vg = (ViewGroup) convertView;
        } else {
            vg = (ViewGroup) inflater.inflate(R.layout.listtag_items, null);
        }
        MyTag tag = tagsList.get(position);
        final TextView tvname = vg.findViewById(R.id.name);
        final TextView tvtype = vg.findViewById(R.id.type);
        final TextView tvcount = vg.findViewById(R.id.count);
        final TextView tvrssi = vg.findViewById(R.id.rssi);
        final ImageView favoritefull = vg.findViewById(R.id.favoritefull);
        final RelativeLayout favorite = vg.findViewById(R.id.favorite);
        if (tag.getIsFavorites())
            favoritefull.setVisibility(View.VISIBLE);
        else
            favoritefull.setVisibility(View.GONE);

        favorite.setOnClickListener(v -> {
            if (tag.getIsFavorites()) {
                tag.setIsFavorites(false);
                favoritefull.setVisibility(View.GONE);
                showToast("Favoris supprimé");
                saveFavoriteTags(tag.getEPC(), tag.getName(), tag.getType(), true);
                runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
            } else {
                showToast("Favoris ajouté");
                tag.setIsFavorites(true);
                favoritefull.setVisibility(View.VISIBLE);
                saveFavoriteTags(tag.getEPC(), tag.getName(), tag.getType(), false);
                runOnUiThread(() -> tagsAdapter.notifyDataSetChanged());
            }
        });
        tv_total.setText(total + " détections");
        tv_count.setText(tagsAdapter.getCount() + " étiquettes");
        if (tag.getRssi() != "Non détecté")
            tvrssi.setText(tag.getRssi() + " cm");
        tvrssi.setTextColor(Color.BLACK);
        tvcount.setText(String.valueOf(tag.getNbrDetections()));
        tvcount.setTextColor(Color.BLACK);
        tvname.setText(tag.getName());
        tvname.setTextColor(Color.BLACK);
        tvtype.setText(tag.getType());
        tvtype.setTextColor(Color.BLACK);
        return vg;
    }
}
}
