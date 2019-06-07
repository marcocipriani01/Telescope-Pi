package io.github.marcocipriani01.telescopepi;

import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import static io.github.marcocipriani01.telescopepi.TelescopePiApp.INTENT_DEVICE;

public class MainActivity extends AppCompatActivity {

    private static boolean btEnabledOnCreate = false;
    private AlertDialog.Builder errorDialog;
    private ListView btDevicesListView;
    private TelescopePiApp telescopePiApp = TelescopePiApp.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setSupportActionBar(this.<Toolbar>findViewById(R.id.main_toolbar));

        FloatingActionButton fab = findViewById(R.id.add_dev_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
        });

        btDevicesListView = findViewById(R.id.bt_devices_list);

        errorDialog = new AlertDialog.Builder(this);
        errorDialog.setCancelable(false);
        errorDialog.setTitle(R.string.app_name);
        errorDialog.setIcon(R.drawable.error);

        // Bluetooth
        try {
            telescopePiApp.initBluetooth();
            if (telescopePiApp.bluetooth.isBluetoothOn()) {
                showList();

            } else {
                BluetoothHelper.requestBluetoothOn(this);
            }

        } catch (UnsupportedOperationException e) {
            errorDialog.setMessage(getApplicationContext().getString(R.string.error_unsupported));
            errorDialog.setPositiveButton(R.string.dialog_accept,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finishAffinity();
                        }
                    });
            errorDialog.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothHelper.INTENT_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Snackbar.make(findViewById(R.id.main_coordinator), R.string.bluetooth_enabled,
                        Snackbar.LENGTH_SHORT).show();
                btEnabledOnCreate = true;
                showList();

            } else {
                errorDialog.setMessage(R.string.bluetooth_must_be_enabled);
                errorDialog.setPositiveButton(R.string.dialog_accept,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishAffinity();
                            }
                        });
                errorDialog.show();
            }
        }
    }

    private void showList() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item);
        final BluetoothDevice[] pairedDevices = telescopePiApp.bluetooth.getPairedDevices();
        if (pairedDevices.length > 0) {
            for (BluetoothDevice bluetoothDevice : pairedDevices) {
                adapter.add(bluetoothDevice.getName());
            }
            btDevicesListView.setAdapter(adapter);
            btDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(MainActivity.this, ManagerActivity.class);
                    intent.putExtra(INTENT_DEVICE, pairedDevices[position].getName());
                    startActivity(intent);
                }
            });

        } else {
            Snackbar.make(findViewById(R.id.main_coordinator), R.string.error_no_devices_found, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telescopePiApp != null && telescopePiApp.bluetooth != null) {
            if (btEnabledOnCreate) {
                telescopePiApp.bluetooth.disableBluetooth();
            }
        }
    }
}