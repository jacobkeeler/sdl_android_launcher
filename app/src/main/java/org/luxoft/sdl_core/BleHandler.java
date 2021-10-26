package org.luxoft.sdl_core;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.WriteType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static org.luxoft.sdl_core.CommunicationService.ACTION_SCAN;
import static org.luxoft.sdl_core.CommunicationService.MOBILE_DATA_EXTRA;
import static org.luxoft.sdl_core.CommunicationService.MOBILE_DEVICE_DISCONNECTED_EXTRA;
import static org.luxoft.sdl_core.CommunicationService.ON_MOBILE_MESSAGE_RECEIVED;
import static org.luxoft.sdl_core.CommunicationService.ON_PERIPHERAL_READY;
import static org.luxoft.sdl_core.CommunicationService.ON_MOBILE_CONTROL_MESSAGE_RECEIVED;
import static org.luxoft.sdl_core.CommunicationService.MOBILE_CONTROL_DATA_EXTRA;

import static org.luxoft.sdl_core.TransportContract.PARAM_ACTION;
import static org.luxoft.sdl_core.TransportContract.PARAM_NAME;
import static org.luxoft.sdl_core.TransportContract.PARAM_ADDRESS;
import static org.luxoft.sdl_core.TransportContract.PARAMS;

class BleHandler {
    public BluetoothCentralManager central;
    private static BleHandler instance = null;
    private BluetoothPeripheral mPeripheral = null;
    private final Context context;
    private final Handler handler = new Handler();
    private final BluetoothLongReader mLongReader = new BluetoothLongReader();
    private final BluetoothLongWriter mLongWriter = new BluetoothLongWriter();

    public static final String TAG = BleHandler.class.getSimpleName();

    private String GenerateDisconnectMessage(BluetoothPeripheral peripheral) {

        try {
            JSONObject message = new JSONObject();
            message.put(PARAM_ACTION, "ON_DEVICE_DISCONNECTED");

            JSONObject params = new JSONObject();
            params.put(PARAM_ADDRESS, peripheral.getAddress());
            message.put(PARAMS, params);
            return message.toString();
        } catch (JSONException ex) {
            Log.i(TAG, "ON_DEVICE_DISCONNECTED msg Failed", ex);
        }
        return null;
    }

    private String GenerateConnectedMessage(BluetoothPeripheral peripheral) {

        try {
            JSONObject message = new JSONObject();
            message.put(PARAM_ACTION, "ON_DEVICE_CONNECTED");
            JSONObject params = new JSONObject();
            params.put(PARAM_NAME, peripheral.getName());
            params.put(PARAM_ADDRESS, peripheral.getAddress());
            message.put(PARAMS, params);
            return message.toString();
        } catch (JSONException ex) {
            Log.i(TAG, "ON_DEVICE_CONNECTED msg Failed", ex);
        }
        return null;
    }

    public static synchronized BleHandler getInstance(Context context) {
        if (instance == null) {
            instance = new BleHandler(context);
        }
        return instance;
    }

    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(BluetoothPeripheral peripheral) {

            peripheral.requestMtu(AndroidSettings.getIntValue(AndroidSettings.IniParams.PreferredMtu));

            final String sdlTesterUUID = AndroidSettings.getStringValue(AndroidSettings.IniParams.SdlTesterServiceUUID);
            final String mobNotifChar = AndroidSettings.getStringValue(AndroidSettings.IniParams.MobileNotificationCharacteristic);
            peripheral.setNotify(UUID.fromString(sdlTesterUUID), UUID.fromString(mobNotifChar),true);

            final Intent intent = new Intent(ON_PERIPHERAL_READY);
            context.sendBroadcast(intent);
        }

        @Override
        public void onMtuChanged(BluetoothPeripheral peripheral, int mtu, GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                mLongReader.setMtu(mtu);
                mLongWriter.setMtu(mtu);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                Log.i(TAG, "SUCCESS: Writing to " + characteristic.getUuid());
                mLongWriter.onLongMessageSent();
            } else {
                Log.i(TAG, "ERROR: Failed writing to " + characteristic.getUuid() + " with " + status);
            }
        }

        @Override
        public void onCharacteristicUpdate(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, GattStatus status) {
            if (status != GattStatus.SUCCESS) return;

            UUID characteristicUUID = characteristic.getUuid();
            final String mobNotifChar = AndroidSettings.getStringValue(AndroidSettings.IniParams.MobileNotificationCharacteristic);
            if (characteristicUUID.equals(UUID.fromString(mobNotifChar))) {
                mLongReader.processReadOperation(value);
            }
        }
    };

    // Callback for central
    private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {

        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            Log.i(TAG, "connected to " + peripheral.getName());
            mPeripheral = peripheral;

            String ctrl_msg = GenerateConnectedMessage(peripheral);
            if(ctrl_msg != null) {
                final Intent intent = new Intent(ON_MOBILE_CONTROL_MESSAGE_RECEIVED);
                intent.putExtra(MOBILE_CONTROL_DATA_EXTRA, ctrl_msg.getBytes());
                context.sendBroadcast(intent);
            }
        }

        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, final HciStatus status) {
            Log.e(TAG, "connection " + peripheral.getName() + " failed with status " + status);
        }

        @Override
        public void onDisconnectedPeripheral(final BluetoothPeripheral peripheral, final HciStatus status) {
            Log.d(TAG, "Disconnected from " + peripheral.getName());

            String ctrl_msg = GenerateDisconnectMessage(peripheral);
            if(ctrl_msg != null) {
                final Intent intent = new Intent(ON_MOBILE_CONTROL_MESSAGE_RECEIVED);
                intent.putExtra(MOBILE_CONTROL_DATA_EXTRA, ctrl_msg.getBytes());
                intent.putExtra(MOBILE_DEVICE_DISCONNECTED_EXTRA, true);
                context.sendBroadcast(intent);
            }
            mLongReader.resetBuffer();
            mLongWriter.resetBuffer();

            if (mPeripheral != null && peripheral.getAddress().equals(mPeripheral.getAddress())) {
                // Restart devices scanning
                final Intent scan_ble = new Intent(ACTION_SCAN);
                context.sendBroadcast(scan_ble);
            }
        }

        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            Log.v(TAG, "Found peripheral " + peripheral.getName());
            central.stopScan();
            central.connectPeripheral(peripheral, peripheralCallback);
        }
    };

    public void writeMessage(byte[] message){

        if (mPeripheral == null) {
            Log.e(TAG, "mPeripheral is null");
            return;
        }


        mLongWriter.processWriteOperation(message);
    }

    private BleHandler(Context context) {
        this.context = context;

        mLongReader.setCallback(new BluetoothLongReader.LongReaderCallback() {
            @Override
            public void OnLongMessageReceived(byte[] message) {
                final Intent intent = new Intent(ON_MOBILE_MESSAGE_RECEIVED);
                intent.putExtra(MOBILE_DATA_EXTRA, message);
                BleHandler.this.context.sendBroadcast(intent);
            }
        });

        mLongWriter.setCallback(new BluetoothLongWriter.LongWriterCallback() {
            @Override
            public void OnLongMessageReady(byte[] message) {

                final String sdlTesterUUID = AndroidSettings.getStringValue(AndroidSettings.IniParams.SdlTesterServiceUUID);
                final String mobRespChar = AndroidSettings.getStringValue(AndroidSettings.IniParams.MobileResponseCharacteristic);

                BluetoothGattCharacteristic responseCharacteristic = mPeripheral.getCharacteristic(UUID.fromString(sdlTesterUUID)
                        , UUID.fromString(mobRespChar));
                if (responseCharacteristic != null) {
                    if ((responseCharacteristic.getProperties() & PROPERTY_WRITE) > 0) {
                        mPeripheral.writeCharacteristic(responseCharacteristic, message, WriteType.WITH_RESPONSE);
                    }
                }
            }
        });
    }

    public void disconnect() {
        Log.d(TAG, "Closing BLE handler...");
        handler.removeCallbacksAndMessages(null);
        if (central != null) {
            if (mPeripheral != null) {
                central.cancelConnection(mPeripheral);
                mPeripheral = null;
            }
            central.close();
            central = null;
        }
    }

    public void connect() {
        Log.d(TAG, "Prepare to start scanning...");
        central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());

        // Scan for peripherals with a certain service UUIDs
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Searching for SDL-compatible peripherals...");
                UUID[] servicesToSearch = new UUID[1];
                servicesToSearch[0] = UUID.fromString(AndroidSettings.getStringValue(AndroidSettings.IniParams.SdlTesterServiceUUID));
                central.scanForPeripheralsWithServices(servicesToSearch);
            }
        }, 1000);
    }
}
