package io.github.marcocipriani01.telescopepi;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * @author marcocipriani01
 * @version 1.0
 */
public class AccessPointAdapter extends ArrayAdapter<AccessPoint> {

    public AccessPointAdapter(Context context) {
        super(context, R.layout.ap_list_item);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.ap_list_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.ssidTextView = convertView.findViewById(R.id.wifi_ssid);
            viewHolder.signalImageView = convertView.findViewById(R.id.wifi_signal_img);
            convertView.setTag(viewHolder);

        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        AccessPoint ap = getItem(position);
        if (ap != null) {
            viewHolder.ssidTextView.setText(ap.getSsid());
            int signal = ap.getSignal();
            if (signal <= 14) {
                viewHolder.signalImageView.setImageResource(R.drawable.wifi_0_bar);

            } else if (signal <= 28) {
                viewHolder.signalImageView.setImageResource(R.drawable.wifi_1_bar);

            } else if (signal <= 42) {
                viewHolder.signalImageView.setImageResource(R.drawable.wifi_2_bar);

            } else if (signal <= 56) {
                viewHolder.signalImageView.setImageResource(R.drawable.wifi_3_bar);

            } else {
                viewHolder.signalImageView.setImageResource(R.drawable.wifi_4_bar);
            }
        }
        return convertView;
    }

    static class ViewHolder {
        TextView ssidTextView;
        ImageView signalImageView;
    }
}