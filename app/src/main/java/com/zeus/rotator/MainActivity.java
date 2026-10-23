package com.zeus.rotator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String[] ON = {"По умолчанию (не менять)", "Авто (любой, по сенсору)", "Портрет", "Ландшафт",
        "Обратный портрет (зеркальный)", "Обратный ландшафт (зеркальный)", "Сенсор: только ландшафт", "Сенсор: только портрет"};
    static final int[] OV = {-99, 10, 1, 0, 9, 8, 6, 7};
    static final String[] VN = {"Выкл", "Заполнить (обрезка)", "Растянуть", "Компромисс (%)"};

    SharedPreferences sp;
    PackageManager pm;
    List<ApplicationInfo> all = new ArrayList<>(), shown = new ArrayList<>();
    Map<String, String> names = new HashMap<>();
    BaseAdapter ad;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

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
        filter("");

        FrameLayout root = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(30), dp(10), dp(6));
        EditText s = new EditText(this);
        s.setHint("Поиск приложений");
        s.setHintTextColor(0xFF999999);
        s.setTextColor(Color.WHITE);
        s.setSingleLine(true);
        s.setBackgroundColor(0x88000000);
        s.setPadding(dp(14), dp(12), dp(14), dp(12));
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { filter(c.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        col.addView(s, new LinearLayout.LayoutParams(-1, -2));

        ListView lv = new ListView(this);
        lv.setDivider(new ColorDrawable(0x22FFFFFF));
        lv.setDividerHeight(1);
        ad = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View c, ViewGroup p) { return row(shown.get(i)); }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, i, id) -> {
            String k = shown.get(i).packageName;
            edit(k, names.get(k));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(8);
        col.addView(lv, lp);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    void filter(String q) {
        q = q.toLowerCase();
        shown.clear();
        for (ApplicationInfo a : all)
            if (q.isEmpty() || names.get(a.packageName).toLowerCase().contains(q) || a.packageName.contains(q)) shown.add(a);
        if (ad != null) ad.notifyDataSetChanged();
    }

    View row(ApplicationInfo a) {
        final String k = a.packageName;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(8), dp(8), dp(8));
        r.setBackgroundColor(0x66000000);
        final CheckBox cb = new CheckBox(this);
        cb.setChecked(sp.getBoolean("e_" + k, false));
        cb.setFocusable(false);
        cb.setOnClickListener(v -> sp.edit().putBoolean("e_" + k, cb.isChecked()).apply());
        r.addView(cb);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(a.loadIcon(pm));
        r.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(10), 0, 0, 0);
        TextView n = new TextView(this);
        n.setText(names.get(k)); n.setTextColor(Color.WHITE); n.setTextSize(16);
        TextView sub = new TextView(this);
        sub.setText(summary(k)); sub.setTextColor(0xFF9E9E9E); sub.setTextSize(12);
        t.addView(n); t.addView(sub);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    String summary(String k) {
        String o = ON[idx(sp.getInt("o_" + k, -99))];
        int v = sp.getInt("v_" + k, 0);
        String vv = v == 3 ? "Компромисс " + sp.getInt("p_" + k, 50) + "%" : VN[v];
        return "Поворот: " + o + "  |  Видео: " + vv;
    }

    int idx(int val) { for (int i = 0; i < OV.length; i++) if (OV[i] == val) return i; return 0; }

    Spinner spin(String[] items, int sel) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setSelection(sel);
        return s;
    }

    TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t); v.setPadding(0, dp(12), 0, 0);
        return v;
    }

    void edit(final String k, String name) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        final Spinner so = spin(ON, idx(sp.getInt("o_" + k, -99)));
        final Spinner sv = spin(VN, sp.getInt("v_" + k, 0));
        final TextView pl = label("Компромисс: " + sp.getInt("p_" + k, 50) + "%  (0 = как есть, 100 = заполнить)");
        final SeekBar sb = new SeekBar(this);
        sb.setMax(100); sb.setProgress(sp.getInt("p_" + k, 50));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { pl.setText("Компромисс: " + p + "%  (0 = как есть, 100 = заполнить)"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        final CheckBox lc = new CheckBox(this);
        lc.setText("Подгонять видео только в ландшафте");
        lc.setChecked(sp.getBoolean("l_" + k, true));
        l.addView(label("Ориентация экрана")); l.addView(so);
        l.addView(label("Подгонка видео")); l.addView(sv);
        l.addView(pl); l.addView(sb); l.addView(lc);
        new AlertDialog.Builder(this).setTitle(name).setView(l)
            .setPositiveButton("Сохранить", (d, w) -> {
                int o = so.getSelectedItemPosition(), v = sv.getSelectedItemPosition();
                sp.edit().putInt("o_" + k, OV[o]).putInt("v_" + k, v).putInt("p_" + k, sb.getProgress())
                    .putBoolean("l_" + k, lc.isChecked()).putBoolean("e_" + k, o > 0 || v > 0).apply();
                ad.notifyDataSetChanged();
            }).setNegativeButton("Отмена", null).show();
    }
}
