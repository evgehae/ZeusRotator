package com.zeus.rotator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    static final String[] ON = {"По умолчанию (не менять)", "Авто (любой, по сенсору)", "Портрет", "Ландшафт",
        "Обратный портрет (зеркальный)", "Обратный ландшафт (зеркальный)", "Сенсор: только ландшафт", "Сенсор: только портрет"};
    static final String[] SN = {"по умолчанию", "Авто", "Портрет", "Ландшафт", "Обр. портрет", "Обр. ландшафт", "Сенсор ландшафт", "Сенсор портрет"};
    static final int[] OV = {-99, 10, 1, 0, 9, 8, 6, 7};
    static final int ORANGE = 0xFFFFDA9A, GREEN = 0xFF6FFF95, DIM = 0xFF8A8A8A;

    SharedPreferences sp;
    PackageManager pm;
    List<ApplicationInfo> all = new ArrayList<>(), shown = new ArrayList<>();
    Map<String, String> names = new HashMap<>();
    BaseAdapter ad;
    EditText search;
    ListView lv;
    FrameLayout root;
    String query = "";

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    float d(float v) { return v * getResources().getDisplayMetrics().density; }

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

        root = new FrameLayout(this);
        root.setFocusableInTouchMode(true);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(30), dp(10), dp(6));
        search = new EditText(this);
        search.setHint("Поиск приложений");
        search.setHintTextColor(0xFF999999);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackgroundColor(0x88000000);
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b2, int d2) {}
            public void onTextChanged(CharSequence c, int a, int b2, int d2) { filter(c.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        col.addView(search, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout box = new FrameLayout(this);
        lv = new ListView(this);
        lv.setDivider(new ColorDrawable(0x22FFFFFF));
        lv.setDividerHeight(1);
        lv.setVerticalScrollBarEnabled(false);
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
        box.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        box.addView(new FastBar(lv), new FrameLayout.LayoutParams(dp(130), -1, Gravity.END));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(8);
        col.addView(box, lp);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
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

    boolean en(String k) { return sp.getBoolean("e_" + k, false); }

    void filter(String q) {
        query = q;
        q = q.toLowerCase();
        shown.clear();
        for (ApplicationInfo a : all)
            if (q.isEmpty() || names.get(a.packageName).toLowerCase().contains(q) || a.packageName.contains(q)) shown.add(a);
        // включённые наверх, внутри групп порядок по алфавиту (сортировка стабильная)
        Collections.sort(shown, (x, y) -> Boolean.compare(en(y.packageName), en(x.packageName)));
        if (ad != null) ad.notifyDataSetChanged();
    }

    View row(ApplicationInfo a) {
        final String k = a.packageName;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(8), dp(24), dp(8));
        r.setBackgroundColor(0x66000000);
        final CheckBox cb = new CheckBox(this);
        cb.setChecked(en(k));
        cb.setFocusable(false);
        cb.setOnClickListener(v -> {
            sp.edit().putBoolean("e_" + k, cb.isChecked()).apply();
            filter(query);
            if (cb.isChecked() && sp.getInt("o_" + k, -99) == -99) edit(k, names.get(k));  // режим ещё не выбран
        });
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
        sub.setText(summary(k)); sub.setTextSize(12);
        t.addView(n); t.addView(sub);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    // Выбранный поворот — кремовым, неактивное — тусклым серым
    SpannableStringBuilder summary(String k) {
        int oi = idx(sp.getInt("o_" + k, -99));
        SpannableStringBuilder b = new SpannableStringBuilder("Поворот: " + SN[oi]);
        b.setSpan(new ForegroundColorSpan(en(k) && oi > 0 ? ORANGE : DIM), 0, b.length(), 0);
        return b;
    }

    int idx(int val) { for (int i = 0; i < OV.length; i++) if (OV[i] == val) return i; return 0; }

    void edit(final String k, String name) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(12), dp(20), 0);
        final Spinner so = new Spinner(this);
        so.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ON));
        so.setSelection(idx(sp.getInt("o_" + k, -99)));
        TextView t = new TextView(this);
        t.setText("Ориентация экрана для этого приложения");
        l.addView(t); l.addView(so);
        new AlertDialog.Builder(this).setTitle(name).setView(l)
            .setPositiveButton("Сохранить", (dg, w) -> {
                int o = so.getSelectedItemPosition();
                sp.edit().putInt("o_" + k, OV[o]).putBoolean("e_" + k, o > 0).apply();
                filter(query);
            })
            .setNegativeButton("Отмена", (dg, w) -> filter(query)).show();
    }

    String letterAt(int i) {
        if (i < 0 || i >= shown.size()) return "";
        String k = shown.get(i).packageName;
        if (en(k)) return "★";
        String n = names.get(k);
        return n == null || n.isEmpty() ? "#" : n.substring(0, 1).toUpperCase();
    }

    // Быстрый ползунок справа: тонкая дорожка, зелёный «язычок», кремовый пузырь с буквой
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
                letter = letterAt(i);
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
            pt.setColor(drag ? GREEN : 0xCC6FFF95);
            c.drawRoundRect(x - tw, ty, x + tw, ty + th, tw, tw, pt);
            if (drag) {
                float bx = x - d(76), by = Math.max(0, Math.min(getHeight() - d(52), ty + th / 2 - d(26)));
                pt.setColor(ORANGE);
                c.drawRoundRect(bx, by, bx + d(52), by + d(52), d(14), d(14), pt);
                pt.setColor(0xFF1B1B1B);
                pt.setTextSize(d(26));
                pt.setTextAlign(Paint.Align.CENTER);
                c.drawText(letter, bx + d(26), by + d(36), pt);
            }
        }
    }
}
