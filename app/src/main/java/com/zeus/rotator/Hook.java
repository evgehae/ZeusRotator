package com.zeus.rotator;

import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    static XSharedPreferences prefs;
    static long last;
    static String PKG = "";

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
        PKG = lp.packageName;
        if (PKG.equals("com.zeus.rotator")) return;
        if (PKG.equals("android")) hookSystem(lp); else hookVideo();
    }

    // ---------- Поворот: system_server ----------
    void hookSystem(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> ar = XposedHelpers.findClass("com.android.server.wm.ActivityRecord", lp.classLoader);
            XC_MethodHook h = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam m) {
                    try {
                        String pkg = (String) XposedHelpers.getObjectField(m.thisObject, "packageName");
                        if (pkg == null || !p().getBoolean("e_" + pkg, false)) return;
                        int o = p().getInt("o_" + pkg, -99);
                        if (o != -99) m.setResult(o);
                    } catch (Throwable t) { }
                }
            };
            try { XposedHelpers.findAndHookMethod(ar, "getOverrideOrientation", h); }
            catch (Throwable t) { XposedBridge.log("ZR: getOverrideOrientation: " + t); }
            try { XposedHelpers.findAndHookMethod(ar, "getOrientation", int.class, h); }
            catch (Throwable t) { XposedBridge.log("ZR: getOrientation: " + t); }
        } catch (Throwable t) { XposedBridge.log("ZR: system hook failed: " + t); }
    }

    // ---------- Подгонка видео: процесс приложения ----------
    void hookVideo() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "layout", int.class, int.class, int.class, int.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam m) {
                        final View v = (View) m.thisObject;
                        if (v instanceof TextureView || v instanceof SurfaceView) {
                            v.post(new Runnable() { public void run() { apply(v); } });
                        }
                    }
                });
        } catch (Throwable t) { XposedBridge.log("ZR: video hook failed: " + t); }
    }

    static void apply(View v) {
        try {
            v.setScaleX(1); v.setScaleY(1); v.setTranslationX(0); v.setTranslationY(0);
            int mode = p().getInt("v_" + PKG, 0);
            if (!p().getBoolean("e_" + PKG, false) || mode == 0) return;
            View r = v.getRootView();
            int W = r.getWidth(), H = r.getHeight(), w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0 || W <= 0) return;
            if (p().getBoolean("l_" + PKG, true) && W <= H) return;
            if ((long) w * h < 0.12 * W * H) return;           // миниатюры и превью игнорируем
            int[] l = new int[2];
            v.getLocationInWindow(l);
            float cx = l[0] + w / 2f, cy = l[1] + h / 2f;
            if (Math.abs(cy - H / 2f) > h / 2f) return;         // соседние страницы ленты не трогаем
            float fill = Math.max((float) W / w, (float) H / h), sx, sy;
            if (mode == 2) { sx = (float) W / w; sy = (float) H / h; }
            else {
                float s = mode == 1 ? fill : 1 + (fill - 1) * p().getInt("p_" + PKG, 50) / 100f;
                sx = s; sy = s;
            }
            v.setPivotX(w / 2f); v.setPivotY(h / 2f);
            v.setScaleX(sx); v.setScaleY(sy);
            v.setTranslationX(W / 2f - cx); v.setTranslationY(H / 2f - cy);
            ViewParent pr = v.getParent();
            while (pr instanceof ViewGroup) {
                ((ViewGroup) pr).setClipChildren(false);
                ((ViewGroup) pr).setClipToPadding(false);
                pr = pr.getParent();
            }
        } catch (Throwable t) { }
    }
}
