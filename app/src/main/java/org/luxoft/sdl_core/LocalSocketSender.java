package org.luxoft.sdl_core;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LocalSocketSender implements IpcSender{
    public static final String TAG = LocalSocketSender.class.getSimpleName();
    LocalSocket mSocket;
    private String mSocketName;
    private String mTransportName;

    public LocalSocketSender(String socket_name,
                             String transport_name) {
        mSocketName = socket_name;
        mTransportName = transport_name;
    }

    @Override
    public void Connect(OnConnectCallback callback){
        Log.i(TAG, "Connect LocalSocketSender " + mTransportName);
        mSocket = new LocalSocket();
        if (!TryToConnect()) {
            Log.e(TAG, "Cannot connect to socket " + mTransportName);
        }
        if (callback != null) {
            callback.Execute();
        }
    };

    private boolean TryToConnect() {
        final int connect_attempts = 10;
        final int attempt_interval_ms = 500;

        for (int i = 0; i < connect_attempts; ++i) {
            Log.d(TAG,"Attempt #" + (i + 1) + " to connect to socket... " + mTransportName);

            try {
                mSocket.connect(new LocalSocketAddress(mSocketName));
            } catch (IOException e) {
                Log.e(TAG, "Connect() failed: " + e.getMessage() +
                         ". Retry in " + attempt_interval_ms + "ms "  + mTransportName);

                try {
                    TimeUnit.MILLISECONDS.sleep(attempt_interval_ms);
                } catch (InterruptedException interruptedException) {
                    break;
                }

                continue;
            }

            Log.d(TAG, "Successfully connected to socket "  + mTransportName);
            return true;
        }

        Log.e(TAG, "Connection attempts exceeded. Can't connect to socket "  + mTransportName);
        return false;
    }

    @Override
    public void Disconnect(){
        Log.i(TAG, "Disconnect LocalSocketSender "  + mTransportName);
        try {
            if(mSocket != null) {
                mSocket.getOutputStream().close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot close output stream "  + mTransportName);
            e.printStackTrace();
        }

        try {
            if(mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot close socket "  + mTransportName);
            e.printStackTrace();
        }
    };

    @Override
    public void Write(byte[] rawMessage){
        Log.i(TAG, "Going to write message "  + mTransportName);

        try {
            String str = new String(rawMessage);
            Log.d(TAG, "Write raw message: " + str + " " + mTransportName);
            mSocket.getOutputStream().write(rawMessage);
        } catch (IOException e) {
            Log.e(TAG, "Cannot write to output stream " +  mTransportName);
            e.printStackTrace();
        }
    };
}
