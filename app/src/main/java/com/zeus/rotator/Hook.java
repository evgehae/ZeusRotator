package com.zeus.rotator;

import android.os.Build;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Hook implements IXposedHookLoadPackage {
    static XSharedPreferences prefs;
    static long last;
    static final Set<String> forced = Collections.synchronizedSet(new HashSet<String>());

    static synchronized XSharedPreferences p() {
        long n = System.currentTimeMillis();
        if (prefs == null) {
            prefs = new XSharedPreferences("com.zeus.rotator", "settings");
            prefs.reload();
            last = n;
        } else if (n - last > 2000) {
            last = n;
            if (prefs.hasFileChanged()) prefs.reload();
        }
        return prefs;
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName)) return;
        XposedBridge.log("RotatorOS: loaded, Android " + Build.VERSION.RELEASE + " sdk " + Build.VERSION.SDK_INT + " " + Build.MANUFACTURER + " " + Build.MODEL);
        try {
            Class<?> ar = XposedHelpers.findClass("com.android.server.wm.ActivityRecord", lp.classLoader);
            XC_MethodHook h = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam m) {
                    try {
                        String pkg = (String) XposedHelpers.getObjectField(m.thisObject, "packageName");
                        if (pkg == null || !p().getBoolean("e_" + pkg, false)) return;
                        int o = p().getInt("o_" + pkg, -99);
                        if (o == -99) return;
                        m.setResult(o);
                        if (forced.add(pkg + ":" + o)) XposedBridge.log("RotatorOS: force " + pkg + " -> " + o);
                    } catch (Throwable t) { }
                }
            };
            try { XposedHelpers.findAndHookMethod(ar, "getOverrideOrientation", h); XposedBridge.log("RotatorOS: hook getOverrideOrientation OK"); }
            catch (Throwable t) { XposedBridge.log("RotatorOS: getOverrideOrientation FAIL: " + t); }
            try { XposedHelpers.findAndHookMethod(ar, "getOrientation", int.class, h); XposedBridge.log("RotatorOS: hook getOrientation OK"); }
            catch (Throwable t) { XposedBridge.log("RotatorOS: getOrientation FAIL: " + t); }
        } catch (Throwable t) { XposedBridge.log("RotatorOS: hook failed: " + t); }
    }
}
