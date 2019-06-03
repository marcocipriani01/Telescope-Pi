package io.github.marcocipriani01.telescopepi.remote;

import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

import io.github.marcocipriani01.telescopepi.R;

public class ManagerActivity extends AppCompatActivity implements BluetoothHelper.BluetoothListener {

    private AlertDialog.Builder errorDialog;
    private ListView listDialog;
    private TelescopePiApp telescopePiApp = TelescopePiApp.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);
        setSupportActionBar(this.<Toolbar>findViewById(R.id.manager_toolbar));
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setDisplayHomeAsUpEnabled(true);
            toolbar.setDisplayShowHomeEnabled(true);
        }

        errorDialog = new AlertDialog.Builder(this);
        errorDialog.setCancelable(false);
        errorDialog.setTitle(R.string.app_name);
        errorDialog.setIcon(R.drawable.error);

        if (telescopePiApp != null && telescopePiApp.bluetooth != null && telescopePiApp.bluetooth.isConnected()) {
            telescopePiApp.bluetooth.addListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telescopePiApp != null && telescopePiApp.bluetooth != null) {
            telescopePiApp.bluetooth.removeListener(this);
        }
    }

    @Override
    public void onConnection(BluetoothDevice device) {

    }

    @Override
    public void onDisconnection(BluetoothDevice device) {
        errorDialog.setMessage(R.string.disconnected);
        errorDialog.setPositiveButton(getApplicationContext().getText(R.string.dialog_OK),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
    }

    @Override
    public void onMessage(String message) {

    }

    @Override
    public void onError(String message) {
        Snackbar.make(findViewById(R.id.main_coordinator), R.string.error, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionError(BluetoothDevice device, String message) {
        Snackbar.make(findViewById(R.id.main_coordinator), R.string.error, Snackbar.LENGTH_SHORT).show();
        finish();
    }
}