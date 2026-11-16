package com.disha;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the phone restarts (or the app is updated), start waiting for the bike again, unless the
 * user last pressed Disconnect or never chose a bike.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        BikeService.initLog(c);
        BikeService.log("Auto-connect after " + (Intent.ACTION_BOOT_COMPLETED.equals(a) ? "phone restart" : "app update"));
        BikeService.autoStart(c, "restart");
    }
}
