package io.github.mhmrdd.isolationpolicy;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class PolicyClient {

    public interface OnReady {
        void onReady(boolean injected);
    }

    private final Context mContext;
    private final AtomicReference<IBinder> mBinder = new AtomicReference<IBinder>();
    private DeliverReceiver mReceiver;
    private OnReady mReadyCallback;

    public PolicyClient(Context context) {
        this.mContext = context;
    }

    public void start(OnReady cb) {
        this.mReadyCallback = cb;
        mReceiver = new DeliverReceiver();
        Utils.registerExportedReceiver(mContext, mReceiver, new IntentFilter(Constants.ACTION_DELIVER_BINDER));

        Intent req = new Intent(Constants.ACTION_REQUEST_BINDER).setPackage("android");
        Bundle extras = new Bundle();
        extras.putParcelable(Constants.EXTRA_PENDING_INTENT,
                PendingIntent.getBroadcast(mContext, 0, new Intent(Constants.ACTION_REQUEST_BINDER),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
        req.putExtras(extras);
        mContext.sendBroadcast(req);
    }

    public void stop() {
        if (mReceiver != null) {
            try { mContext.unregisterReceiver(mReceiver); } catch (Throwable ignored) {}
            mReceiver = null;
        }
        mBinder.set(null);
    }

    public boolean isReady() {
        return mBinder.get() != null;
    }

    public boolean ping() {
        IBinder b = mBinder.get();
        if (b == null) return false;
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(Constants.DESCRIPTOR);
            b.transact(Constants.TRX_PING, d, r, 0);
            r.readException();
            return r.readInt() == 1;
        } catch (RemoteException e) {
            return false;
        } finally {
            d.recycle(); r.recycle();
        }
    }

    public List<String> getPolicy() {
        IBinder b = mBinder.get();
        if (b == null) return new ArrayList<String>();
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(Constants.DESCRIPTOR);
            b.transact(Constants.TRX_GET_POLICY, d, r, 0);
            r.readException();
            List<String> out = r.createStringArrayList();
            return out != null ? out : new ArrayList<String>();
        } catch (RemoteException e) {
            return new ArrayList<String>();
        } finally {
            d.recycle(); r.recycle();
        }
    }

    public int setPolicy(Set<String> packages) {
        IBinder b = mBinder.get();
        if (b == null) return -1;
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(Constants.DESCRIPTOR);
            d.writeStringList(new ArrayList<String>(packages));
            b.transact(Constants.TRX_SET_POLICY, d, r, 0);
            r.readException();
            return r.readInt();
        } catch (RemoteException e) {
            return -1;
        } finally {
            d.recycle(); r.recycle();
        }
    }

    public long getVersion() {
        IBinder b = mBinder.get();
        if (b == null) return -1L;
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(Constants.DESCRIPTOR);
            b.transact(Constants.TRX_GET_VERSION, d, r, 0);
            r.readException();
            return r.readLong();
        } catch (RemoteException e) {
            return -1L;
        } finally {
            d.recycle(); r.recycle();
        }
    }

    private class DeliverReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                PendingIntent pi = intent.getParcelableExtra(Constants.EXTRA_PENDING_INTENT);
                if (pi == null) return;
                if (!Constants.APPLICATION_ID.equals(pi.getCreatorPackage())) return;
                Bundle extras = intent.getExtras();
                IBinder b = extras != null ? extras.getBinder(Constants.EXTRA_BINDER) : null;
                if (b == null) return;
                mBinder.set(b);
                if (mReadyCallback != null) mReadyCallback.onReady(true);
            } catch (Throwable t) {
                Log.e(Constants.TAG, "client recv", t);
            }
        }
    }
}
