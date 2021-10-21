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
    private WriteMessageCallback mCallback = null;
    private Thread mLoopTread;

    private final String mSocketName;
    private final String mTransportName;

    public LocalSocketReceiver(String socket_name,
                               String transport_name) {
        mSocketName = socket_name;
        mTransportName = transport_name;
    }

    @Override
    public void Connect(OnConnectCallback callback){
        Log.i(TAG, "Connect LocalSocketReceiver " + mTransportName);
        try {
            mServer = new LocalServerSocket(mSocketName);
        } catch (IOException e) {
            Log.e(TAG, "The localSocketServer creation failed "  + mTransportName);
            e.printStackTrace();
        }

        try {
            Log.d(TAG, "LocalSocketReceiver begins to accept() "  + mTransportName);
            mReceiver = mServer.accept();
        } catch (IOException e) {
            Log.e(TAG, "LocalSocketReceiver accept() failed "  + mTransportName);
            e.printStackTrace();
        }

        try {
            mInputStream = mReceiver.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "getInputStream() failed "  + mTransportName);
            e.printStackTrace();
        }

        Log.d(TAG, "The client connect to LocalSocketReceiver "  + mTransportName);
        ReadLoop readLoop = new ReadLoop();
        mLoopTread = new Thread(readLoop);
        mLoopTread.start();

        callback.Execute();
    }

    @Override
    public void Disconnect(){
        Log.i(TAG, "Disconnect LocalSocketReceiver "  + mTransportName);

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
    public void Read(WriteMessageCallback callback){
        Log.i(TAG, "Going to read message "  + mTransportName);
        synchronized (mCallbackLock) {
            mCallback = callback;
        }
    }

    class ReadLoop implements Runnable {
        @Override
        public void run() {
            while (true) {
                byte[] buffer;
                buffer = new byte[AndroidSettings.getIntValue(AndroidSettings.IniParams.BufferSize)];
                int mBytesRead;
                try {
                    mBytesRead = mInputStream.read(buffer);
                } catch (IOException e) {
                    Log.d(TAG, "There is an exception when reading socket " + mTransportName);
                    e.printStackTrace();
                    break;
                }

                if (mBytesRead >= 0) {
                    Log.d(TAG, "Receive data from socket, bytesRead = "
                            + mBytesRead + " "  + mTransportName );
                    byte[] truncated_buffer = Arrays.copyOfRange(buffer, 0, mBytesRead);
                    String stringified_data = new String(truncated_buffer);
                    Log.d(TAG, "Receive data from socket = " + stringified_data + " " + mTransportName);

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
