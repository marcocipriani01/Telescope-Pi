package io.github.marcocipriani01.telescopepi.remote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"WeakerAccess", "unused"})
public class BluetoothHelper {

    public static final int INTENT_ENABLE_BT = 10;
    private static final UUID DEFAULT_UUID = UUID.fromString("b9029ed0-6d6a-4ff6-b318-215067a6d8b1");
    private boolean isConnected = false;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private ArrayList<BluetoothListener> listeners = new ArrayList<>();
    private BufferedReader input;
    private OutputStream output;

    public BluetoothHelper() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("No Bluetooth adapter found!");
        }
    }

    public static void requestDiscoverable(Context context) {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        context.startActivity(discoverableIntent);
    }

    public static void requestBluetoothOn(Activity activity) {
        activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), INTENT_ENABLE_BT);
    }

    public ArrayList<BluetoothListener> getListeners() {
        return listeners;
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

    public void connectToAddress(String address) {
        if (address == null) {
            throw new NullPointerException("Null Bluetooth address");
        }
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        new ConnectThread(device).start();
    }

    public void connectToName(String name) {
        for (BluetoothDevice blueDevice : bluetoothAdapter.getBondedDevices()) {
            if (blueDevice.getName().equals(name)) {
                connectToAddress(blueDevice.getAddress());
                return;
            }
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        new ConnectThread(device).start();
    }

    public void disconnect() {
        try {
            input.close();
            output.close();
            bluetoothSocket.close();
            bluetoothDevice = null;
            bluetoothSocket = null;
            input = null;
            output = null;
            isConnected = false;
            for (BluetoothListener listener : listeners) {
                listener.onDisconnection(bluetoothDevice);
            }

        } catch (IOException e) {
            for (BluetoothListener listener : listeners) {
                listener.onError(e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void send(String message) {
        try {
            output.write((message + "\n").getBytes());

        } catch (IOException e) {
            for (BluetoothListener listener : listeners) {
                listener.onError(e.getMessage());
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
        if (listener != null) {
            listeners.add(listener);
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

        void onError(String message);

        void onConnectionError(BluetoothDevice device, String message);
    }

    private class ReceiveThread extends Thread implements Runnable {

        @Override
        public void run() {
            String message;
            try {
                while ((message = input.readLine()) != null) {
                    for (BluetoothListener listener : listeners) {
                        listener.onMessage(message);
                    }
                }

            } catch (IOException e) {
                for (BluetoothListener listener : listeners) {
                    listener.onError(e.getMessage());
                }
                disconnect();
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
                for (BluetoothListener listener : listeners) {
                    listener.onConnectionError(bluetoothDevice, e.getMessage());
                }
            }
        }

        @Override
        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                bluetoothSocket.connect();
                output = bluetoothSocket.getOutputStream();
                input = new BufferedReader(new InputStreamReader(bluetoothSocket.getInputStream()));
                new ReceiveThread().start();
                isConnected = true;
                for (BluetoothListener listener : listeners) {
                    listener.onConnection(bluetoothDevice);
                }

            } catch (IOException e) {
                for (BluetoothListener listener : listeners) {
                    listener.onConnectionError(bluetoothDevice, e.getMessage());
                }
                try {
                    bluetoothSocket.close();

                } catch (IOException closeException) {
                    for (BluetoothListener listener : listeners) {
                        listener.onError(e.getMessage());
                    }
                }
            }
        }
    }
}