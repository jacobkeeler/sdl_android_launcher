package org.luxoft.sdl_core;

import java.util.HashMap;

public class AndroidSettings {

    private static final String AndroidSection = "ANDROID";
    private static final String TAG = AndroidSettings.class.getSimpleName();

    private static HashMap<String, String> mValues;

    public enum IniParams {
        BufferSize,
        BleReceiverSocketAddress,
        BleControlSocketAddress,
        BleSenderSocketAddress,
        BtReceiverSocketAddress,
        BtControlSocketAddress,
        BtSenderSocketAddress,
        PreferredMtu,
        SdlTesterServiceUUID,
        MobileNotificationCharacteristic,
        MobileResponseCharacteristic
    }

    private static String getStrValue(IniLoader iniFile, String section, String Key, String defaultValue) {
        String result = defaultValue;

        if(iniFile.containsKey(section, Key)) {
            result = iniFile.getValue(section, Key).trim().replaceAll("^\"+|\"+$", "");
        }

        return result;
    }

    public static void loadSettings(String filepath) {

        HashMap<String, String> defaulValues  = new HashMap<String, String>() {{
            put(IniParams.BufferSize.toString(),                       "131072");
            put(IniParams.BleReceiverSocketAddress.toString(),         "./localBleReader");
            put(IniParams.BleControlSocketAddress.toString(),          "./localBleControl");
            put(IniParams.BleSenderSocketAddress.toString(),           "./localBleWriter");
            put(IniParams.BtReceiverSocketAddress.toString(),          "./localBtReader");
            put(IniParams.BtControlSocketAddress.toString(),           "./localBtControl");
            put(IniParams.BtSenderSocketAddress.toString(),            "./localBtWriter");
            put(IniParams.PreferredMtu.toString(),                     "512");
            put(IniParams.SdlTesterServiceUUID.toString(),             "00001101-0000-1000-8000-00805f9b34fb");
            put(IniParams.MobileNotificationCharacteristic.toString(), "00001102-0000-1000-8000-00805f9b34fb");
            put(IniParams.MobileResponseCharacteristic.toString(),     "00001104-0000-1000-8000-00805f9b34fb");
        }};

        mValues = new HashMap<>();
        // fill with default values
        for (IniParams key : IniParams.values()) {
            final String keyName = key.toString();
            assert defaulValues.containsKey(keyName) : "Default value is not found for key: " + keyName;
            mValues.put(keyName, defaulValues.get(keyName));
        }

        IniLoader iniLoader = new IniLoader();
        iniLoader.load(filepath + "/androidSmartDeviceLink.ini");

        for (IniParams key : IniParams.values()) {
            final String keyName = key.toString();
            mValues.put(keyName, getStrValue(iniLoader, AndroidSection, keyName, defaulValues.get(keyName)));
        }
    }

    public static String getStringValue(IniParams param) {
        final String paramName = param.toString();
        if(mValues.containsKey(paramName)) {
            return mValues.get(paramName);
        }
        return "";
    }

    public static int getIntValue(IniParams param) {
        int result = 0;
        final String paramName = param.toString();

        if (mValues.containsKey(paramName)) {
            String value = mValues.get(paramName);
            try {
                result = Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
            }
        }
        return result;
    }
}
