package io.github.mhmrdd.isolationpolicy;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PolicyService extends Binder {
    private final int mOwnerUid;

    public PolicyService(int ownerUid) {
        this.mOwnerUid = ownerUid;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (Binder.getCallingUid() != mOwnerUid) {
            return super.onTransact(code, data, reply, flags);
        }
        if (code == Constants.TRX_PING) {
            data.enforceInterface(Constants.DESCRIPTOR);
            if (reply != null) {
                reply.writeNoException();
                reply.writeInt(1);
            }
            return true;
        }
        if (code == Constants.TRX_GET_VERSION) {
            data.enforceInterface(Constants.DESCRIPTOR);
            if (reply != null) {
                reply.writeNoException();
                reply.writeLong(PolicyStore.version());
            }
            return true;
        }
        if (code == Constants.TRX_GET_POLICY) {
            data.enforceInterface(Constants.DESCRIPTOR);
            if (reply != null) {
                reply.writeNoException();
                reply.writeStringList(new java.util.ArrayList<>(PolicyStore.snapshot()));
            }
            return true;
        }
        if (code == Constants.TRX_SET_POLICY) {
            data.enforceInterface(Constants.DESCRIPTOR);
            List<String> list = data.createStringArrayList();
            Set<String> next = new HashSet<>();
            if (list != null) {
                for (String s : list) if (s != null && !s.isEmpty()) next.add(s);
            }
            PolicyStore.replace(next);
            if (reply != null) {
                reply.writeNoException();
                reply.writeInt(next.size());
            }
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}
