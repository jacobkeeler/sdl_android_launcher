package org.luxoft.sdl_core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IniLoader {

    private HashMap<String, HashMap<String, String>> mDataMap;
    private String mFilePath;
    private boolean mIsLoaded = false;

    public boolean load(String filePath) {
        mFilePath = filePath;
        return loadProcess(filePath);
    }

    public boolean reload() {
        return isBlank(mFilePath) && loadProcess(mFilePath);
    }

    private boolean loadProcess(String filePath) {
        mIsLoaded = false;
        mDataMap = new HashMap<>();
        try {
            FileReader fileReader = new FileReader(new File(filePath));
            BufferedReader br = new BufferedReader(fileReader);
            String line = br.readLine();

            String section = null;
            HashMap<String, String> map = new HashMap<>();
            while (line != null) {
                line = line.trim();

                //Blank line
                if (isBlank(line)) {
                    // no process
                }
                //Comment line
                else if (line.charAt(0) == ';') {
                    // no process
                }
                //Section line
                else if (line.charAt(0) == '[' && line.charAt(line.length() - 1) == ']') {
                    section = line.substring(1, line.length() - 1);
                    map = new HashMap<>();
                }
                //Parameter line
                else if (line.length() >= 3 && line.contains("=") && line.length() > line.indexOf("=") + 1) {
                    final int tIndex = line.indexOf("=");
                    String key = line.substring(0, tIndex);
                    String value = line.substring(tIndex + 1);

                    map.put(key.trim(), value);
                    mDataMap.put(section.trim(), map);
                }

                line = br.readLine();
            }
            br.close();
        } catch (IOException e) {
            return false;
        }
        mIsLoaded = true;
        return true;
    }

    public HashMap<String, HashMap<String, String>> getAllDataMap() {
        if (mIsLoaded) {
            return mDataMap;
        }
        return null;
    }

    public Map<String, String> getSectionDataMap(String section) {
        if (mIsLoaded) {
            return mDataMap.get(section);
        }
        return null;
    }

    public String getValue(String section, String key) {
        if (mIsLoaded) {
            HashMap<String, String> map = mDataMap.get(section);
            if (map != null) {
                return map.get(key);
            }
        }
        return null;
    }

    public boolean containsSection(String section) {
        if (mIsLoaded) {
            return mDataMap.containsKey(section);
        }
        return false;
    }

    public boolean containsKey(String section, String key) {
        if (mIsLoaded) {
            HashMap<String, String> map = mDataMap.get(section);
            return map != null && map.containsKey(key);
        }
        return false;
    }

    private boolean isBlank(String str) {
        return str == null || str.length() == 0;
    }
}
