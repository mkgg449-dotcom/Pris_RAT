package com.pris.rat.tool;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AccessService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }
}