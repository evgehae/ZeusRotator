package com.zeus.rotator;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Hook implements IXposedHookLoadPackage {
    static XSharedPreferences prefs;
    static long last;
    static String PKG = "";
    static boolean appHooked;
    static final Set<String> once = Collections.synchronizedSet(new HashSet<String>());

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

    static void log(String s) { XposedBridge.log("RotatorOS: " + s); }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp.packageName.equals("com.zeus.rotator")) return;
        if (lp.packageName.equals("android")) { hookSystem(lp); return; }
        // в процессе приложения LSPosed вызывает нас и для gms/webview: имя «своего» пакета не затираем
        if (lp.isFirstApplication || PKG.isEmpty()) PKG = lp.packageName;
        if (!appHooked) { appHooked = true; hookApp(); }
    }

    // ================= system_server: поворот, рабочий стол, экран блокировки =================
    static Object fld(Object o, String n) { try { return XposedHelpers.getObjectField(o, n); } catch (Throwable t) { return null; } }

    static boolean callBool(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return (Boolean) m.invoke(o);
            } catch (NoSuchMethodException e) { /* ищем выше по иерархии */ }
            catch (Throwable t) { return false; }
        }
        return false;
    }

    static Object rec(Object o) {
        if (o == null) return null;
        if (o.getClass().getName().endsWith("ActivityRecord")) return o;
        return fld(o, "mActivityRecord");
    }

    // Какую ориентацию навязать этой активности (null — не трогаем)
    static Integer forcedFor(Object o) {
        try {
            Object ar = rec(o);
            if (ar == null) return null;
            Object s = fld(ar, "packageName");
            if (s instanceof String) {
                String pkg = (String) s;
                if (p().getBoolean("e_" + pkg, false)) {
                    int v = p().getInt("o_" + pkg, -99);
                    if (v != -99) return v;
                }
            }
            if (p().getBoolean("home_on", false)) {
                int v = p().getInt("home_o", -99);
                if (v != -99 && callBool(ar, "isActivityTypeHome")) return v;   // любой лаунчер
            }
        } catch (Throwable t) { }
        return null;
    }

    static void tryHook(String cn, ClassLoader cl, String mn, XC_MethodHook h, Object... params) {
        String tag = cn.substring(cn.lastIndexOf('.') + 1) + "." + mn;
        try {
            Object[] a = new Object[params.length + 1];
            System.arraycopy(params, 0, a, 0, params.length);
            a[params.length] = h;
            XposedHelpers.findAndHookMethod(cn, cl, mn, a);
            log("hook " + tag + " OK");
        } catch (Throwable t) { log("hook " + tag + " skip: " + t.getClass().getSimpleName()); }
    }

    static XC_MethodHook setWhenForced(final Object val) {
        return new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam m) {
                try { if (forcedFor(m.thisObject) != null) m.setResult(val); } catch (Throwable t) { }
            }
        };
    }

    void hookSystem(XC_LoadPackage.LoadPackageParam lp) {
        log("loaded, Android " + Build.VERSION.RELEASE + " sdk " + Build.VERSION.SDK_INT + " " + Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DISPLAY);
        ClassLoader cl = lp.classLoader;
        final String AR = "com.android.server.wm.ActivityRecord";

        // 1) сама ориентация
        XC_MethodHook ori = new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam m) {
                try {
                    Integer f = forcedFor(m.thisObject);
                    if (f == null) return;
                    m.setResult(f.intValue());
                    Object s = fld(rec(m.thisObject), "packageName");
                    if (once.add("o:" + s + ":" + f)) log("force " + s + " -> " + f);
                } catch (Throwable t) { }
            }
        };
        tryHook(AR, cl, "getOverrideOrientation", ori);
        tryHook(AR, cl, "getOrientation", ori, int.class);

        // 2) убираем чёрные поля / «режим совместимости», когда приложение портретное, а экран повернули
        XC_MethodHook no = setWhenForced(Boolean.FALSE), yes = setWhenForced(Boolean.TRUE), zero = setWhenForced(Float.valueOf(0f));
        tryHook(AR, cl, "shouldCreateCompatDisplayInsets", no);
        tryHook(AR, cl, "inSizeCompatMode", no);
        tryHook(AR, cl, "isLetterboxedForFixedOrientationAndAspectRatio", no);
        tryHook(AR, cl, "getMinAspectRatio", zero);
        tryHook(AR, cl, "getMaxAspectRatio", zero);
        tryHook(AR, cl, "isResizeable", yes);
        tryHook("com.android.server.wm.AppCompatSizeCompatModePolicy", cl, "shouldCreateAppCompatDisplayInsets", no);
        tryHook("com.android.server.wm.AppCompatSizeCompatModePolicy", cl, "isInSizeCompatMode", no);
        tryHook("com.android.server.wm.AppCompatAspectRatioPolicy", cl, "isLetterboxedForFixedOrientationAndAspectRatio", no);
        tryHook("com.android.server.wm.AppCompatAspectRatioPolicy", cl, "getMinAspectRatio", zero);
        tryHook("com.android.server.wm.AppCompatAspectRatioPolicy", cl, "getMaxAspectRatio", zero);

        // 3) экран блокировки (экспериментально): пока включён keyguard, навязываем выбранную ориентацию
        tryHook("com.android.server.wm.DisplayContent", cl, "getOrientation", new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam m) {
                try {
                    if (!p().getBoolean("lock_on", false)) return;
                    int v = p().getInt("lock_o", -99);
                    if (v == -99 || !callBool(m.thisObject, "isKeyguardLocked")) return;
                    m.setResult(v);
                    if (once.add("lock:" + v)) log("force keyguard -> " + v);
                } catch (Throwable t) { }
            }
        });
    }

    // ================= процесс приложения: зеркало и «чистый экран» =================
    void hookApp() {
        XC_MethodHook h = new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam m) {
                try {
                    if (m.args != null && m.args.length > 0 && Boolean.FALSE.equals(m.args[0])) return;   // потеря фокуса
                    final Activity a = (Activity) m.thisObject;
                    applyUi(a);
                    a.getWindow().getDecorView().postDelayed(new Runnable() { public void run() { applyUi(a); } }, 400);
                } catch (Throwable t) { }
            }
        };
        try { XposedHelpers.findAndHookMethod(Activity.class, "onResume", h); }
        catch (Throwable t) { log("onResume hook FAIL: " + t); }
        try { XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged", boolean.class, h); }
        catch (Throwable t) { log("onWindowFocusChanged hook FAIL: " + t); }
    }

    static void applyUi(Activity a) {
        try {
            XSharedPreferences sp = p();
            if (!sp.getBoolean("e_" + PKG, false)) return;
            int m = sp.getInt("m_" + PKG, 0);
            View c = a.findViewById(android.R.id.content);
            if (c != null && (m != 0 || c.getScaleX() < 0 || c.getScaleY() < 0)) {
                float tx = (m == 1 || m == 3) ? -1f : 1f, ty = (m == 2 || m == 3) ? -1f : 1f;
                if (c.getScaleX() != tx) c.setScaleX(tx);
                if (c.getScaleY() != ty) c.setScaleY(ty);
            }
            int sb = sp.getInt("sb_" + PKG, 0), nb = sp.getInt("nb_" + PKG, 0);
            if ((sb != 0 || nb != 0) && Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController ic = a.getWindow().getInsetsController();
                if (ic == null) return;
                int hide = 0, show = 0;
                if (sb == 1) hide |= WindowInsets.Type.statusBars(); else if (sb == 2) show |= WindowInsets.Type.statusBars();
                if (nb == 1) hide |= WindowInsets.Type.navigationBars(); else if (nb == 2) show |= WindowInsets.Type.navigationBars();
                if (hide != 0) {
                    ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    ic.hide(hide);
                }
                if (show != 0) ic.show(show);
            }
        } catch (Throwable t) { }
    }
}
