package io.github.marcocipriani01.telescopepi;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * @author marcocipriani01
 * @version 1.1
 */
public class BluetoothHelper {

    public static final int INTENT_ENABLE_BT = 10;
    public static final int INTENT_DISCOVERABLE = 11;
    private static final String TAG = "BluetoothHelper";
    private static final UUID DEFAULT_UUID = UUID.fromString("b9029ed0-6d6a-4ff6-b318-215067a6d8b1");
    private boolean isConnected = false;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private ReceiveThread receiveThread;
    private ArrayList<BluetoothListener> listeners = new ArrayList<>();
    private BufferedReader input;
    private OutputStream output;

    public BluetoothHelper() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("No Bluetooth adapter found!");
        }
    }

    public static void requestDiscoverable(Activity activity) {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        activity.startActivityForResult(discoverableIntent, INTENT_DISCOVERABLE);
    }

    public static void requestBluetoothOn(Activity activity) {
        activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), INTENT_ENABLE_BT);
    }

    public void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
    }

    public void disableBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        }
    }

    public void connect(String address) {
        if (address == null) {
            throw new NullPointerException("Null Bluetooth address!");
        }
        try {
            new ConnectThread(bluetoothAdapter.getRemoteDevice(address)).start();

        } catch (IllegalStateException e) {
            Log.e(TAG, "Connection thread aborted.", e);
        }
    }

    public void connectWithName(String name) {
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getName().equals(name)) {
                connect(device);
                return;
            }
        }
        throw new IllegalArgumentException("Invalid name!");
    }

    public void connect(BluetoothDevice device) {
        if (device == null) {
            throw new NullPointerException("Null Bluetooth device!");
        }
        try {
            new ConnectThread(device).start();

        } catch (IllegalStateException e) {
            Log.e(TAG, "Connection thread aborted.", e);
        }
    }

    public void disconnect() {
        if (isConnected) {
            new DisconnectThread().start();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void send(String message) {
        try {
            output.write((message + "\n").getBytes());

        } catch (IOException e) {
            Log.e(TAG, "Error while sending data. Disconnecting...", e);
            for (BluetoothListener listener : listeners) {
                listener.onError(e);
            }
            disconnect();
        }
    }

    public void send(int number) {
        send(String.valueOf(number));
    }

    public boolean isBluetoothOn() {
        return bluetoothAdapter.isEnabled();
    }

    public BluetoothDevice[] getPairedDevices() {
        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        return devices.toArray(new BluetoothDevice[0]);
    }

    public BluetoothSocket getBluetoothSocket() {
        return bluetoothSocket;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public void addListener(BluetoothListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);

        } else {
            throw new IllegalArgumentException("Listener null or already in the list!");
        }
    }

    public void removeListener(BluetoothListener listener) {
        listeners.remove(listener);
    }

    public void removeAllListeners() {
        listeners.clear();
    }

    public interface BluetoothListener {

        void onConnection(BluetoothDevice device);

        void onDisconnection(BluetoothDevice device);

        void onMessage(String message);

        void onError(Throwable throwable);

        void onConnectionError(BluetoothDevice device, Throwable throwable);
    }

    private class ReceiveThread extends Thread implements Runnable {

        @Override
        public void run() {
            String message;
            try {
                while ((message = input.readLine()) != null && !isInterrupted()) {
                    for (BluetoothListener listener : listeners) {
                        listener.onMessage(message);
                    }
                }

            } catch (IOException e) {
                if (!isInterrupted()) {
                    Log.e(TAG, "Reading thread error. Disconnecting...", e);
                    for (BluetoothListener listener : listeners) {
                        listener.onError(e);
                    }
                    disconnect();
                }
            }
        }
    }

    private class ConnectThread extends Thread {

        ConnectThread(BluetoothDevice device) {
            BluetoothHelper.this.bluetoothDevice = device;
            try {
                BluetoothHelper.this.bluetoothSocket = device.createRfcommSocketToServiceRecord(DEFAULT_UUID);
                // BluetoothHelper.this.bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(DEFAULT_UUID);

            } catch (IOException e) {
                Log.e(TAG, "Connection error.", e);
                for (BluetoothListener listener : listeners) {
                    listener.onConnectionError(bluetoothDevice, e);
                }
                BluetoothHelper.this.bluetoothDevice = null;
                throw new IllegalStateException("Unable to connect!");
            }
        }

        @Override
        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                bluetoothSocket.connect();
                output = bluetoothSocket.getOutputStream();
                input = new BufferedReader(new InputStreamReader(bluetoothSocket.getInputStream()));
                receiveThread = new ReceiveThread();
                receiveThread.start();
                isConnected = true;
                for (BluetoothListener listener : listeners) {
                    listener.onConnection(bluetoothDevice);
                }

            } catch (IOException e) {
                Log.e(TAG, "Connection error.", e);
                for (BluetoothListener listener : listeners) {
                    listener.onConnectionError(bluetoothDevice, e);
                }
                try {
                    if (receiveThread != null) {
                        receiveThread.interrupt();
                        receiveThread = null;
                    }
                    bluetoothSocket.close();
                    bluetoothSocket = null;
                    bluetoothDevice = null;
                    input = null;
                    output = null;
                    isConnected = false;

                } catch (IOException e1) {
                    Log.e(TAG, "Disconnection error.", e);
                    for (BluetoothListener listener : listeners) {
                        listener.onError(e);
                    }
                }
            }
        }
    }

    private class DisconnectThread extends Thread {

        DisconnectThread() {

        }

        @Override
        public void run() {
            try {
                if (receiveThread != null) {
                    receiveThread.interrupt();
                    receiveThread = null;
                }
                bluetoothSocket.close();
                bluetoothSocket = null;
                input = null;
                output = null;
                isConnected = false;
                for (BluetoothListener listener : listeners) {
                    listener.onDisconnection(bluetoothDevice);
                }
                bluetoothDevice = null;
                listeners.clear();

            } catch (IOException e) {
                Log.e(TAG, "Disconnection error.", e);
                for (BluetoothListener listener : listeners) {
                    listener.onError(e);
                }
            }
        }
    }
}