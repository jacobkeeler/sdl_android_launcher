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

    public LocalSocketSender(String socket_name) {
        mSocketName = socket_name;
    }

    @Override
    public void Connect(OnConnectCallback callback){
        Log.i(TAG, "Connect LocalSocketSender");
        mSocket = new LocalSocket();
        if (!TryToConnect()) {
            Log.e(TAG, "Cannot connect to socket");
        }
        if (callback != null) {
            callback.Execute();
        }
    };

    private boolean TryToConnect() {
        final int connect_attempts = 10;
        final int attempt_interval_ms = 500;

        for (int i = 0; i < connect_attempts; ++i) {
            Log.d(TAG,"Attempt #" + (i + 1) + " to connect to socket...");

            try {
                mSocket.connect(new LocalSocketAddress(mSocketName));
            } catch (IOException e) {
                Log.e(TAG, "Connect() failed: " + e.getMessage() +
                         ". Retry in " + attempt_interval_ms + "ms");

                try {
                    TimeUnit.MILLISECONDS.sleep(attempt_interval_ms);
                } catch (InterruptedException interruptedException) {
                    break;
                }

                continue;
            }

            Log.d(TAG, "Successfully connected to socket");
            return true;
        }

        Log.e(TAG, "Connection attempts exceeded. Can't connect to socket");
        return false;
    }

    @Override
    public void Disconnect(){
        Log.i(TAG, "Disconnect LocalSocketSender");
        try {
            if(mSocket != null) {
                mSocket.getOutputStream().close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot close output stream");
            e.printStackTrace();
        }

        try {
            if(mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot close socket");
            e.printStackTrace();
        }
    };

    @Override
    public void Write(byte[] rawMessage){
        Log.i(TAG, "Going to write message");

        try {
            String str = new String(rawMessage);
            Log.d(TAG, "Write raw message: " + str);
            mSocket.getOutputStream().write(rawMessage);
        } catch (IOException e) {
            Log.e(TAG, "Cannot write to output stream");
            e.printStackTrace();
        }
    };
}
