package org.luxoft.sdl_core;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class BleLocalSocketReader implements BleReader {
    public static final String TAG = BleLocalSocketReader.class.getSimpleName();
    private LocalServerSocket mServer;
    private LocalSocket mReceiver;
    private InputStream mInputStream;

    private final Object mCallbackLock = new Object();
    private BleAdapterMessageCallback mCallback = null;
    private Thread mLoopTread;

    @Override
    public void Connect(OnConnectCallback callback){
        Log.i(TAG, "Connect BleLocalSocketReader");
        try {
            mServer = new LocalServerSocket(AndroidSettings.getStringValue(AndroidSettings.IniParams.ReaderSocketAdress));
        } catch (IOException e) {
            Log.e(TAG, "The localSocketServer creation failed");
            e.printStackTrace();
        }

        try {
            Log.d(TAG, "BleLocalSocketReader begins to accept()");
            mReceiver = mServer.accept();
        } catch (IOException e) {
            Log.e(TAG, "BleLocalSocketReader accept() failed");
            e.printStackTrace();
        }

        try {
            mInputStream = mReceiver.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "getInputStream() failed");
            e.printStackTrace();
        }

        Log.d(TAG, "The client connect to BleLocalSocketReader");
        ReadLoop readLoop = new ReadLoop();
        mLoopTread = new Thread(readLoop);
        mLoopTread.start();

        callback.Execute();
    }

    @Override
    public void Disconnect(){
        Log.i(TAG, "Disconnect BleLocalSocketReader");

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
                buffer = new byte[AndroidSettings.getIntValue(AndroidSettings.IniParams.BufferSize)];
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
