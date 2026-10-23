package de.robv.android.xposed;
import android.content.SharedPreferences;
import java.util.Map; import java.util.Set;
public class XSharedPreferences implements SharedPreferences {
    public XSharedPreferences(String pkg, String name) {}
    public void reload() {}
    public boolean hasFileChanged() { return false; }
    public Map<String, ?> getAll() { return null; }
    public String getString(String k, String d) { return d; }
    public Set<String> getStringSet(String k, Set<String> d) { return d; }
    public int getInt(String k, int d) { return d; }
    public long getLong(String k, long d) { return d; }
    public float getFloat(String k, float d) { return d; }
    public boolean getBoolean(String k, boolean d) { return d; }
    public boolean contains(String k) { return false; }
    public Editor edit() { throw new UnsupportedOperationException(); }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
}
