package org.luxoft.sdl_core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static org.luxoft.sdl_core.BluetoothBleContract.PARAMS;
import static org.luxoft.sdl_core.BluetoothBleContract.PARAM_ACTION;
import static org.luxoft.sdl_core.BluetoothBleContract.PARAM_ADDRESS;
import static org.luxoft.sdl_core.BluetoothBleContract.PARAM_NAME;
import static org.luxoft.sdl_core.CommunicationService.MOBILE_CONTROL_DATA_EXTRA;
import static org.luxoft.sdl_core.CommunicationService.MOBILE_DATA_EXTRA;
import static org.luxoft.sdl_core.CommunicationService.ON_MOBILE_CONTROL_MESSAGE_RECEIVED;
import static org.luxoft.sdl_core.CommunicationService.ON_MOBILE_MESSAGE_RECEIVED;
import static org.luxoft.sdl_core.CommunicationService.ON_PERIPHERAL_READY;

public class ClassicBtHandler {
    private final Context context;
    private static ClassicBtHandler instance = null;
    private BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private AcceptThread mAcceptThread;
    private int mState;
    private final BluetoothLongReader mLongReader = new BluetoothLongReader();
    private final BluetoothLongWriter mLongWriter = new BluetoothLongWriter();

    // Name for the SDP record when creating server socket
    private static final String SERVICE_NAME = "SdlProxy";

    // Unique UUID for this application
    private static final UUID SDL_UUID =
            UUID.fromString("936DA01F-9ABD-4D9D-80C7-02AF85C822A8");

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public static final String TAG = ClassicBtHandler.class.getSimpleName();

    public static synchronized ClassicBtHandler getInstance(Context context) {
        if (instance == null) {
            instance = new ClassicBtHandler(context);
        }
        return instance;
    }

    private ClassicBtHandler(Context context) {
        this.context = context;
        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mReceiver, filter);
        mState = STATE_NONE;
        mLongReader.setMtu(1024); //tmp for now

        mLongReader.setCallback(new BluetoothLongReader.LongReaderCallback() {
            @Override
            public void OnLongMessageReceived(byte[] message) {
                final Intent intent = new Intent(ON_MOBILE_MESSAGE_RECEIVED);
                intent.putExtra(MOBILE_DATA_EXTRA, message);
                ClassicBtHandler.this.context.sendBroadcast(intent);
            }
        });
    }

    public void DoDiscovery() {
        Log.i(TAG, "If we're already discovering, stop it");
        //
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
        Log.i(TAG, "Request discover from BluetoothAdapter");
        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    public void disconnect() {
        Log.d(TAG, "Closing bluetooth handler...");
        context.unregisterReceiver(mReceiver);
    }

    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished
     */
    private synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();
                Log.i(TAG, "Classic BT device is found " + deviceName + " with address " + deviceHardwareAddress);

                connect(device);
            }
        }
    };


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(SDL_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");

            // Always cancel discovery because it will slow down a connection
            mBtAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close()" +
                            " socket during connection failure", e2);
                }
            }
                //connectionFailed();
                //return;*/
            //}

            // Reset the ConnectThread because we're done
            synchronized (ClassicBtHandler.this) {
                mConnectThread = null;
            }

            Log.d(TAG, "Start the connected thread!!!");
            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device) {
        Log.d(TAG, "connected to Socket");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);

        String ctrl_msg = GenerateConnectedMessage(device);
        if(ctrl_msg != null) {
            final Intent intent = new Intent(ON_MOBILE_CONTROL_MESSAGE_RECEIVED);
            intent.putExtra(MOBILE_CONTROL_DATA_EXTRA, ctrl_msg.getBytes());
            context.sendBroadcast(intent);
        }

        final Intent intent = new Intent(ON_PERIPHERAL_READY);
        context.sendBroadcast(intent);

        //mConnectedThread.start();

    }

    public void start_connected_thread(){
        mConnectedThread.start();
    }
    private String GenerateConnectedMessage(BluetoothDevice device) {
        try {
            JSONObject message = new JSONObject();
            message.put(PARAM_ACTION, "ON_DEVICE_CONNECTED");
            JSONObject params = new JSONObject();
            params.put(PARAM_NAME, device.getName());
            params.put(PARAM_ADDRESS, device.getAddress());
            message.put(PARAMS, params);
            return message.toString();
        } catch (JSONException ex) {
            Log.i(TAG, "ON_DEVICE_CONNECTED msg Failed", ex);
        }
        return null;
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    mLongReader.processReadOperation(buffer);
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    //connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                //mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                       // .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                tmp = mBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        SERVICE_NAME, SDL_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket listen() failed", e);
            }
            mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }

        public void run() {
            Log.d(TAG, "Socket BEGIN mAcceptThread");
            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (ClassicBtHandler.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread");

        }

        public void cancel() {
            Log.d(TAG, "Socket cancel");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close() of server failed", e);
            }
        }
    }

    public void writeMessage(byte[] message){
        mLongWriter.processWriteOperation(message);
    }

}