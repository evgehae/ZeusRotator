package com.zeus.rotator;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class Hook implements IXposedHookLoadPackage {
    static XSharedPreferences prefs;
    static long last;
    static String PKG = "";
    static int logCount, applyLog, errLog, trackLog, pcLog;
    static boolean videoHooked;
    static Handler mainH;
    static final Map<View, ViewTreeObserver> reg = Collections.synchronizedMap(new WeakHashMap<View, ViewTreeObserver>());
    static final Map<View, String> sig = Collections.synchronizedMap(new WeakHashMap<View, String>());
    static final Map<View, VInfo> info = Collections.synchronizedMap(new WeakHashMap<View, VInfo>());
    static final Set<String> forced = Collections.synchronizedSet(new HashSet<String>());

    // Где внутри видеослоя реально находится картинка (плеер сам рисует чёрные поля внутри поверхности)
    static class VInfo {
        int w, h;
        long last;
        float[] box;
        final ArrayList<float[]> ring = new ArrayList<float[]>();
    }

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

    static void log(String s) { if (logCount++ < 800) XposedBridge.log("ZR[" + PKG + "] " + s); }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp.packageName.equals("com.zeus.rotator")) return;
        // В процессе приложения LSPosed вызывает нас ещё и для gms/webview: имя «своего» пакета не затираем
        if (lp.isFirstApplication || PKG.isEmpty()) PKG = lp.packageName;
        if (lp.packageName.equals("android")) hookSystem(lp);
        else if (!videoHooked) { videoHooked = true; hookVideo(); }
    }

    // ---------- Поворот: system_server ----------
    void hookSystem(XC_LoadPackage.LoadPackageParam lp) {
        XposedBridge.log("ZR[system] loaded, Android " + Build.VERSION.RELEASE + " sdk " + Build.VERSION.SDK_INT + " " + Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DISPLAY);
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
                        if (forced.add(pkg + ":" + o)) XposedBridge.log("ZR[system] force " + pkg + " -> " + o);
                    } catch (Throwable t) { }
                }
            };
            try { XposedHelpers.findAndHookMethod(ar, "getOverrideOrientation", h); XposedBridge.log("ZR[system] hook getOverrideOrientation OK"); }
            catch (Throwable t) { XposedBridge.log("ZR[system] getOverrideOrientation FAIL: " + t); }
            try { XposedHelpers.findAndHookMethod(ar, "getOrientation", int.class, h); XposedBridge.log("ZR[system] hook getOrientation OK"); }
            catch (Throwable t) { XposedBridge.log("ZR[system] getOrientation FAIL: " + t); }
        } catch (Throwable t) { XposedBridge.log("ZR[system] hook failed: " + t); }
    }

    // ---------- Подгонка видео: процесс приложения ----------
    void hookVideo() {
        log("loaded, Android " + Build.VERSION.RELEASE + " sdk " + Build.VERSION.SDK_INT + " " + Build.MANUFACTURER + " " + Build.MODEL);
        try {
            XposedHelpers.findAndHookMethod(View.class, "layout", int.class, int.class, int.class, int.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam m) {
                        View v = (View) m.thisObject;
                        if (v instanceof TextureView || v instanceof SurfaceView) track(v);
                    }
                });
        } catch (Throwable t) { log("video hook failed: " + t); }
        XC_MethodHook att = new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam m) { track((View) m.thisObject); }
        };
        try { XposedHelpers.findAndHookMethod(SurfaceView.class, "onAttachedToWindow", att); }
        catch (Throwable t) { log("hook SurfaceView.onAttachedToWindow FAIL: " + t); }
        try { XposedHelpers.findAndHookMethod(TextureView.class, "onAttachedToWindow", att); }
        catch (Throwable t) { log("hook TextureView.onAttachedToWindow FAIL: " + t); }
    }

    // Подгонка пересчитывается перед каждой отрисовкой кадра: это лечит «залипший» масштаб после поворота
    static void track(final View v) {
        try {
            ViewTreeObserver vto = v.getViewTreeObserver();
            if (!vto.isAlive() || reg.get(v) == vto) return;
            reg.put(v, vto);
            if (trackLog++ < 15) log("TRACK " + v.getClass().getName() + " " + v.getWidth() + "x" + v.getHeight() + " attached=" + v.isAttachedToWindow());
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() { if (v.isAttachedToWindow()) apply(v); return true; }
            });
        } catch (Throwable t) { }
    }

    // ---------- Поиск границ картинки внутри видеослоя ----------
    static void sample(final View v, final VInfo vi) {
        try {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return;
            final int bw = 160, bh = Math.max(16, Math.round(160f * h / w));
            if (v instanceof SurfaceView) {
                SurfaceView sv = (SurfaceView) v;
                if (!sv.getHolder().getSurface().isValid()) return;
                final Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                if (mainH == null) mainH = new Handler(Looper.getMainLooper());
                PixelCopy.request(sv, bm, new PixelCopy.OnPixelCopyFinishedListener() {
                    public void onPixelCopyFinished(int res) {
                        try {
                            if (res == PixelCopy.SUCCESS) analyze(v, vi, bm);
                            else if (pcLog++ < 5) log("PixelCopy result=" + res);
                        } catch (Throwable t) { }
                        bm.recycle();
                    }
                }, mainH);
            } else if (v instanceof TextureView) {
                Bitmap b2 = ((TextureView) v).getBitmap(bw, bh);
                if (b2 != null) { analyze(v, vi, b2); b2.recycle(); }
            }
        } catch (Throwable t) { if (errLog++ < 5) log("sample error: " + Log.getStackTraceString(t)); }
    }

    static void analyze(View v, VInfo vi, Bitmap bm) {
        int bw = bm.getWidth(), bh = bm.getHeight();
        int[] px = new int[bw * bh];
        bm.getPixels(px, 0, bw, 0, 0, bw, bh);
        int[] col = new int[bw], row = new int[bh];
        for (int y = 0; y < bh; y++)
            for (int x = 0; x < bw; x++) {
                int c = px[y * bw + x];
                int m = Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
                if (m > 28) { col[x]++; row[y]++; }
            }
        int minX = bw, maxX = -1, minY = bh, maxY = -1;
        int ct = Math.max(2, bh / 40), rt = Math.max(2, bw / 40);
        for (int x = 0; x < bw; x++) if (col[x] >= ct) { if (x < minX) minX = x; if (x > maxX) maxX = x; }
        for (int y = 0; y < bh; y++) if (row[y] >= rt) { if (y < minY) minY = y; if (y > maxY) maxY = y; }
        if (maxX < minX || maxY < minY) return;                   // пустой/тёмный кадр — пропускаем
        float kx = v.getWidth() / (float) bw, ky = v.getHeight() / (float) bh;
        synchronized (vi) {
            vi.ring.add(new float[]{minX * kx, minY * ky, (maxX + 1) * kx, (maxY + 1) * ky});
            if (vi.ring.size() > 6) vi.ring.remove(0);
            float[] u = null;
            for (float[] b : vi.ring) {
                if (u == null) u = b.clone();
                else { u[0] = Math.min(u[0], b[0]); u[1] = Math.min(u[1], b[1]); u[2] = Math.max(u[2], b[2]); u[3] = Math.max(u[3], b[3]); }
            }
            vi.box = u;
        }
    }

    static float vis(float s, float w, float h, float W, float H) {
        float a = w * s, b = h * s;
        return Math.min(a, W) / a * Math.min(b, H) / b;
    }

    // Максимальный масштаб, при котором обрезается не больше crop доли кадра
    static float smart(float w, float h, float W, float H, float crop) {
        float fill = Math.max(W / w, H / h), lo = 1, hi = fill;
        if (vis(fill, w, h, W, H) >= 1 - crop) return fill;
        for (int i = 0; i < 24; i++) {
            float m = (lo + hi) / 2;
            if (vis(m, w, h, W, H) >= 1 - crop) lo = m; else hi = m;
        }
        return lo;
    }

    static void apply(View v) {
        try {
            XSharedPreferences sp = p();
            boolean en = sp.getBoolean("e_" + PKG, false);
            int mode = sp.getInt("v_" + PKG, 0);
            View r = v.getRootView();
            int W = r.getWidth(), H = r.getHeight(), w = v.getWidth(), h = v.getHeight();
            float sx = 1, sy = 1, tx = 0, ty = 0;
            float[] bx = null;
            String why = "off";
            if (en && mode != 0 && w > 0 && h > 0 && W > 0) {
                if (sp.getBoolean("l_" + PKG, true) && W <= H) why = "portrait";
                else if ((long) w * h < 0.12 * W * H) why = "small";
                else {
                    // позиция вида в окне БЕЗ его собственного масштаба
                    float x = v.getLeft(), y = v.getTop();
                    View c = v;
                    while (c.getParent() instanceof View) {
                        View q = (View) c.getParent();
                        x = q.getLeft() + q.getTranslationX() + x - q.getScrollX();
                        y = q.getTop() + q.getTranslationY() + y - q.getScrollY();
                        c = q;
                    }
                    float cy = y + h / 2f;
                    if (Math.abs(cy - H / 2f) > h / 2f) why = "not-current";
                    else {
                        VInfo vi = info.get(v);
                        if (vi == null) { vi = new VInfo(); info.put(v, vi); }
                        if (vi.w != w || vi.h != h) { synchronized (vi) { vi.w = w; vi.h = h; vi.ring.clear(); vi.box = null; } }
                        long now = SystemClock.uptimeMillis();
                        if (now - vi.last > 400) { vi.last = now; sample(v, vi); }
                        bx = vi.box;
                        if (bx == null) why = "measuring";
                        else {
                            float vw = bx[2] - bx[0], vh = bx[3] - bx[1];
                            float vcx = (bx[0] + bx[2]) / 2f, vcy = (bx[1] + bx[3]) / 2f;
                            if (vw < w * 0.05f || vh < h * 0.05f) why = "box-small";
                            else {
                                float fill = Math.max(w / vw, h / vh);
                                int pc = sp.getInt("p_" + PKG, 50);
                                if (mode == 1) { sx = fill; sy = fill; }
                                else if (mode == 2) { sx = w / vw; sy = h / vh; }
                                else if (mode == 3) { sx = 1 + (fill - 1) * pc / 100f; sy = sx; }
                                else if (mode == 4) { sx = smart(vw, vh, w, h, pc / 100f); sy = sx; }
                                sx = Math.min(sx, 8f); sy = Math.min(sy, 8f);
                                tx = -sx * (vcx - w / 2f);   // центр картинки -> центр видеослоя
                                ty = -sy * (vcy - h / 2f);
                                why = "applied";
                            }
                        }
                    }
                }
            }
            String boxs = bx == null ? "-" : Math.round(bx[0]) + "," + Math.round(bx[1]) + "," + Math.round(bx[2]) + "," + Math.round(bx[3]);
            if (applyLog++ < 10) {
                String fi;
                try {
                    java.io.File f = (java.io.File) sp.getClass().getMethod("getFile").invoke(sp);
                    fi = f + " exists=" + f.exists() + " read=" + f.canRead() + " len=" + f.length();
                } catch (Throwable t) { fi = "err " + t; }
                log("APPLY#" + applyLog + " e=" + en + " mode=" + mode + " debug=" + sp.getBoolean("debug", false)
                    + " land=" + sp.getBoolean("l_" + PKG, true) + " root=" + W + "x" + H + " view=" + w + "x" + h
                    + " why=" + why + " box=" + boxs + " scale=" + sx + "," + sy + " keys=" + (sp.getAll() == null ? -1 : sp.getAll().size()) + " file=" + fi);
            }
            boolean diff = Math.abs(v.getScaleX() - sx) > 0.001f || Math.abs(v.getScaleY() - sy) > 0.001f
                || Math.abs(v.getTranslationX() - tx) > 0.5f || Math.abs(v.getTranslationY() - ty) > 0.5f;
            if (diff) {
                v.setPivotX(w / 2f); v.setPivotY(h / 2f);
                v.setScaleX(sx); v.setScaleY(sy);
                v.setTranslationX(tx); v.setTranslationY(ty);
                if (sx > 1.001f || sy > 1.001f) unclip(v);
            }
            if (en && sp.getBoolean("debug", false)) {
                String s = w + "x" + h + "|" + W + "x" + H + "|" + Math.round(sx * 100) + "," + Math.round(sy * 100) + "|" + why + "|" + boxs;
                if (!s.equals(sig.get(v))) { sig.put(v, s); dump(v, why, sx, sy, tx, ty, boxs, W, H); }
            }
        } catch (Throwable t) { if (errLog++ < 5) log("apply error: " + Log.getStackTraceString(t)); }
    }

    static void unclip(View v) {
        ViewParent pr = v.getParent();
        while (pr instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) pr;
            if (g.getClipChildren()) g.setClipChildren(false);
            if (g.getClipToPadding()) g.setClipToPadding(false);
            pr = pr.getParent();
        }
    }

    static String idn(View v) {
        try { return v.getId() == View.NO_ID ? "-" : v.getResources().getResourceEntryName(v.getId()); }
        catch (Throwable t) { return "0x" + Integer.toHexString(v.getId()); }
    }

    static void dump(View v, String why, float sx, float sy, float tx, float ty, String boxs, int W, int H) {
        try {
            StringBuilder b = new StringBuilder();
            android.util.DisplayMetrics dm = v.getResources().getDisplayMetrics();
            b.append("VIEW ").append(v.getClass().getName()).append(" id=").append(idn(v))
             .append(" size=").append(v.getWidth()).append('x').append(v.getHeight())
             .append(" root=").append(W).append('x').append(H)
             .append(" display=").append(dm.widthPixels).append('x').append(dm.heightPixels)
             .append(" orient=").append(v.getResources().getConfiguration().orientation)
             .append(" mode=").append(p().getInt("v_" + PKG, 0)).append(" pct=").append(p().getInt("p_" + PKG, 50))
             .append(" result=").append(why).append(" videoBox=").append(boxs)
             .append(" newScale=").append(sx).append(',').append(sy).append(" t=").append(tx).append(',').append(ty)
             .append(" curScale=").append(v.getScaleX()).append(',').append(v.getScaleY());
            if (v instanceof SurfaceView) {
                try { b.append(" surfFrame=").append(((SurfaceView) v).getHolder().getSurfaceFrame().toShortString()); } catch (Throwable t) { }
            }
            ViewParent pr = v.getParent();
            int d = 0;
            while (pr instanceof View && d < 6) {
                View q = (View) pr;
                b.append("\n   ^ ").append(q.getClass().getName()).append(" id=").append(idn(q)).append(' ')
                 .append(q.getWidth()).append('x').append(q.getHeight()).append(" @").append(q.getLeft()).append(',').append(q.getTop());
                pr = q.getParent(); d++;
            }
            log(b.toString());
        } catch (Throwable t) { }
    }
}
