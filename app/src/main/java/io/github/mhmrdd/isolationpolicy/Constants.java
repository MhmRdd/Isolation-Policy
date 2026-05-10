package io.github.mhmrdd.isolationpolicy;

import android.os.IBinder;

public class Constants {
    public static final String TAG = "IsolPolicy";
    public static final String APPLICATION_ID = "io.github.mhmrdd.isolationpolicy";

    public static final String ACTION_REQUEST_BINDER = APPLICATION_ID + ".REQUEST_BINDER";
    public static final String ACTION_DELIVER_BINDER = APPLICATION_ID + ".DELIVER_BINDER";

    public static final String EXTRA_PENDING_INTENT = "pi";
    public static final String EXTRA_BINDER = "b";

    public static final String DESCRIPTOR = APPLICATION_ID + ".policy.v1";

    public static final int TRX_PING = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TRX_GET_POLICY = IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int TRX_SET_POLICY = IBinder.FIRST_CALL_TRANSACTION + 2;
    public static final int TRX_GET_VERSION = IBinder.FIRST_CALL_TRANSACTION + 3;
}
