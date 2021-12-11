package com.example.visio_conduits.utils;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.example.visio_conduits.BaseActivity;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2017-5-22.
 */

public class FileUtils extends BaseActivity {
    static String TAG = "FileUtils";
    public static String ADDR = "btaddress";
    public static String NAME = "btname";
    public static String TYPE = "tagType";

    public static void saveXmlList(List<String[]> data, String fileName) {
        try {
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + fileName);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream fos = new FileOutputStream(file);
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(fos, "utf-8");
            serializer.startDocument("utf-8", true);
            serializer.startTag(null, "root");
            for (int i = 0; i < data.size(); i++) {
                serializer.startTag(null, "bt");
                serializer.startTag(null, ADDR);
                serializer.text(data.get(i)[0]);
                serializer.endTag(null, ADDR);
                serializer.startTag(null, NAME);
                serializer.text(data.get(i)[1]);
                serializer.endTag(null, NAME);
                if (fileName.equals(FAV_TAGS_FILE_NAME))
                {
                    serializer.startTag(null, TYPE);
                    serializer.text(data.get(i)[2]);
                    serializer.endTag(null, TYPE);
                }
                serializer.endTag(null, "bt");
            }
            serializer.endTag(null, "root");
            serializer.endDocument();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String[]> readXmlList(String fileName) {
        ArrayList<String[]> list = new ArrayList<String[]>();
        try {
            File path = new File(Environment.getExternalStorageDirectory() + File.separator + fileName);
            if (!path.exists()) {
                return list;
            }
            FileInputStream fis = new FileInputStream(path);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "utf-8");
            int eventType = parser.getEventType(); // 获得事件类型
            String addr = null;
            String name = null;
            String type = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName(); // 获得当前节点的名称
                switch (eventType) {
                    case XmlPullParser.START_TAG: // 当前等于开始节点
                        if ("root".equals(tagName)) { //
                        } else if ("bt".equals(tagName)) { //
                        } else if (ADDR.equals(tagName)) { // <name>
                            addr = parser.nextText();
                        } else if (NAME.equals(tagName)) { // <age>
                            name = parser.nextText();
                        } else if (TYPE.equals(tagName)) { // <age>
                            type = parser.nextText();
                        }
                        break;
                    case XmlPullParser.END_TAG: // </persons>
                        if ("bt".equals(tagName)) {
                            Log.i(TAG, "addr---" + addr);
                            Log.i(TAG, "name---" + name);
                            String[] str = new String[3];
                            str[0] = addr;
                            str[1] = name;
                            str[2] = type;
                            list.add(str);
                        }
                        break;
                    default:
                        break;
                }
                eventType = parser.next(); // 获得下一个事件类型
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return list;
    }

    public static void clearXmlList(String fileName) {
        List<String[]> list = readXmlList(fileName);
        list.clear();
        saveXmlList(list, fileName);
    }

    public static void writeFile(String fileName, String data, boolean append) {
        if (TextUtils.isEmpty(data))
            return;

        int index = fileName.lastIndexOf(File.separator);
        if (index != -1) {
            File filePath = new File(fileName.substring(0, index));
            if (!filePath.exists()) {
                filePath.mkdirs();
            }
        } else {
            return;
        }

        File file = new File(fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
                Runtime runtime = Runtime.getRuntime();
                runtime.exec("chmod 0666 " + file);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file, append);
            fileOutputStream.write(data.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch (Exception e) {
            }
        }
    }
}
