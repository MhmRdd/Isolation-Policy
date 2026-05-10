package io.github.mhmrdd.isolationpolicy;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BindHook {

    public static void install(ClassLoader cl) {
        try {
            Class<?> hostingRecord = XposedHelpers.findClass(
                    "com.android.server.am.HostingRecord", cl);
            int hooked = XposedBridge.hookAllMethods(hostingRecord, "usesAppZygote", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!Boolean.TRUE.equals(param.getResult())) return;
                    String defPkg = resolveDefiningPackage(param.thisObject);
                    if (defPkg == null) return;
                    if (PolicyStore.isDenied(defPkg)) {
                        param.setResult(Boolean.FALSE);
                    }
                }
            }).size();
            Log.i(Constants.TAG, "hook installed on HostingRecord.usesAppZygote (" + hooked + ")");
        } catch (Throwable t) {
            Log.e(Constants.TAG, "install hook", t);
        }
    }

    private static String resolveDefiningPackage(Object hr) {
        try {
            Object v = XposedHelpers.callMethod(hr, "getDefiningPackageName");
            if (v instanceof String) {
                String s = (String) v;
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object v = XposedHelpers.getObjectField(hr, "mDefiningPackageName");
            if (v instanceof String) {
                String s = (String) v;
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
