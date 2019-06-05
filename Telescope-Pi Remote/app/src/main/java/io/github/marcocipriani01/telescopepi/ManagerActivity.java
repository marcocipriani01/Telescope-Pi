package io.github.marcocipriani01.telescopepi;

import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

public class ManagerActivity extends AppCompatActivity implements BluetoothHelper.BluetoothListener {

    private AlertDialog.Builder errorDialog;
    private TelescopePiApp telescopePiApp = TelescopePiApp.getInstance();
    private TextView netInterfaceLabel;
    private TextView ipLabel;
    private Switch wifiSwitch;
    private Button netSelectionButton;
    private Switch hotspotSwitch;
    private Button connectHotspot;
    private Switch indiSwitch;
    private ImageButton shutdownButton;
    private ImageButton rebootButton;
    private String hotspotSSID;
    private String hotspotPswd;
    private String hotspotPswdType;
    private ProgressBar progressBar;

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

        if (telescopePiApp != null && telescopePiApp.bluetooth != null && telescopePiApp.bluetooth.isConnected()) {
            telescopePiApp.bluetooth.addListener(this);

        } else {
            throw new IllegalStateException("Not connected!");
        }

        errorDialog = new AlertDialog.Builder(this);
        errorDialog.setCancelable(false);
        errorDialog.setTitle(R.string.app_name);
        errorDialog.setIcon(R.drawable.error);

        netInterfaceLabel = findViewById(R.id.net_interface_label);
        ipLabel = findViewById(R.id.ip_address_label);
        wifiSwitch = findViewById(R.id.wifi_switch);
        netSelectionButton = findViewById(R.id.net_selection_button);
        hotspotSwitch = findViewById(R.id.hotspot_switch);
        connectHotspot = findViewById(R.id.hotspot_button);
        indiSwitch = findViewById(R.id.indi_web_switch);
        shutdownButton = findViewById(R.id.shutdown_button);
        rebootButton = findViewById(R.id.reboot_button);
        progressBar = findViewById(R.id.manager_progressbar);
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
        errorDialog.show();
    }

    public void shutdown(View v) {
        telescopePiApp.bluetooth.send("07");
    }

    public void reboot(View v) {
        telescopePiApp.bluetooth.send("08");
    }

    public void askIP(View v) {
        telescopePiApp.bluetooth.send("05");
    }

    @Override
    public void onMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (message.startsWith("Busy=")) {
                    Snackbar.make(findViewById(R.id.manager_coordinator), message.replace("Busy=", ""), Snackbar.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.VISIBLE);
                    return;
                }

                progressBar.setVisibility(View.GONE);
                if (message.startsWith("Error=")) {
                    errorDialog.setMessage(message.replace("Error=", ""));
                    errorDialog.show();

                } else if (message.equals("Shutting down...") || message.equals("Rebooting...")) {
                    telescopePiApp.bluetooth.disconnect();
                    errorDialog.setMessage(R.string.disconnected);
                    errorDialog.setPositiveButton(getApplicationContext().getText(R.string.dialog_OK),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            });
                    errorDialog.show();

                }else  if (message.startsWith("NetInterface=")) {
                    netInterfaceLabel.setText(message.replace("NetInterface=", ""));

                } else if (message.startsWith("WiFi=")) {
                    wifiSwitch.setChecked(Boolean.valueOf(message.replace("WiFi=", "").toLowerCase()));

                } else if (message.startsWith("IP=")) {
                    ipLabel.setText(message.replace("IP=", ""));

                } else if (message.startsWith("Hotspot=")) {
                    hotspotSwitch.setChecked(Boolean.valueOf(message.replace("Hotspot=", "").toLowerCase()));

                } else if (message.startsWith("HotspotSSID=")) {
                    hotspotSSID = message.replace("HotspotSSID=", "");

                } else if (message.startsWith("HotspotPswdType=")) {
                    hotspotPswdType = message.replace("HotspotPswdType=", "");

                } else if (message.startsWith("HotspotPswd=")) {
                    hotspotPswd = message.replace("HotspotPswd=", "");

                } else if (message.startsWith("INDI=")) {
                    indiSwitch.setChecked(Boolean.valueOf(message.replace("INDI=", "").toLowerCase()));

                } else if (message.startsWith("WiFiAPs=[")) {
                    String[] ma = message.replace("WiFiAPs=[", "").replace("]", "").split(",");
                    final ArrayAdapter<AccessPoint> apsList = new ArrayAdapter<>(ManagerActivity.this, android.R.layout.select_dialog_singlechoice);
                    for (String ap : ma) {
                        ap = ap.trim();
                        String signal = ap.split("[()]")[1];
                        apsList.add(new AccessPoint(ap.replace("(" + signal + ")", ""), Integer.valueOf(signal)));
                    }

                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(ManagerActivity.this);
                    alertBuilder.setIcon(R.mipmap.app_icon);
                    alertBuilder.setTitle(R.string.select_ap_dialog_title);

                    alertBuilder.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

                    alertBuilder.setAdapter(apsList, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            telescopePiApp.bluetooth.send("0" + which);
                        }
                    });
                    alertBuilder.show();
                }
            }
        });
    }

    @Override
    public void onError(String message) {
        Snackbar.make(findViewById(R.id.manager_coordinator), R.string.error, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionError(BluetoothDevice device, String message) {
        Snackbar.make(findViewById(R.id.manager_coordinator), R.string.error, Snackbar.LENGTH_SHORT).show();
        finish();
    }

    public static class AccessPoint {
        private String ssid;
        private int signal;

        public AccessPoint(String ssid, int signal) {
            this.ssid = ssid;
            this.signal = signal;
        }

        public String getSsid() {
            return ssid;
        }

        public void setSsid(String ssid) {
            this.ssid = ssid;
        }

        public int getSignal() {
            return signal;
        }

        public void setSignal(int signal) {
            this.signal = signal;
        }

        @NonNull
        @Override
        public String toString() {
            return ssid;
        }
    }
}