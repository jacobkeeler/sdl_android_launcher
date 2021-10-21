package org.luxoft.sdl_core;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class CommunicationService extends Service {
        public static final String TAG = CommunicationService.class.getSimpleName();
        public final static String ACTION_START_BLE = "ACTION_START_BLE";
        public final static String ACTION_START_BT ="ACTION_START_BT";
        public final static String ACTION_STOP_TRANSPORT = "ACTION_STOP_TRANSPORT";
        public final static String ACTION_DISCONNECT_FROM_NATIVE = "ACTION_DISCONNECT_FROM_NATIVE";
        public final static String ACTION_SCAN = "ACTION_SCAN_BLE";
        public final static String ON_PERIPHERAL_READY = "ON_PERIPHERAL_READY";
        public final static String ON_BLE_SCAN_STARTED = "ON_BLE_SCAN_STARTED";
        public final static String ON_NATIVE_READY = "ON_NATIVE_READY";
        public final static String ON_NATIVE_CONTROL_READY = "ON_NATIVE_CONTROL_READY";
        public final static String ON_MOBILE_MESSAGE_RECEIVED = "ON_MOBILE_MESSAGE_RECEIVED";
        public final static String MOBILE_DATA_EXTRA = "MOBILE_DATA_EXTRA";
        public final static String MOBILE_CONTROL_DATA_EXTRA = "MOBILE_CONTROL_DATA_EXTRA";
        public final static String MOBILE_DEVICE_DISCONNECTED_EXTRA = "MOBILE_DEVICE_DISCONNECTED_EXTRA";
        public final static String ON_MOBILE_CONTROL_MESSAGE_RECEIVED = "ON_MOBILE_CONTROL_MESSAGE_RECEIVED";

        public final static String BLE_RECEIVER_SOCKET_ADDRESS = "./localBleReader";
        public final static String BLE_SENDER_SOCKET_ADDRESS = "./localBleWriter";
        public final static String BLE_CONTROL_RECEIVER_SOCKET_ADDRESS = "./localBleControl";

        public final static String BT_RECEIVER_SOCKET_ADDRESS = "./localBtReader";
        public final static String BT_SENDER_SOCKET_ADDRESS = "./localBtWriter";
        public final static String BT_CONTROL_RECEIVER_SOCKET_ADDRESS = "./localBtControl";

        public enum TransportType {
        BLE,
        CLASSIC_BT
        }

        BleHandler mBleHandler;
        ClassicBtHandler mClassicBtHandler;
        JavaToNativeAdapter mNativeAdapterThread;
        WriteMessageCallback mCallback;
        TransportType mCurrentTransport;

        @Override
        public void onCreate() {
            registerReceiver(communicationServiceReceiver, makeCommunicationServiceIntentFilter());
            super.onCreate();
        }

        @Override
        public void onDestroy() {
            unregisterReceiver(communicationServiceReceiver);
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private void initBleHandler(){
            mBleHandler = BleHandler.getInstance(this);
        }

        private void initClassicBTHandler(){
            mClassicBtHandler = ClassicBtHandler.getInstance(this);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            return super.onStartCommand(intent, flags, startId);
        }

        private String getTransportName() {
            switch (mCurrentTransport) {
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

                    case ACTION_START_BLE:
                        Log.i(TAG, "ACTION_START_BLE received by communicationServiceReceiver");
                        mCurrentTransport = TransportType.BLE;
                        mNativeAdapterThread = new JavaToNativeAdapter(CommunicationService.this,
                                BLE_SENDER_SOCKET_ADDRESS, BLE_RECEIVER_SOCKET_ADDRESS,
                                BLE_CONTROL_RECEIVER_SOCKET_ADDRESS,
                                getTransportName());
                        mNativeAdapterThread.start();
                        break;

                    case ACTION_START_BT:
                        Log.i(TAG, "ACTION_START_BT received by communicationServiceReceiver");
                        mCurrentTransport = TransportType.CLASSIC_BT;
                        mNativeAdapterThread = new JavaToNativeAdapter(CommunicationService.this,
                                BT_SENDER_SOCKET_ADDRESS, BT_RECEIVER_SOCKET_ADDRESS,
                                BT_CONTROL_RECEIVER_SOCKET_ADDRESS,
                                getTransportName());
                        mNativeAdapterThread.start();
                        break;

                    case ACTION_SCAN:
                        Log.i(TAG, "ACTION_SCAN received by communicationServiceReceiver");
                        switch (mCurrentTransport) {
                            case BLE:
                                mBleHandler.connect();
                                break;

                            case CLASSIC_BT:
                                mClassicBtHandler.DoDiscovery();
                                break;
                        }

                        final Intent scan_started_intent = new Intent(ON_BLE_SCAN_STARTED);
                        context.sendBroadcast(scan_started_intent);

                        break;

                    case ACTION_STOP_TRANSPORT:
                        Log.i(TAG, "ACTION_STOP received by communicationServiceReceiver");

                        switch (mCurrentTransport) {
                            case BLE:
                                mBleHandler.disconnect();
                                break;

                            case CLASSIC_BT:
                                mClassicBtHandler.disconnect();
                                break;
                        }

                        break;

                    case ACTION_DISCONNECT_FROM_NATIVE:
                        Log.i(TAG, "ACTION_DISCONNECT_FROM_NATIVE received by communicationServiceReceiver");

                        try {
                            mNativeAdapterThread.setStopThread();
                            mNativeAdapterThread.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mNativeAdapterThread = null;

                        break;

                    case ON_NATIVE_READY:
                        Log.i(TAG, "ON_NATIVE_READY received by communicationServiceReceiver");

                        switch (mCurrentTransport) {
                            case BLE:
                                mCallback = new BleAdapterWriteMessageCallback();
                                if (mNativeAdapterThread != null) {
                                    mNativeAdapterThread.ReadMessageFromNative(mCallback);
                                }
                                break;

                            case CLASSIC_BT:
                                mCallback = new BtAdapterWriteMessageCallback();
                                if (mNativeAdapterThread != null) {
                                    mNativeAdapterThread.ReadMessageFromNative(mCallback);
                                }
                                mClassicBtHandler.start_connected_thread();
                                break;
                        }
                        break;

                    case ON_NATIVE_CONTROL_READY:
                        Log.i(TAG, "ON_NATIVE_CONTROL_READY received by communicationServiceReceiver");
                        switch (mCurrentTransport) {
                            case BLE:
                                initBleHandler();
                                final Intent scan_ble = new Intent(ACTION_SCAN);
                                context.sendBroadcast(scan_ble);
                                break;

                            case CLASSIC_BT:
                                initClassicBTHandler();
                                final Intent scan_bt = new Intent(ACTION_SCAN);
                                context.sendBroadcast(scan_bt);
                                break;
                        }
                        break;


                    case ON_PERIPHERAL_READY:
                        Log.i(TAG, "ON_PERIPHERAL_READY received by communicationServiceReceiver");
                        if (mNativeAdapterThread != null) {
                            mNativeAdapterThread.EstablishConnectionWithNative();
                        }

                        break;

                    case ON_MOBILE_MESSAGE_RECEIVED:
                        Log.i(TAG, "ON_MOBILE_MESSAGE_RECEIVED received by communicationServiceReceiver");
                        byte[] mobile_message = intent.getByteArrayExtra(MOBILE_DATA_EXTRA);
                        if (mNativeAdapterThread != null) {
                            mNativeAdapterThread.ForwardMessageToNative(mobile_message);
                        }

                        break;

                    case ON_MOBILE_CONTROL_MESSAGE_RECEIVED:
                        Log.i(TAG, "ON_MOBILE_CONTROL_MESSAGE_RECEIVED received by communicationServiceReceiver");
                        byte[] mobile_control_message = intent.getByteArrayExtra(MOBILE_CONTROL_DATA_EXTRA);
                        if (mNativeAdapterThread != null) {
                            mNativeAdapterThread.SendControlMessageToNative(mobile_control_message);
                            if (intent.getBooleanExtra(MOBILE_DEVICE_DISCONNECTED_EXTRA, false)) {
                                mNativeAdapterThread.CloseConnectionWithNative();
                            }
                        }
                        break;

                    default:
                        Log.e(TAG, "Unexpected value: " + intent.getAction());
                }
            }
        };

    private static IntentFilter makeCommunicationServiceIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_START_BLE);
        intentFilter.addAction(ACTION_START_BT);
        intentFilter.addAction(ACTION_STOP_TRANSPORT);
        intentFilter.addAction(ACTION_SCAN);
        intentFilter.addAction(ON_NATIVE_READY);
        intentFilter.addAction(ON_NATIVE_CONTROL_READY);
        intentFilter.addAction(ON_PERIPHERAL_READY);
        intentFilter.addAction(ON_MOBILE_MESSAGE_RECEIVED);
        intentFilter.addAction(ON_MOBILE_CONTROL_MESSAGE_RECEIVED);
        intentFilter.addAction(ACTION_DISCONNECT_FROM_NATIVE);
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

