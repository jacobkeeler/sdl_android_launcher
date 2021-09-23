package org.luxoft.sdl_core;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class LocalSocketReceiver implements IpcReceiver {
    public static final String TAG = LocalSocketReceiver.class.getSimpleName();
    private LocalServerSocket mServer;
    private LocalSocket mReceiver;
    private InputStream mInputStream;

    private final Object mCallbackLock = new Object();
    private BleAdapterMessageCallback mCallback = null;
    private Thread mLoopTread;

    public static final int mBufferSize = 131072; // Copied from SDL INI file
    private String mSocketName;

    public LocalSocketReceiver(String socket_name) {
        mSocketName = socket_name;
    }

    @Override
    public void Connect(OnConnectCallback callback){
        Log.i(TAG, "Connect LocalSocketReceiver");
        try {
            mServer = new LocalServerSocket(mSocketName);
        } catch (IOException e) {
            Log.e(TAG, "The localSocketServer creation failed");
            e.printStackTrace();
        }

        try {
            Log.d(TAG, "LocalSocketReceiver begins to accept()");
            mReceiver = mServer.accept();
        } catch (IOException e) {
            Log.e(TAG, "LocalSocketReceiver accept() failed");
            e.printStackTrace();
        }

        try {
            mInputStream = mReceiver.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "getInputStream() failed");
            e.printStackTrace();
        }

        Log.d(TAG, "The client connect to LocalSocketReceiver");
        ReadLoop readLoop = new ReadLoop();
        mLoopTread = new Thread(readLoop);
        mLoopTread.start();

        callback.Execute();
    }

    @Override
    public void Disconnect(){
        Log.i(TAG, "Disconnect LocalSocketReceiver");

        if (mLoopTread != null) {
            mLoopTread.interrupt();
        }

        if (mReceiver != null){
            try {
                mReceiver.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mServer != null){
            try {
                mServer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void Read(BleAdapterMessageCallback callback){
        Log.i(TAG, "Going to read message");
        synchronized (mCallbackLock) {
            mCallback = callback;
        }
    }

    class ReadLoop implements Runnable {
        @Override
        public void run() {
            while (true) {
                byte[] buffer;
                buffer = new byte[mBufferSize];
                int mBytesRead;
                try {
                    mBytesRead = mInputStream.read(buffer);
                } catch (IOException e) {
                    Log.d(TAG, "There is an exception when reading socket");
                    e.printStackTrace();
                    break;
                }

                if (mBytesRead >= 0) {
                    Log.d(TAG, "Receive data from socket, bytesRead = "
                            + mBytesRead);
                    byte[] truncated_buffer = Arrays.copyOfRange(buffer, 0, mBytesRead);
                    String stringified_data = new String(truncated_buffer);
                    Log.d(TAG, "Receive data from socket = " + stringified_data);

                    synchronized (mCallbackLock) {
                        if (mCallback != null) {
                            mCallback.OnMessageReceived(truncated_buffer);
                        }
                    }
                }
            }
        }
    }
}
