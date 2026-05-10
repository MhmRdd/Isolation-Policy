package io.github.mhmrdd.isolationpolicy;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Entry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) return;
        Log.i(Constants.TAG, "loaded into system_server");
        BindHook.install(lpparam.classLoader);
        new Handler(Looper.getMainLooper()).post(new RegisterTransport());
    }

    private static class RegisterTransport implements Runnable {
        @Override
        public void run() {
            try {
                Application app = AndroidAppHelper.currentApplication();
                if (app == null) {
                    Log.e(Constants.TAG, "currentApplication() null");
                    return;
                }
                Utils.registerExportedReceiver(app, new RequestReceiver(), new IntentFilter(Constants.ACTION_REQUEST_BINDER));
                Log.i(Constants.TAG, "transport ready pid=" + Process.myPid());
            } catch (Throwable t) {
                Log.e(Constants.TAG, "register", t);
            }
        }
    }

    private static class RequestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                PendingIntent pi = intent.getParcelableExtra(Constants.EXTRA_PENDING_INTENT);
                if (pi == null) return;
                if (!Constants.APPLICATION_ID.equals(pi.getCreatorPackage())) {
                    Log.w(Constants.TAG, "drop request from " + pi.getCreatorPackage());
                    return;
                }
                Intent reply = new Intent(Constants.ACTION_DELIVER_BINDER).setPackage(Constants.APPLICATION_ID);
                Bundle extras = new Bundle();
                extras.putBinder(Constants.EXTRA_BINDER, new PolicyService(pi.getCreatorUid()));
                extras.putParcelable(Constants.EXTRA_PENDING_INTENT,
                        PendingIntent.getBroadcast(ctx, 0, new Intent(Constants.ACTION_DELIVER_BINDER),
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
                reply.putExtras(extras);
                ctx.sendBroadcast(reply);
                Log.i(Constants.TAG, "delivered binder to " + pi.getCreatorPackage() + " uid=" + pi.getCreatorUid());
            } catch (Throwable t) {
                Log.e(Constants.TAG, "deliver", t);
            }
        }
    }
}
