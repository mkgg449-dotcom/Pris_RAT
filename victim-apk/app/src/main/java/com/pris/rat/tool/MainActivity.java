package com.pris.rat.tool;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(50, 50, 50, 50);

        final TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setText("Testing connection...\n\n");
        ll.addView(tv);
        sv.addView(ll);
        setContentView(sv);

        new Thread(() -> {
            try {
                URL url = new URL("https://pris-rat.onrender.com/api/ping");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                final String result = "STATUS: " + code + "\n\nRESPONSE:\n" + sb.toString();
                runOnUiThread(() -> tv.setText(result));

            } catch (Exception e) {
                final String err = "ERROR:\n" + e.getMessage() + "\n\n" + e.getClass().getName();
                runOnUiThread(() -> tv.setText(err));
            }
        }).start();
    }
}