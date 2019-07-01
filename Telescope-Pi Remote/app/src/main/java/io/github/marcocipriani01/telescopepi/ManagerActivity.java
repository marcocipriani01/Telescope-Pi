package io.github.marcocipriani01.telescopepi;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import static io.github.marcocipriani01.telescopepi.TelescopePiApp.INTENT_DEVICE;

/**
 * @author marcocipriani01
 * @version 1.1
 */
public class ManagerActivity extends AppCompatActivity implements BluetoothHelper.BluetoothListener {

    private TelescopePiApp telescopePiApp = TelescopePiApp.getInstance();
    private BluetoothHelper btHelper;
    private AccessPoint hotspotAP;
    private boolean isHotspotOn;
    private boolean isWifiOn;

    private AlertDialog.Builder errorDialog;
    private TextView netInterfaceLabel;
    private TextView ipLabel;
    private Switch wifiSwitch;
    private Switch hotspotSwitch;
    private Switch indiSwitch;
    private ProgressBar progressBar;
    private ImageButton shutdownButton;
    private ImageButton rebootButton;
    private ImageButton reloadIPButton;
    private Button connectHotspotButton;
    private Button connectWiFiButton;

    private CompoundButton.OnCheckedChangeListener wifiSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                btHelper.send("02");

            } else {
                btHelper.send("01");
            }
        }
    };
    private CompoundButton.OnCheckedChangeListener hotspotSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                btHelper.send("03");

            } else {
                btHelper.send("04");
            }
        }
    };
    private CompoundButton.OnCheckedChangeListener indiSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                btHelper.send("21");

            } else {
                btHelper.send("20");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);
        setSupportActionBar(this.<Toolbar>findViewById(R.id.manager_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        progressBar = findViewById(R.id.manager_progressbar);
        netInterfaceLabel = findViewById(R.id.net_interface_label);
        ipLabel = findViewById(R.id.ip_address_label);
        reloadIPButton = findViewById(R.id.reload_ip_button);
        wifiSwitch = findViewById(R.id.wifi_switch);
        hotspotSwitch = findViewById(R.id.hotspot_switch);
        indiSwitch = findViewById(R.id.indi_web_switch);
        connectWiFiButton = findViewById(R.id.wifi_connect_button);
        connectHotspotButton = findViewById(R.id.hotspot_button);
        shutdownButton = findViewById(R.id.shutdown_button);
        rebootButton = findViewById(R.id.reboot_button);

        errorDialog = new AlertDialog.Builder(this);
        errorDialog.setCancelable(false);
        errorDialog.setTitle(R.string.app_name);
        errorDialog.setIcon(R.drawable.error);

        String device = getIntent().getStringExtra(INTENT_DEVICE);
        if (telescopePiApp != null && telescopePiApp.bluetooth != null && device != null) {
            btHelper = telescopePiApp.bluetooth;
            btHelper.connectWithName(device);
            progressBar.setVisibility(View.VISIBLE);
            btHelper.addListener(this);

        } else {
            Log.wtf(TelescopePiApp.TAG, "Something is null in the ManagerActivity's onCreate(): app[" +
                    (telescopePiApp == null) + "], bt[" +
                    (telescopePiApp == null || telescopePiApp.bluetooth == null) + "], dev[" + (device == null));
            errorDialog.setMessage(R.string.connection_error);
            errorDialog.setPositiveButton(R.string.dialog_accept,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            errorDialog.show();
            return;
        }

        wifiSwitch.setOnCheckedChangeListener(wifiSwitchListener);
        hotspotSwitch.setOnCheckedChangeListener(hotspotSwitchListener);
        indiSwitch.setOnCheckedChangeListener(indiSwitchListener);

        reloadIPButton.setEnabled(false);
        wifiSwitch.setEnabled(false);
        connectWiFiButton.setEnabled(false);
        hotspotSwitch.setEnabled(false);
        connectHotspotButton.setEnabled(false);
        indiSwitch.setEnabled(false);
        shutdownButton.setEnabled(false);
        rebootButton.setEnabled(false);
    }

    public void askIP(View v) {
        progressBar.setVisibility(View.VISIBLE);
        btHelper.send("05");
    }

    public void connectWiFi(View v) {
        progressBar.setVisibility(View.VISIBLE);
        btHelper.send("06");
    }

    public void shutdown(View v) {
        AlertDialog.Builder alert = new AlertDialog.Builder(ManagerActivity.this);
        alert.setTitle(R.string.app_name);
        alert.setMessage(R.string.shutdown_confirm_message);
        alert.setIcon(R.mipmap.app_icon);
        alert.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                btHelper.send("07");
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public void reboot(View v) {
        AlertDialog.Builder alert = new AlertDialog.Builder(ManagerActivity.this);
        alert.setTitle(R.string.app_name);
        alert.setMessage(R.string.reboot_confirm_message);
        alert.setIcon(R.mipmap.app_icon);
        alert.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                btHelper.send("08");
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public void connectHotspot(View v) {
        if (hotspotAP == null) {
            onError();
            return;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(ManagerActivity.this);
        alert.setTitle(R.string.app_name);
        alert.setMessage(ManagerActivity.this.getString(R.string.connect_to_wifi_question) + hotspotAP.getSsid() + "\"?");
        /* ManagerActivity.this.getString(AccessPoint.Security.NONE.getNameResId()) +
                ((hotspotAP.getSecurity() == AccessPoint.Security.NONE) ? "." : (", " + hotspotAP.getPassword()))
        */
        alert.setIcon(R.mipmap.app_icon);
        alert.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String hotspotSSID = hotspotAP.getSsid();
                WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + hotspotSSID + "\"";

                String password = hotspotAP.getPassword();
                switch (hotspotAP.getSecurity()) {
                    case WPA: {
                        conf.preSharedKey = "\"" + password + "\"";
                        break;
                    }

                    case WEP: {
                        conf.wepKeys[0] = "\"" + password + "\"";
                        conf.wepTxKeyIndex = 0;
                        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                        break;
                    }

                    default: {
                        onError();
                        return;
                    }
                }

                WifiManager wifiManager = (WifiManager) telescopePiApp.getSystemService(Context.WIFI_SERVICE);
                wifiManager.setWifiEnabled(true);
                wifiManager.addNetwork(conf);
                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration i : list) {
                    if (i.SSID != null && i.SSID.equals("\"" + hotspotSSID + "\"")) {
                        wifiManager.disconnect();
                        wifiManager.enableNetwork(i.networkId, true);
                        wifiManager.reconnect();

                    } else {
                        wifiManager.disableNetwork(i.networkId);
                    }
                }
                dialog.dismiss();
            }
        });
        alert.show();
    }

    @Override
    protected void onDestroy() {
        if (btHelper != null) {
            btHelper.disconnect();
            progressBar.setVisibility(View.VISIBLE);
            reloadIPButton.setEnabled(false);
            wifiSwitch.setEnabled(false);
            connectWiFiButton.setEnabled(false);
            hotspotSwitch.setEnabled(false);
            connectHotspotButton.setEnabled(false);
            indiSwitch.setEnabled(false);
            shutdownButton.setEnabled(false);
            rebootButton.setEnabled(false);
            btHelper = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConnection(BluetoothDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                reloadIPButton.setEnabled(true);
                wifiSwitch.setEnabled(true);
                hotspotSwitch.setEnabled(true);
                indiSwitch.setEnabled(true);
                shutdownButton.setEnabled(true);
                rebootButton.setEnabled(true);
            }
        });
    }

    @Override
    public void onDisconnection(BluetoothDevice device) {
        btHelper = null;
        if (!ManagerActivity.this.isDestroyed()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    errorDialog.setMessage(R.string.disconnected);
                    errorDialog.setPositiveButton(R.string.dialog_accept,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            });
                    errorDialog.show();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

   /* @Override
    public void finish() {
        exitActivity();
        //finish();
    }*/

    /*@Override
    public void onBackPressed() {
        exitActivity();
    }*/

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
                if (message.startsWith("Error=") && !message.equals("Error=Invalid command!")) {
                    errorDialog.setMessage(message.replace("Error=", ""));
                    errorDialog.setPositiveButton(R.string.dialog_accept,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    errorDialog.show();

                } else if (message.equals("Shutting down...") || message.equals("Rebooting...")) {
                    finish();

                } else if (message.startsWith("NetInterface=")) {
                    netInterfaceLabel.setText(message.replace("NetInterface=", ""));

                } else if (message.startsWith("WiFi=")) {
                    isWifiOn = Boolean.valueOf(message.replace("WiFi=", "").toLowerCase());
                    connectWiFiButton.setEnabled(isWifiOn);
                    wifiSwitch.setOnCheckedChangeListener(null);
                    wifiSwitch.setChecked(isWifiOn);
                    wifiSwitch.setOnCheckedChangeListener(wifiSwitchListener);
                    hotspotSwitch.setEnabled(isWifiOn);
                    connectHotspotButton.setEnabled(isHotspotOn && isWifiOn);

                } else if (message.startsWith("IP=")) {
                    String ip = message.replace("IP=", "");
                    if (ip.equals("null")) {
                        ipLabel.setText(R.string.unknown);

                    } else {
                        ipLabel.setText(ip);
                    }

                } else if (message.startsWith("Hotspot=")) {
                    isHotspotOn = Boolean.valueOf(message.replace("Hotspot=", "").toLowerCase());
                    hotspotSwitch.setOnCheckedChangeListener(null);
                    hotspotSwitch.setChecked(isHotspotOn);
                    hotspotSwitch.setOnCheckedChangeListener(hotspotSwitchListener);
                    connectHotspotButton.setEnabled(isHotspotOn);
                    wifiSwitch.setEnabled(!isHotspotOn);
                    connectWiFiButton.setEnabled(isWifiOn && !isHotspotOn);

                } else if (message.startsWith("HotspotSSID=")) {
                    hotspotAP = new AccessPoint(message.replace("HotspotSSID=", ""));

                } else if (message.startsWith("HotspotPswdType=")) {
                    if (hotspotAP != null) {
                        hotspotAP.setSecurity(message.replace("HotspotPswdType=", ""));
                    }

                } else if (message.startsWith("HotspotPswd=")) {
                    if (hotspotAP != null && hotspotAP.getSecurity() != null) {
                        hotspotAP.setPassword(message.replace("HotspotPswd=", ""));
                    }

                } else if (message.startsWith("INDI=")) {
                    indiSwitch.setOnCheckedChangeListener(null);
                    indiSwitch.setChecked(Boolean.valueOf(message.replace("INDI=", "").toLowerCase()));
                    indiSwitch.setOnCheckedChangeListener(indiSwitchListener);

                } else if (message.equals("WiFiAPs=[]")) {
                    Snackbar.make(findViewById(R.id.manager_coordinator), R.string.no_wifi_aps, Snackbar.LENGTH_SHORT).show();

                } else if (message.startsWith("WiFiAPs=[")) {
                    try {
                        String[] ma = message.replace("WiFiAPs=[", "").replace("]", "").split(",");
                        final AccessPointAdapter apsList = new AccessPointAdapter(ManagerActivity.this);
                        for (String ap : ma) {
                            ap = ap.trim();
                            String signal = ap.split("[()]")[1];
                            apsList.add(new AccessPoint(ap.replace("(" + signal + ")", ""), Integer.valueOf(signal.replace("/70", ""))));
                        }

                        AlertDialog.Builder alert = new AlertDialog.Builder(ManagerActivity.this);
                        alert.setTitle(R.string.select_ap_dialog_title);
                        alert.setIcon(R.mipmap.app_icon);
                        alert.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        alert.setAdapter(apsList, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                btHelper.send("1" + which);
                                dialog.dismiss();
                            }
                        });
                        alert.show();

                    } catch (NumberFormatException e) {
                        Snackbar.make(findViewById(R.id.manager_coordinator), R.string.error, Snackbar.LENGTH_SHORT).show();
                    }

                } else if (message.startsWith("TypePswd=")) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(ManagerActivity.this);
                    alert.setTitle(R.string.app_name);
                    alert.setMessage(ManagerActivity.this.getString(R.string.type_pswd_msg) + message.replace("TypePswd=", "") + "\":");
                    alert.setIcon(R.mipmap.app_icon);
                    final TextView input = new TextView(ManagerActivity.this);
                    alert.setView(input);
                    alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            btHelper.send(input.getText().toString());
                            dialog.dismiss();
                        }
                    });
                    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            btHelper.send("#");
                            dialog.dismiss();
                        }
                    });
                    alert.show();
                }
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onError();
            }
        });
    }

    public void onError() {
        Snackbar.make(findViewById(R.id.manager_coordinator), R.string.error, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionError(BluetoothDevice device, Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                errorDialog.setMessage(R.string.connection_error);
                errorDialog.setPositiveButton(R.string.dialog_accept,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                errorDialog.show();
            }
        });
    }
}