package org.luxoft.sdl_core;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import static org.luxoft.sdl_core.CommunicationService.ON_NATIVE_CONTROL_READY;
import static org.luxoft.sdl_core.CommunicationService.ON_NATIVE_READY;

public class JavaToNativeAdapter extends Thread {
    public static final String TAG = JavaToNativeAdapter.class.getSimpleName();
    private static final int WRITE_ID = 1;
    private static final int READ_ID = 2;
    private static final int WRITE_CONTROL_ID = 3;
    private static final int CONNECT_CONTROL_WRITER_ID = 4;
    private static final int CONNECT_READER_ID = 5;
    private static final int CONNECT_WRITER_ID = 6;
    private static final int DISCONNECT_ID = 7;
    public static final String CONTROL_SOCKET_ADDRESS = "./localBleControl";


    Handler mHandler;
    IpcSender mWriter;
    IpcSender mControlWriter;
    IpcReceiver mReader;
    BleAdapterMessageCallback mCallback;
    private final Context mContext;

    JavaToNativeAdapter(Context context, String sender_socket_address, String receiver_socket_address){
        mControlWriter = new LocalSocketSender(CONTROL_SOCKET_ADDRESS);
        mWriter = new LocalSocketSender(sender_socket_address);
        mReader = new LocalSocketReceiver(receiver_socket_address);
        mContext = context;
    }

    public void EstablishConnectionWithNative() {
        Log.i(TAG, "Establishing communication with native");
        Message message = mHandler.obtainMessage(CONNECT_READER_ID);
        mHandler.sendMessage(message);
    }

    public void CloseConnectionWithNative() {
        Log.i(TAG, "Closing communication with native");
        Message message = mHandler.obtainMessage(DISCONNECT_ID);
        mHandler.sendMessage(message);
    }

    public void ForwardMessageToNative(byte[] rawMessage){
        String stringified_message = new String(rawMessage);
        Log.i(TAG, "Forward message to native: " + stringified_message);
        Message message = mHandler.obtainMessage(WRITE_ID, rawMessage);
        mHandler.sendMessage(message);
    }

    public void ReadMessageFromNative(BleAdapterMessageCallback callback){
        Log.i(TAG, "Save callback to read message from native");
        mCallback = callback;
        Message message = mHandler.obtainMessage(READ_ID, mCallback);
        mHandler.sendMessage(message);
    }

    public void SendControlMessageToNative(byte[] rawMessage){
        String stringified_message = new String(rawMessage);
        Log.i(TAG, "Control message to native: " + stringified_message);
        Message message = mHandler.obtainMessage(WRITE_CONTROL_ID, rawMessage);
        mHandler.sendMessage(message);
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler(Looper.myLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case WRITE_ID:
                        mWriter.Write((byte[]) msg.obj);
                        break;
                    case READ_ID:
                        mReader.Read((BleAdapterMessageCallback) msg.obj);
                        break;
                    case WRITE_CONTROL_ID:
                        mControlWriter.Write((byte[]) msg.obj);
                        break;
                    case CONNECT_READER_ID:
                        mReader.Connect(new OnConnectCallback() {
                            @Override
                            public void Execute() {
                                Log.i(TAG, "BLE reader is connected");
                                Message message = mHandler.obtainMessage(CONNECT_WRITER_ID);
                                mHandler.sendMessage(message);
                            }
                        });
                        break;
                    case CONNECT_WRITER_ID:
                        mWriter.Connect(new OnConnectCallback() {
                            @Override
                            public void Execute() {
                                Log.i(TAG, "Writer is connected");
                                final Intent intent = new Intent(ON_NATIVE_READY);
                                mContext.sendBroadcast(intent);
                            }
                        });
                        break;
                    case CONNECT_CONTROL_WRITER_ID:
                        mControlWriter.Connect(new OnConnectCallback() {
                            @Override
                            public void Execute() {
                                Log.i(TAG, "Control writer is connected");
                                final Intent intent = new Intent(ON_NATIVE_CONTROL_READY);
                                mContext.sendBroadcast(intent);
                            }
                        });
                        break;
                    case DISCONNECT_ID:
                        Log.i(TAG, "Disconnecting reader");
                        mReader.Disconnect();

                        Log.i(TAG, "Disconnecting writer");
                        mWriter.Disconnect();
                        break;
                }
            }
        };

        Message message = mHandler.obtainMessage(CONNECT_CONTROL_WRITER_ID);
        mHandler.sendMessage(message);

        Looper.loop();
        mReader.Disconnect();
        mWriter.Disconnect();
        mControlWriter.Disconnect();
    }

    public void setStopThread(){
        mHandler.getLooper().quit();
    }

}