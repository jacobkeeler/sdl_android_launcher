package org.luxoft.sdl_core;

import static org.luxoft.sdl_core.CommunicationService.ACTION_CONNECT_ADAPTERS;
import static org.luxoft.sdl_core.CommunicationService.ACTION_START_BLE;
import static org.luxoft.sdl_core.CommunicationService.ACTION_START_BT;
import static org.luxoft.sdl_core.CommunicationService.ACTION_STOP_TRANSPORT;
import static org.luxoft.sdl_core.CommunicationService.ON_DEVICE_SCAN_STARTED;
import static org.luxoft.sdl_core.CommunicationService.SDL_STOPPED_BY_USER_EXTRA;
import static org.luxoft.sdl_core.SdlLauncherService.ON_SDL_SERVICE_STARTED;
import static org.luxoft.sdl_core.SdlLauncherService.ON_SDL_SERVICE_STOPPED;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import static org.luxoft.sdl_core.SdlLauncherService.ACTION_SDL_SERVICE_START;
import static org.luxoft.sdl_core.SdlLauncherService.ACTION_SDL_SERVICE_STOP;

public class MainActivity extends AppCompatActivity {

    static final String TAG = MainActivity.class.getSimpleName();

    private Button start_sdl_button;
    private Button stop_sdl_button;
    private Button start_bt_button;
    private Button start_ble_button;
    public static String sdl_cache_folder_path;
    public static String sdl_external_dir_folder_path;
    private static final int ACCESS_LOCATION_REQUEST = 1;
    private static final int ACCESS_EXT_STORAGE_REQUEST = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AndroidSettings.loadSettings(getFilesDir().toString());

        start_sdl_button = findViewById(R.id.start_sdl_button);
        stop_sdl_button = findViewById(R.id.stop_sdl_button);
        start_bt_button = findViewById(R.id.start_bt_button);
        start_ble_button = findViewById(R.id.start_ble_button);

        start_sdl_button.setEnabled(true);
        stop_sdl_button.setEnabled(false);
        start_bt_button.setEnabled(false);
        start_ble_button.setEnabled(false);
        start_sdl_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start_sdl_button.setEnabled(false);
                stop_sdl_button.setEnabled(true);
                if(isBluetoothSupported() && isBluetoothPermissionGranted()){
                    start_bt_button.setEnabled(true);
                }

                if (isBleSupported() && isBluetoothPermissionGranted()) {
                    start_ble_button.setEnabled(true);
                }

                Intent start_intent = new Intent(MainActivity.this, SdlLauncherService.class);
                start_intent.setAction(ACTION_SDL_SERVICE_START);
                AndroidTool.startService(MainActivity.this, start_intent);
                final Intent connect_intent = new Intent(ACTION_CONNECT_ADAPTERS);
                sendBroadcast(connect_intent);
            }
        });

        stop_sdl_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start_bt_button.setEnabled(false);
                start_bt_button.setText("Start BT");
                start_ble_button.setEnabled(false);
                start_ble_button.setText("Start BLE");
                final Intent disconnect_intent = new Intent(ACTION_STOP_TRANSPORT);
                disconnect_intent.putExtra(SDL_STOPPED_BY_USER_EXTRA, true);
                sendBroadcast(disconnect_intent);
                Intent stop_intent = new Intent(MainActivity.this, SdlLauncherService.class);
                stop_intent.setAction(ACTION_SDL_SERVICE_STOP);
                AndroidTool.startService(MainActivity.this, stop_intent);
            }
        });

        start_bt_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!isBluetoothEnabled()){
                    return;
                }

                String buttonText = start_bt_button.getText().toString();
                if(buttonText.equals("Start BT")) {
                    start_ble_button.setEnabled(false);
                    start_bt_button.setText("Stop BT");
                    final Intent intent = new Intent(ACTION_START_BT);
                    sendBroadcast(intent);
                }else if(buttonText.equals("Stop BT")){
                    start_ble_button.setEnabled(true);
                    start_bt_button.setText("Start BT");
                    final Intent intent = new Intent(ACTION_STOP_TRANSPORT);
                    sendBroadcast(intent);
                }
            }
        });

        start_ble_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!isBluetoothEnabled()){
                    return;
                }

                String buttonText = start_ble_button.getText().toString();
                if(buttonText.equals("Start BLE")) {
                    start_bt_button.setEnabled(false);
                    start_ble_button.setText("Stop BLE");
                    final Intent intent = new Intent(ACTION_START_BLE);
                    sendBroadcast(intent);
                }else if(buttonText.equals("Stop BLE")){
                    start_bt_button.setEnabled(true);
                    start_ble_button.setText("Start BLE");
                    final Intent intent = new Intent(ACTION_STOP_TRANSPORT);
                    sendBroadcast(intent);
                }
            }
        });

        sdl_cache_folder_path = getDataDir().toString();
        TextView cache_folder_view = findViewById(R.id.cache_folder);
        cache_folder_view.setText(String.format("Cache folder: %s", sdl_cache_folder_path));

        if (isStorageMounted()) {
            if (isStoragePermissionGranted()) {
                updateExternalDirField(getExternalDirectory());
                runInitializeAssetsThread();
            } else {
                updateExternalDirField(getDefaultExternalDirectory()); // fallback path
                askForStoragePermissions();
            }
        }

        if (savedInstanceState == null) {
            initBT();
        }

        registerReceiver(mainActivityReceiver, makeMainActivityIntentFilter());
    }

    @Override
    protected void onDestroy (){
        if ((isBluetoothSupported() || isBleSupported()) && isBluetoothPermissionGranted()) {
            stopService(new Intent(MainActivity.this, CommunicationService.class));
        }
        super.onDestroy();
        unregisterReceiver(mainActivityReceiver);
    }

    private void updateExternalDirField(final String path) {
        sdl_external_dir_folder_path = path;
        TextView external_folder_view = findViewById(R.id.external_folder);
        external_folder_view.setText(String.format("External folder: %s", sdl_external_dir_folder_path));
    }

    private void runInitializeAssetsThread() {
        final ProgressDialog dialog = new ProgressDialog(MainActivity.this);
        dialog.setMessage("Initializing assets..");
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.show();

        Thread thread = new Thread() {
            @Override
            public void run() {
                initializeAssets();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                    }
                });
            }
        };

        thread.start();
    }

    private ArrayList<String> getAvailableAbi(AssetManager manager) throws IOException {
        ArrayList<String> output = new ArrayList<>();
        String[] available_archs = manager.list("");
        for (String available : available_archs) {
            String[] available_content = manager.list(available);
            for (String content : available_content) {
                if (content.contains(".so")) {
                    output.add(available);
                    break;
                }
            }
        }

        return output;
    }

    private String getPreferableAbi(ArrayList<String> supported, ArrayList<String> available) {
        for (String supported_abi : supported ) {
            if (available.contains(supported_abi)) {
                Log.d(TAG, "Supported ABI " + supported_abi + " assets are available. Use this ABI for initialization");
                return supported_abi;
            }

            Log.d(TAG, "ABI " + supported_abi + " is not available. Check the next supported");
        }

        // In case if no ABI supported
        if (!available.isEmpty()) {
            String first_available = available.get(0);
            Log.w(TAG, "No supported ABI found. Use first available " + first_available);
            return first_available;
        }

        Log.e(TAG, "No any available ABI found. Exiting");
        return null;
    }

    private void extractAssets(AssetManager manager, String target_folder, String target_abi) throws IOException {
        String[] assets = manager.list(target_abi);
        for (String asset : assets) {
            Log.d(TAG, "Found asset: " + asset);

            File target_asset_file = new File(target_folder + File.separator + asset);
            if (target_asset_file.exists()) {
                Log.d(TAG, "Asset already initialized in " + target_asset_file);
                continue;
            }

            String assetName = target_abi + File.separator + asset;
            Log.d(TAG, "Initializing asset: " + assetName);

            if (manager.list(assetName).length > 0) {
                if (target_asset_file.mkdir()) {
                    extractAssets(manager, target_asset_file.getAbsolutePath(), assetName);
                } else {
                    Log.e(TAG, "Failed to make folder: " + target_asset_file.getAbsolutePath());
                }
                continue;
            }

            InputStream in = manager.open(assetName);
            DataOutputStream outw = new DataOutputStream(new FileOutputStream(
                    target_asset_file.getAbsolutePath()));

            final int max_buffer_size = 80000;
            byte[] buf = new byte[max_buffer_size];
            int len;
            while ((len = in.read(buf, 0, max_buffer_size)) > 0) {
                outw.write(buf, 0, len);
            }

            in.close();
            outw.close();
        }
    }

    private void initializeAssets() {
        Log.d(TAG, "Initializing assets");

        try {
            AssetManager assetManager = getAssets();
            ArrayList<String> available_abi = getAvailableAbi(assetManager);
            ArrayList<String> supported_abi = new ArrayList<String>(Arrays.asList(Build.SUPPORTED_ABIS));
            String target_abi = getPreferableAbi(supported_abi, available_abi);
            if (target_abi == null) {
                return;
            }

            String target_folder = getFilesDir().toString();
            extractAssets(assetManager, target_folder, target_abi);
        } catch (IOException e) {
            Log.e(TAG, "Exception during assets initialization: " + e.toString());
        }
    }

    private String getWritableExternalDirectory() {
        // Create a temporary file to see whether a volume is really writeable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        String directoryName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString().concat("/SDL");

        File directory = new File(directoryName);
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()) {
                return null;
            }
        }

        return directoryName;
    }

    private static boolean isStorageMounted() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private String getDefaultExternalDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString().concat("/SDL");
    }

    private String getExternalDirectory() {
        String path = getWritableExternalDirectory();

        if (path == null) {
            path = getDefaultExternalDirectory();
        }

        return path;
    }

    private void askForStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), ACCESS_EXT_STORAGE_REQUEST);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, ACCESS_EXT_STORAGE_REQUEST);
        }

        //permission is automatically granted on sdk < 23 upon installation, no need to ask
    }

    private void askForBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, ACCESS_LOCATION_REQUEST);
        }

        //permission is automatically granted on sdk < 23 upon installation, no need to ask
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Log.v(TAG,"Storage permission is granted");
                return true;
            } else {
                Log.v(TAG,"Storage permission is revoked");
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Log.v(TAG,"Storage permission is granted");
                return true;
            } else {
                Log.v(TAG,"Storage permission is revoked");
                return false;
            }
        } else { //permission is automatically granted on sdk < 23 upon installation
            Log.v(TAG,"Storage Permission is granted");
            return true;
        }
    }

    private boolean isBluetoothPermissionGranted() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Bluetooth permission is revoked");
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Bluetooth permission is revoked");
                return false;
            }
        }

        Log.v(TAG,"Bluetooth permission is granted");
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        if (resultCode == RESULT_OK) {
            Uri treeUri = resultData.getData();
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
            grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (pickedDir != null) {
                  // TODO: Add support for a file operations for Android 10 on external storage
            }
        }

        runInitializeAssetsThread();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case ACCESS_LOCATION_REQUEST: {
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "Bluetooth permissions are granted");
                        startCommunicationService();
                    }
                }
                break;
            }
            case ACCESS_EXT_STORAGE_REQUEST: {
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "External storage permissions are granted");
                        updateExternalDirField(getWritableExternalDirectory());
                    }
                }

                runInitializeAssetsThread();
                break;
            }

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    private boolean isBleSupported(){
        return BluetoothAdapter.getDefaultAdapter() != null &&
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private boolean isBluetoothSupported(){
        return BluetoothAdapter.getDefaultAdapter() != null;
    }

    private boolean isBluetoothEnabled(){
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()){
            showToastMessage("Bluetooth is OFF, enable BT on your device");
            return false;
        }
        return true;
    }

    private void initBT() {
        if (!isBluetoothSupported()) {
            showToastMessage("Bluetooth is NOT supported");
            return;
        }

        if (!isBleSupported()){
            showToastMessage("BLE is NOT supported");
        }

        if (!isBluetoothPermissionGranted()) {
            askForBluetoothPermissions();
            return;
        }

        startCommunicationService();
    }

    private void startCommunicationService() {
        Log.d(TAG, "Starting communication service...");
        startService(new Intent(MainActivity.this, CommunicationService.class));
    }

    private void showToastMessage(String message) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    private final BroadcastReceiver mainActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                return;
            }

            switch (action) {
                case ON_SDL_SERVICE_STOPPED:
                    start_sdl_button.setEnabled(true);
                    stop_sdl_button.setEnabled(false);
                    showToastMessage("SDL service has been stopped");
                    break;

                case ON_SDL_SERVICE_STARTED:
                    start_sdl_button.setEnabled(false);
                    stop_sdl_button.setEnabled(true);
                    showToastMessage("SDL service has been started");
                    break;

                case ON_DEVICE_SCAN_STARTED:
                    showToastMessage("Scanning for devices nearby...");
                    break;
            }
        }
    };

    private static IntentFilter makeMainActivityIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ON_SDL_SERVICE_STOPPED);
        intentFilter.addAction(ON_SDL_SERVICE_STARTED);
        intentFilter.addAction(ON_DEVICE_SCAN_STARTED);
        return intentFilter;
    }

}
