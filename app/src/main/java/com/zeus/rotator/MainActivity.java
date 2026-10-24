package com.zeus.rotator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int BEIGE = 0xFFEEE8AA, GREEN = 0xFF6FFF95;
    // у каждого раздела свой цвет меню и свой цвет переключателя
    static final int ORI_M = 0xFF4DD6FF, ORI_S = 0xFFB388FF;     // ориентация: голубой / фиолетовый
    static final int MIR_M = 0xFFFF7AC6, MIR_S = 0xFFFFB86B;     // зеркало: розовый / оранжевый
    static final int STA_M = 0xFFFFD166, STA_S = 0xFF5CFFB0;     // строка состояния: жёлтый / мятный
    static final int NAV_M = 0xFF7C9CFF, NAV_S = 0xFFFF6B6B;     // нижняя панель: васильковый / коралловый
    static final int HOME_M = 0xFF9BE564, HOME_S = 0xFF38BDF8;   // рабочий стол: лайм / небесный
    static final int LOCK_M = 0xFFC792FF, LOCK_S = 0xFFFF8FA3;   // экран блокировки: лавандовый / розовый
    static final String[] ON = {"По умолчанию (не менять)", "Авто (любой, по сенсору)", "Портрет", "Ландшафт",
        "Портрет вверх ногами (180°)", "Ландшафт вверх ногами (180°)", "Сенсор: только ландшафт", "Сенсор: только портрет"};
    static final String[] SN = {"по умолчанию", "Авто", "Портрет", "Ландшафт", "Портрет 180°", "Ландшафт 180°", "Сенсор ландшафт", "Сенсор портрет"};
    static final int[] OV = {-99, 10, 1, 0, 9, 8, 6, 7};
    static final String[] MN = {"Нет", "Горизонтально ↔", "Вертикально ↕", "Оба ↔ ↕"};
    static final String[] MS = {"", "Зеркало ↔", "Зеркало ↕", "Зеркало ↔↕"};
    static final String[] BN = {"Авто (не трогать)", "Скрыть", "Показать"};

    SharedPreferences sp;
    PackageManager pm;
    List<ApplicationInfo> all = new ArrayList<>(), shown = new ArrayList<>();
    Map<String, String> names = new HashMap<>();
    BaseAdapter ad;
    EditText search;
    ListView lv;
    FrameLayout root;
    String query = "", launchers = "";
    TextView homeT, homeS, lockT, lockS, info;
    Switch homeSw, lockSw;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    float d(float v) { return v * getResources().getDisplayMetrics().density; }
    int geti(String k, int def) { return sp.getInt(k, def); }
    boolean en(String k) { return sp.getBoolean("e_" + k, false); }
    int idx(int val) { for (int i = 0; i < OV.length; i++) if (OV[i] == val) return i; return 0; }

    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { sp = getSharedPreferences("settings", MODE_WORLD_READABLE); }
        catch (Throwable t) { sp = getSharedPreferences("settings", MODE_PRIVATE); }
        pm = getPackageManager();
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            if (a.packageName.equals(getPackageName())) continue;
            all.add(a);
            names.put(a.packageName, String.valueOf(a.loadLabel(pm)));
        }
        Collections.sort(all, (x, y) -> names.get(x.packageName).compareToIgnoreCase(names.get(y.packageName)));
        launchers = findLaunchers();
        filter("");

        root = new FrameLayout(this);
        root.setFocusableInTouchMode(true);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(36), dp(10), dp(6));
        final TextView logo = new TextView(this);
        logo.setText("RotatorOS"); logo.setTextSize(28); logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setPadding(dp(4), 0, 0, 0);
        logo.post(() -> {
            logo.getPaint().setShader(new LinearGradient(0, 0, Math.max(1, logo.getWidth()), 0,
                new int[]{ORI_M, ORI_S, MIR_M, MIR_S}, null, Shader.TileMode.CLAMP));
            logo.invalidate();
        });
        col.addView(logo, new LinearLayout.LayoutParams(-2, -2));
        TextView tag = new TextView(this);
        tag.setText("поворот  ·  зеркало  ·  чистый экран"); tag.setTextSize(12); tag.setTextColor(a(BEIGE, 0xAA));
        tag.setPadding(dp(5), 0, 0, dp(10));
        col.addView(tag, new LinearLayout.LayoutParams(-2, -2));
        search = new EditText(this);
        search.setHint("Поиск приложений");
        search.setHintTextColor(0x99EEE8AA);
        search.setTextColor(BEIGE);
        search.setSingleLine(true);
        search.setBackground(shape(0x99000000, a(BEIGE, 0x66), 1.5f, 14));
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b2, int d2) {}
            public void onTextChanged(CharSequence c, int a, int b2, int d2) { filter(c.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        col.addView(search, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout box = new FrameLayout(this);
        lv = new ListView(this);
        lv.setDivider(new ColorDrawable(0x00000000));
        lv.setDividerHeight(dp(6));
        lv.setVerticalScrollBarEnabled(false);
        lv.addHeaderView(buildHeader(), null, false);
        ad = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View c, ViewGroup p) { return row(shown.get(i)); }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, i, id) -> {
            int pos = i - lv.getHeaderViewsCount();
            if (pos < 0 || pos >= shown.size()) return;
            String k = shown.get(pos).packageName;
            edit(k, names.get(k));
        });
        box.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        box.addView(new FastBar(lv), new FrameLayout.LayoutParams(dp(130), -1, Gravity.END));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(8);
        col.addView(box, lp);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        // прозрачные системные панели: фон приложения виден под строкой состояния и нижней панелью, значки светлые
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController wc = w.getInsetsController();
            if (wc != null) wc.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            w.getDecorView().setSystemUiVisibility(0x100 | 0x200 | 0x400);   // LAYOUT_STABLE | HIDE_NAVIGATION | FULLSCREEN (раскладка под панелями)
        }
        final LinearLayout colF = col;
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            colF.setPadding(dp(10), ins.getSystemWindowInsetTop() + dp(8), dp(10), ins.getSystemWindowInsetBottom() + dp(6));
            return ins;
        });
        root.requestApplyInsets();
        refreshHeader();
    }

    // Назад: сначала сбрасывает поиск (возврат ко всем приложениям), и только потом выходит
    @Override
    public void onBackPressed() {
        if (!query.isEmpty()) {
            search.setText("");
            search.clearFocus();
            root.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            lv.setSelection(0);
            return;
        }
        super.onBackPressed();
    }

    // ---------- список ----------
    void filter(String q) {
        query = q;
        q = q.toLowerCase();
        shown.clear();
        for (ApplicationInfo a : all)
            if (q.isEmpty() || names.get(a.packageName).toLowerCase().contains(q) || a.packageName.contains(q)) shown.add(a);
        Collections.sort(shown, (x, y) -> Boolean.compare(en(y.packageName), en(x.packageName)));  // включённые наверх
        if (ad != null) ad.notifyDataSetChanged();
    }

    boolean active(String k) {
        return en(k) && (geti("o_" + k, -99) != -99 || geti("m_" + k, 0) != 0 || geti("sb_" + k, 0) != 0 || geti("nb_" + k, 0) != 0);
    }

    SpannableStringBuilder summary(String k) {
        boolean on = en(k);
        int o = idx(geti("o_" + k, -99)), m = geti("m_" + k, 0), sb = geti("sb_" + k, 0), nb = geti("nb_" + k, 0);
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (o > 0) chip(b, "Поворот: " + SN[o], ORI_M, on);
        if (m > 0) chip(b, MS[m], MIR_M, on);
        if (sb > 0) chip(b, "Статус: " + (sb == 1 ? "скрыт" : "показан"), STA_M, on);
        if (nb > 0) chip(b, "Панель: " + (nb == 1 ? "скрыта" : "показана"), NAV_M, on);
        if (b.length() == 0) chip(b, "Режим не задан", BEIGE, false);
        return b;
    }

    void chip(SpannableStringBuilder b, String t, int color, boolean on) {
        if (b.length() > 0) {
            int s0 = b.length();
            b.append("  ·  ");
            b.setSpan(new ForegroundColorSpan(a(BEIGE, 0x88)), s0, b.length(), 0);
        }
        int s1 = b.length();
        b.append(t);
        b.setSpan(new ForegroundColorSpan(on ? color : a(color, 0x99)), s1, b.length(), 0);
    }

    ColorStateList cbTint() {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{GREEN, BEIGE});
    }

    View row(ApplicationInfo a) {
        final String k = a.packageName;
        boolean act = active(k);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(8), dp(24), dp(8));
        r.setBackground(act ? shape(0x66081A10, a(GREEN, 0x99), 1.5f, 14) : shape(0x66000000, 0x22EEE8AA, 1f, 14));
        final CheckBox cb = new CheckBox(this);
        cb.setChecked(en(k));
        cb.setFocusable(false);
        cb.setButtonTintList(cbTint());
        cb.setOnClickListener(v -> {
            sp.edit().putBoolean("e_" + k, cb.isChecked()).apply();
            filter(query);
            if (cb.isChecked() && !active(k)) edit(k, names.get(k));   // режим ещё не выбран
            refreshHeader();
        });
        r.addView(cb);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(a.loadIcon(pm));
        r.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(10), 0, 0, 0);
        TextView n = new TextView(this);
        n.setText(names.get(k)); n.setTextColor(act ? GREEN : BEIGE); n.setTextSize(16);
        TextView sub = new TextView(this);
        sub.setText(summary(k)); sub.setTextSize(12);
        t.addView(n); t.addView(sub);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }


    GradientDrawable shape(int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(d(radiusDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, (int) d(strokeDp)), stroke);
        return g;
    }

    int a(int color, int alpha) { return (color & 0xFFFFFF) | (alpha << 24); }

    void tint(Switch sw, int c) {
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{c, 0xFF8A7F6A}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{a(c, 0x99), 0x44FFFFFF}));
    }

    // Раздел окна настроек: цветная карточка, заголовок, свой переключатель и меню выбора
    class Sec {
        final LinearLayout box;
        final Switch sw;
        final Spinner spn;

        Sec(String title, int menu, int swc, String[] items, boolean on, int sel) {
            box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(10), dp(14), dp(12));
            box.setBackground(shape(a(menu, 0x18), a(menu, 0xAA), 1.5f, 16));
            LinearLayout h = new LinearLayout(MainActivity.this);
            h.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(MainActivity.this);
            t.setText(title); t.setTextColor(menu); t.setTextSize(15); t.setTypeface(Typeface.DEFAULT_BOLD);
            h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            sw = new Switch(MainActivity.this);
            tint(sw, swc);
            sw.setChecked(on);
            h.addView(sw);
            box.addView(h);
            spn = spin(items, menu, sel);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = dp(6);
            box.addView(spn, sl);
            spn.setEnabled(on);
            spn.setAlpha(on ? 1f : 0.4f);
            sw.setOnCheckedChangeListener((b, c) -> { spn.setEnabled(c); spn.setAlpha(c ? 1f : 0.4f); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(10);
            box.setLayoutParams(lp);
        }

        boolean on() { return sw.isChecked(); }
        int pos() { return spn.getSelectedItemPosition(); }
    }

    Spinner spin(String[] items, final int color, int sel) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            public View getView(int p, View c, ViewGroup g) { TextView t = (TextView) super.getView(p, c, g); t.setTextColor(color); return t; }
            public View getDropDownView(int p, View c, ViewGroup g) { TextView t = (TextView) super.getDropDownView(p, c, g); t.setTextColor(color); return t; }
        });
        s.setSelection(sel);
        if (s.getBackground() != null) s.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        return s;
    }

    // ---------- шапка: рабочий стол, экран блокировки, резервная копия ----------
    String findLaunchers() {
        List<String> n = new ArrayList<>();
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : pm.queryIntentActivities(i, PackageManager.MATCH_ALL)) {
                String l = String.valueOf(r.loadLabel(pm));
                if (!n.contains(l)) n.add(l);
            }
        } catch (Throwable t) { }
        return n.isEmpty() ? "не найдены" : TextUtils.join(", ", n);
    }

    View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.addView(card("home", "Рабочий стол", HOME_M, HOME_S));
        h.addView(card("lock", "Экран блокировки", LOCK_M, LOCK_S));
        LinearLayout br = new LinearLayout(this);
        br.setGravity(Gravity.CENTER_VERTICAL);
        br.setPadding(dp(4), dp(8), dp(4), dp(8));
        br.addView(link("Экспорт", ORI_M, v -> exportCfg()));
        br.addView(link("Импорт", STA_S, v -> importCfg()));
        info = new TextView(this);
        info.setTextColor(0xAAEEE8AA); info.setTextSize(11);
        br.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        h.addView(br);
        return h;
    }

    TextView link(String t, int color, View.OnClickListener l) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextColor(color); v.setTextSize(14); v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(10), dp(6), dp(14), dp(6));
        v.setOnClickListener(l);
        return v;
    }

    View card(final String key, String title, int menu, int swc) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        r.setBackground(shape(a(menu, 0x14), a(menu, 0xAA), 1.5f, 16));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(this);
        tt.setText(title); tt.setTextSize(17); tt.setTypeface(Typeface.DEFAULT_BOLD); tt.setTextColor(menu);
        TextView ss = new TextView(this);
        ss.setTextSize(12);
        t.addView(tt); t.addView(ss);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final Switch sw = new Switch(this);
        tint(sw, swc);
        sw.setOnClickListener(v -> {
            sp.edit().putBoolean(key + "_on", sw.isChecked()).apply();
            if (sw.isChecked() && geti(key + "_o", -99) == -99) modeDialog(key);
            refreshHeader();
        });
        r.addView(sw);
        r.setOnClickListener(v -> modeDialog(key));
        if (key.equals("home")) { homeT = tt; homeS = ss; homeSw = sw; } else { lockT = tt; lockS = ss; lockSw = sw; }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        r.setLayoutParams(lp);
        return r;
    }

    void refreshHeader() {
        upd("home", homeT, homeS, homeSw, "Лаунчеры: " + launchers);
        upd("lock", lockT, lockS, lockSw, "экспериментально, зависит от прошивки");
        int gone = 0;
        for (Map.Entry<String, ?> e : sp.getAll().entrySet())
            if (e.getKey().startsWith("e_") && Boolean.TRUE.equals(e.getValue()) && !names.containsKey(e.getKey().substring(2))) gone++;
        info.setText(gone > 0 ? "Запомнено для удалённых приложений: " + gone : "Настройки запоминаются сами");
    }

    void upd(String key, TextView t, TextView s, Switch sw, String extra) {
        boolean on = sp.getBoolean(key + "_on", false);
        int o = idx(geti(key + "_o", -99));
        int menu = key.equals("home") ? HOME_M : LOCK_M;
        t.setTextColor(menu);
        SpannableStringBuilder b = new SpannableStringBuilder(o > 0 ? SN[o] : "режим не выбран");
        b.setSpan(new ForegroundColorSpan(on && o > 0 ? menu : a(menu, 0x99)), 0, b.length(), 0);
        int s0 = b.length();
        b.append("\n").append(extra);
        b.setSpan(new ForegroundColorSpan(a(BEIGE, 0xBB)), s0, b.length(), 0);
        s.setText(b);
        sw.setChecked(on);
    }

    void modeDialog(final String key) {
        boolean home = key.equals("home");
        int o = idx(geti(key + "_o", -99));
        final Sec s1 = new Sec("Ориентация", home ? HOME_M : LOCK_M, home ? HOME_S : LOCK_S,
            Arrays.copyOfRange(ON, 1, ON.length), o > 0, Math.max(0, o - 1));
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(4), dp(20), dp(4));
        l.addView(s1.box);
        show(new AlertDialog.Builder(this).setCustomTitle(title(home ? "Рабочий стол" : "Экран блокировки")).setView(l)
            .setPositiveButton("Сохранить", (dg, w) -> {
                int v = s1.on() ? OV[s1.pos() + 1] : -99;
                sp.edit().putInt(key + "_o", v).putBoolean(key + "_on", v != -99).apply();
                refreshHeader();
            }).setNegativeButton("Отмена", (dg, w) -> refreshHeader()));
    }

    // ---------- окно настроек приложения ----------
    void edit(final String k, String name) {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(4), dp(20), dp(8));
        sv.addView(l);
        int o = idx(geti("o_" + k, -99)), m = geti("m_" + k, 0), sb = geti("sb_" + k, 0), nb = geti("nb_" + k, 0);
        String[] bars = {"Скрыть", "Показать"};
        final Sec s1 = new Sec("Ориентация экрана", ORI_M, ORI_S, Arrays.copyOfRange(ON, 1, ON.length), o > 0, Math.max(0, o - 1));
        final Sec s2 = new Sec("Зеркальное отображение", MIR_M, MIR_S, new String[]{MN[1], MN[2], MN[3]}, m > 0, Math.max(0, m - 1));
        final Sec s3 = new Sec("Чистый экран · строка состояния", STA_M, STA_S, bars, sb > 0, Math.max(0, sb - 1));
        final Sec s4 = new Sec("Чистый экран · нижняя панель", NAV_M, NAV_S, bars, nb > 0, Math.max(0, nb - 1));
        l.addView(s1.box); l.addView(s2.box); l.addView(s3.box); l.addView(s4.box);
        TextView hint = new TextView(this);
        hint.setText("Зеркало и чистый экран работают внутри приложения: добавьте его в область действия модуля в LSPosed. Поворот этого не требует.");
        hint.setTextSize(12); hint.setTextColor(a(BEIGE, 0xAA)); hint.setPadding(dp(2), dp(12), 0, 0);
        l.addView(hint);
        show(new AlertDialog.Builder(this).setCustomTitle(title(name)).setView(sv)
            .setPositiveButton("Сохранить", (dg, w) -> {
                int oo = s1.on() ? OV[s1.pos() + 1] : -99, mm = s2.on() ? s2.pos() + 1 : 0;
                int aa = s3.on() ? s3.pos() + 1 : 0, cc = s4.on() ? s4.pos() + 1 : 0;
                sp.edit().putInt("o_" + k, oo).putInt("m_" + k, mm).putInt("sb_" + k, aa).putInt("nb_" + k, cc)
                    .putBoolean("e_" + k, oo != -99 || mm > 0 || aa > 0 || cc > 0).apply();
                filter(query);
                refreshHeader();
            }).setNegativeButton("Отмена", (dg, w) -> { filter(query); refreshHeader(); }));
    }

    void show(AlertDialog.Builder b) {
        AlertDialog dlg = b.create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(shape(0xF2121216, a(BEIGE, 0x44), 1.5f, 24));
        if (dlg.getButton(DialogInterface.BUTTON_POSITIVE) != null) dlg.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(GREEN);
        if (dlg.getButton(DialogInterface.BUTTON_NEGATIVE) != null) dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(BEIGE);
    }

    TextView title(String t) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextColor(BEIGE); v.setTextSize(19); v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(20), dp(16), dp(20), dp(4));
        return v;
    }

    TextView lab(String t) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextColor(BEIGE); v.setPadding(0, dp(12), 0, 0);
        return v;
    }

    // ---------- резервная копия ----------
    void exportCfg() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "RotatorOS-backup.json");
        startActivityForResult(i, 1);
    }

    void importCfg() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(i, 2);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (rc == 1) {
                JSONObject j = new JSONObject();
                for (Map.Entry<String, ?> e : sp.getAll().entrySet()) j.put(e.getKey(), e.getValue());
                try (OutputStream os = getContentResolver().openOutputStream(data.getData(), "w")) {
                    os.write(j.toString(1).getBytes("UTF-8"));
                }
                Toast.makeText(this, "Настройки сохранены в файл", Toast.LENGTH_SHORT).show();
            } else if (rc == 2) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream is = getContentResolver().openInputStream(data.getData())) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                }
                JSONObject j = new JSONObject(bo.toString("UTF-8"));
                SharedPreferences.Editor ed = sp.edit();
                Iterator<String> it = j.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    Object v = j.get(k);
                    if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                    else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                    else if (v instanceof Long) ed.putInt(k, ((Long) v).intValue());
                    else if (v instanceof String) ed.putString(k, (String) v);
                }
                ed.apply();
                filter(query);
                refreshHeader();
                Toast.makeText(this, "Настройки загружены", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    String letterAt(int i) {
        if (i < 0) return "↑";
        if (i >= shown.size()) return "";
        String k = shown.get(i).packageName;
        if (en(k)) return "★";
        String n = names.get(k);
        return n == null || n.isEmpty() ? "#" : n.substring(0, 1).toUpperCase();
    }

    // Быстрый ползунок справа: тонкая дорожка, зелёный «язычок», бежевый пузырь с буквой
    class FastBar extends View {
        final ListView list;
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        float frac;
        boolean drag;
        String letter = "";

        FastBar(ListView l) {
            super(MainActivity.this);
            list = l;
            l.setOnScrollListener(new AbsListView.OnScrollListener() {
                public void onScrollStateChanged(AbsListView v, int s) {}
                public void onScroll(AbsListView v, int first, int vis, int tot) {
                    if (!drag && tot > vis) { frac = first / (float) (tot - vis); invalidate(); }
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                if (e.getX() < getWidth() - d(44) || list.getCount() <= list.getChildCount()) return false;
                drag = true;
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (!drag) return false;
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                float pad = d(28);
                frac = Math.max(0f, Math.min(1f, (e.getY() - pad) / (getHeight() - 2 * pad)));
                int n = list.getCount(), i = Math.round(frac * (n - 1));
                list.setSelectionFromTop(i, 0);
                letter = letterAt(i - list.getHeaderViewsCount());
                invalidate();
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { drag = false; invalidate(); }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = list.getCount();
            if (n == 0 || n <= list.getChildCount()) return;
            float pad = d(28), x = getWidth() - d(12), top = pad, bot = getHeight() - pad;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(0x33FFFFFF);
            c.drawRoundRect(x - d(1.5f), top, x + d(1.5f), bot, d(2), d(2), pt);
            float th = d(drag ? 64 : 48), tw = d(drag ? 5 : 3);
            float ty = top + frac * (bot - top - th);
            pt.setShader(new LinearGradient(0, ty, 0, ty + th, new int[]{ORI_M, ORI_S, MIR_M}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(x - tw, ty, x + tw, ty + th, tw, tw, pt);
            pt.setShader(null);
            if (drag) {
                float bx = x - d(76), by = Math.max(0, Math.min(getHeight() - d(52), ty + th / 2 - d(26)));
                pt.setShader(new LinearGradient(bx, by, bx + d(52), by + d(52), new int[]{MIR_S, MIR_M}, null, Shader.TileMode.CLAMP));
                c.drawRoundRect(bx, by, bx + d(52), by + d(52), d(14), d(14), pt);
                pt.setShader(null);
                pt.setColor(0xFF1B1B1B);
                pt.setTextSize(d(26));
                pt.setTextAlign(Paint.Align.CENTER);
                c.drawText(letter, bx + d(26), by + d(36), pt);
            }
        }
    }
}
