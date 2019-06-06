package io.github.marcocipriani01.telescopepi;

import android.app.Application;

public class TelescopePiApp extends Application {

    public static final String TAG = "Telescope-Pi Remote";
    public static final String INTENT_DEVICE = "BtDevice";
    private static TelescopePiApp instance;
    public BluetoothHelper bluetooth;

    public static TelescopePiApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public void initBluetooth() throws UnsupportedOperationException {
        bluetooth = new BluetoothHelper();
    }
}