package io.github.marcocipriani01.telescopepi;

import androidx.annotation.NonNull;

/**
 * @author marcocipriani01
 * @version 1.0
 */
public class AccessPoint {

    private String ssid;
    private int signal;
    private Security security;
    private String password;

    public AccessPoint(String ssid) {
        this.ssid = ssid;
    }

    public AccessPoint(String ssid, int signal) {
        this.ssid = ssid;
        this.signal = signal;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = Security.fromString(security);
        if (this.security == null) {
            password = null;
        }
    }

    public void setSecurity(Security security) {
        this.security = security;
        if (this.security == null) {
            password = null;
        }
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        if (ssid == null) {
            throw new NullPointerException("Null SSID!");

        } else {
            this.ssid = ssid;
        }
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (password == null || password.equals("")) {
            this.password = null;
            this.security = Security.NONE;

        } else {
            this.password = password;
        }
    }

    public enum Security {
        WPA(R.string.wpa), WEP(R.string.wep), NONE(R.string.open_network);

        private int nameResId;

        Security(int nameResId) {
            this.nameResId = nameResId;
        }

        public static Security fromString(String s) {
            if (s == null) {
                return NONE;
            }

            switch (s.toLowerCase()) {
                case "": {
                    return NONE;
                }

                case "wpa": {
                    return WPA;
                }

                case "wep": {
                    return WEP;
                }

                default: {
                    return null;
                }
            }
        }

        public int getNameResId() {
            return nameResId;
        }
    }
}
