package org.luxoft.sdl_core;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class CommunicationService extends Service {
        public static final String TAG = CommunicationService.class.getSimpleName();
        public final static String ACTION_CONNECT_ADAPTERS = "ACTION_CONNECT_ADAPTERS";
        public final static String ACTION_START_BLE = "ACTION_START_BLE";
        public final static String ACTION_START_BT ="ACTION_START_BT";
        public final static String ACTION_STOP_TRANSPORT = "ACTION_STOP_TRANSPORT";
        public final static String ACTION_SCAN = "ACTION_SCAN_BLE";
        public final static String ON_PERIPHERAL_READY = "ON_PERIPHERAL_READY";
        public final static String ON_DEVICE_SCAN_STARTED = "ON_DEVICE_SCAN_STARTED";
        public final static String ON_NATIVE_READY = "ON_NATIVE_READY";
        public final static String ON_MOBILE_MESSAGE_RECEIVED = "ON_MOBILE_MESSAGE_RECEIVED";
        public final static String MOBILE_DATA_EXTRA = "MOBILE_DATA_EXTRA";
        public final static String MOBILE_CONTROL_DATA_EXTRA = "MOBILE_CONTROL_DATA_EXTRA";
        public final static String MOBILE_DEVICE_DISCONNECTED_EXTRA = "MOBILE_DEVICE_DISCONNECTED_EXTRA";
        public final static String SDL_STOPPED_BY_USER_EXTRA = "SDL_STOPPED_BY_USER_EXTRA";
        public final static String ON_MOBILE_CONTROL_MESSAGE_RECEIVED = "ON_MOBILE_CONTROL_MESSAGE_RECEIVED";

        public enum TransportType {
            BLE,
            CLASSIC_BT
        }

        BleHandler mBleHandler;
        ClassicBtHandler mClassicBtHandler;
        Map<TransportType, JavaToNativeAdapter> mNativeAdapterThreadMap;
        WriteMessageCallback mCallback;
        TransportType mCurrentTransport;

        @Override
        public void onCreate() {
            mNativeAdapterThreadMap = new HashMap<>();
            registerReceiver(communicationServiceReceiver, makeCommunicationServiceIntentFilter());
            super.onCreate();
        }

        @Override
        public void onDestroy() {
            unregisterReceiver(communicationServiceReceiver);
            closeJavaAdapterThreads();
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private void closeJavaAdapterThreads() {
            for (Map.Entry<TransportType, JavaToNativeAdapter> entry : mNativeAdapterThreadMap.entrySet()) {
                try {
                    entry.getValue().setStopThread();
                    entry.getValue().join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mNativeAdapterThreadMap.clear();
        }

        private void initBleHandler(){
            mBleHandler = BleHandler.getInstance(this);
        }

        private void initClassicBTHandler(){
            mClassicBtHandler = ClassicBtHandler.getInstance(this);
        }

        private void initBleJavaToNativeAdapter() {
            final String sender_socket_address = AndroidSettings.getStringValue(AndroidSettings.IniParams.BleSenderSocketAddress);
            final String receiver_socket_address = AndroidSettings.getStringValue(AndroidSettings.IniParams.BleReceiverSocketAddress);
            final String control_socket_address = AndroidSettings.getStringValue(AndroidSettings.IniParams.BleControlSocketAddress);

            JavaToNativeAdapter thread = new JavaToNativeAdapter(CommunicationService.this,
                    sender_socket_address, receiver_socket_address, control_socket_address,
                    getTransportName(TransportType.BLE));
            mNativeAdapterThreadMap.put(TransportType.BLE, thread);
            thread.start();
        }

        private void initBtJavaToNativeAdapter() {
            final String sender_socket_address = AndroidSettings.getStringValue(AndroidSettings.IniParams.BtSenderSocketAddress);
            final String receiver_socket_address = AndroidSettings.getStringValue(AndroidSettings.IniParams.BtReceiverSocketAddress);
            final String control_socket_address = AndroidSettings.getStringValue(AndroidSettings.IniParams.BtControlSocketAddress);

            JavaToNativeAdapter thread = new JavaToNativeAdapter(CommunicationService.this,
                    sender_socket_address, receiver_socket_address, control_socket_address,
                    getTransportName(TransportType.CLASSIC_BT));
            mNativeAdapterThreadMap.put(TransportType.CLASSIC_BT, thread);
            thread.start();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            return super.onStartCommand(intent, flags, startId);
        }

        private String getTransportName(TransportType type) {
            switch (type) {
                case BLE:
                    return "[IPC][JAVA][BLE]";

                case CLASSIC_BT:
                    return "[IPC][JAVA][BT]";

                default:
                    Log.e(TAG, "[IPC][JAVA][Unknown]");
                    break;
            }
            return "[IPC][JAVA][Unknown]";
        }

        private final BroadcastReceiver communicationServiceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) {
                    return;
                }

                switch (intent.getAction()) {

                    case ACTION_CONNECT_ADAPTERS: {
                        Log.i(TAG, "ACTION_CONNECT_ADAPTERS received by communicationServiceReceiver");
                        initBleJavaToNativeAdapter();
                        initBtJavaToNativeAdapter();
                        break;
                    }

                    case ACTION_START_BLE: {
                        Log.i(TAG, "ACTION_START_BLE received by communicationServiceReceiver");
                        mCurrentTransport = TransportType.BLE;

                        initBleHandler();
                        final Intent scan_ble = new Intent(ACTION_SCAN);
                        context.sendBroadcast(scan_ble);

                        break;
                    }

                    case ACTION_START_BT: {
                        Log.i(TAG, "ACTION_START_BT received by communicationServiceReceiver");
                        mCurrentTransport = TransportType.CLASSIC_BT;

                        initClassicBTHandler();
                        final Intent scan_bt = new Intent(ACTION_SCAN);
                        context.sendBroadcast(scan_bt);

                        break;
                    }

                    case ACTION_SCAN: {
                        Log.i(TAG, "ACTION_SCAN received by communicationServiceReceiver");
                        switch (mCurrentTransport) {
                            case BLE:
                                mBleHandler.connect();
                                break;

                            case CLASSIC_BT:
                                mClassicBtHandler.DoDiscovery();
                                break;
                        }

                        final Intent scan_started_intent = new Intent(ON_DEVICE_SCAN_STARTED);
                        context.sendBroadcast(scan_started_intent);

                        break;
                    }

                    case ACTION_STOP_TRANSPORT: {
                        Log.i(TAG, "ACTION_STOP_TRANSPORT received by communicationServiceReceiver");

                        if (mCurrentTransport != null) {
                            switch (mCurrentTransport) {
                                case BLE:
                                    if (mBleHandler != null) {
                                        mBleHandler.disconnect();
                                    }
                                    break;

                                case CLASSIC_BT:
                                    if (mClassicBtHandler != null) {
                                        mClassicBtHandler.disconnect();
                                    }
                                    break;
                            }
                        }

                        if (intent.getBooleanExtra(SDL_STOPPED_BY_USER_EXTRA, false)) {
                            Log.i(TAG, "Finalizing Java adapters");
                            closeJavaAdapterThreads();
                        }

                        break;
                    }

                    case ON_NATIVE_READY: {
                        Log.i(TAG, "ON_NATIVE_READY received by communicationServiceReceiver");

                        switch (mCurrentTransport) {
                            case BLE: {
                                mCallback = new BleAdapterWriteMessageCallback();
                                JavaToNativeAdapter adapter = mNativeAdapterThreadMap.get(mCurrentTransport);
                                if (adapter != null) {
                                    adapter.ReadMessageFromNative(mCallback);
                                }
                                break;
                            }

                            case CLASSIC_BT: {
                                mCallback = new BtAdapterWriteMessageCallback();
                                JavaToNativeAdapter adapter = mNativeAdapterThreadMap.get(mCurrentTransport);
                                if (adapter != null) {
                                    adapter.ReadMessageFromNative(mCallback);
                                }
                                mClassicBtHandler.start_connected_thread();
                                break;
                            }
                        }
                        break;
                    }

                    case ON_PERIPHERAL_READY: {
                        Log.i(TAG, "ON_PERIPHERAL_READY received by communicationServiceReceiver");

                        JavaToNativeAdapter adapter = mNativeAdapterThreadMap.get(mCurrentTransport);
                        if (adapter != null) {
                            adapter.EstablishConnectionWithNative();
                        }

                        break;
                    }

                    case ON_MOBILE_MESSAGE_RECEIVED: {
                        Log.i(TAG, "ON_MOBILE_MESSAGE_RECEIVED received by communicationServiceReceiver");
                        byte[] mobile_message = intent.getByteArrayExtra(MOBILE_DATA_EXTRA);

                        JavaToNativeAdapter adapter = mNativeAdapterThreadMap.get(mCurrentTransport);
                        if (adapter != null) {
                            adapter.ForwardMessageToNative(mobile_message);
                        }

                        break;
                    }

                    case ON_MOBILE_CONTROL_MESSAGE_RECEIVED: {
                        Log.i(TAG, "ON_MOBILE_CONTROL_MESSAGE_RECEIVED received by communicationServiceReceiver");
                        byte[] mobile_control_message = intent.getByteArrayExtra(MOBILE_CONTROL_DATA_EXTRA);

                        JavaToNativeAdapter adapter = mNativeAdapterThreadMap.get(mCurrentTransport);
                        if (adapter != null) {
                            adapter.SendControlMessageToNative(mobile_control_message);
                            if (intent.getBooleanExtra(MOBILE_DEVICE_DISCONNECTED_EXTRA, false)) {
                                adapter.CloseConnectionWithNative();
                            }
                        }
                        break;
                    }

                    default:
                        Log.e(TAG, "Unexpected value: " + intent.getAction());
                }
            }
        };

    private static IntentFilter makeCommunicationServiceIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_CONNECT_ADAPTERS);
        intentFilter.addAction(ACTION_START_BLE);
        intentFilter.addAction(ACTION_START_BT);
        intentFilter.addAction(ACTION_STOP_TRANSPORT);
        intentFilter.addAction(ACTION_SCAN);
        intentFilter.addAction(ON_NATIVE_READY);
        intentFilter.addAction(ON_PERIPHERAL_READY);
        intentFilter.addAction(ON_MOBILE_MESSAGE_RECEIVED);
        intentFilter.addAction(ON_MOBILE_CONTROL_MESSAGE_RECEIVED);
        return intentFilter;
    }

    class BleAdapterWriteMessageCallback implements WriteMessageCallback {
        public void OnMessageReceived(byte[] rawMessage) {
            mBleHandler.writeMessage(rawMessage);
        }
    };

    class BtAdapterWriteMessageCallback implements WriteMessageCallback {
        public void OnMessageReceived(byte[] rawMessage) {
            mClassicBtHandler.writeMessage(rawMessage);
        }
    };
}

