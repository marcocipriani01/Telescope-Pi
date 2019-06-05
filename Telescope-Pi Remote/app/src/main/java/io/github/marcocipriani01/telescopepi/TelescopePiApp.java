package io.github.marcocipriani01.telescopepi;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

public class TelescopePiApp extends Application implements BluetoothHelper.BluetoothListener {

    public static final String TAG = "Telescope-Pi Remote";
    private static TelescopePiApp instance;
    public BluetoothHelper bluetooth;

    public static TelescopePiApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "TelescopePiApp onCreate");
        instance = this;
    }

    public void initBluetooth() {
        bluetooth = new BluetoothHelper();
        bluetooth.addListener(this);
        Log.e(TAG, "Bluetooth initialized");
    }

    public void connect(BluetoothDevice device) {
        Log.e(TAG, "Connection started");
        bluetooth.connectToDevice(device);
    }

    @Override
    public void onConnection(BluetoothDevice device) {
        Log.e(TAG, "Connected to: " + device.getName() + " - " + device.getAddress());
    }

    @Override
    public void onDisconnection(BluetoothDevice device) {
        Log.e(TAG, "Disconnected from: " + device.getName() + " - " + device.getAddress());
    }

    @Override
    public void onMessage(String message) {
        Log.e(TAG, "BT message: " + message);
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "BT error: " + message);
    }

    @Override
    public void onConnectionError(BluetoothDevice device, String message) {
        Log.e(TAG, "BT connection error (" + device.getName() + " - " + device.getAddress() + "): " + message);
    }
}