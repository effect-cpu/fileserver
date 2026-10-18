package com.milktea.fileserver;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;


public class Prefs {

    private static final String NAME = "fileserver_prefs";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean showIpv6() {
        return sp.getBoolean("show_ipv6", true);
    }

    public void setShowIpv6(boolean v) {
        sp.edit().putBoolean("show_ipv6", v).apply();
    }

    public boolean allowBrowse() {
        return sp.getBoolean("allow_browse", true);
    }

    public void setAllowBrowse(boolean v) {
        sp.edit().putBoolean("allow_browse", v).apply();
    }

    public boolean antiCrawler() {
        return sp.getBoolean("anti_crawler", false);
    }

    public void setAntiCrawler(boolean v) {
        sp.edit().putBoolean("anti_crawler", v).apply();
    }

    public boolean captcha() {
        return sp.getBoolean("captcha", false);
    }

    public void setCaptcha(boolean v) {
        sp.edit().putBoolean("captcha", v).apply();
    }

    public int port() {
        return sp.getInt("port", 8080);
    }

    public void setPort(int p) {
        sp.edit().putInt("port", p).apply();
    }


    public int themeMode() {
        return sp.getInt("theme_mode", 0);
    }

    public void setThemeMode(int mode) {
        sp.edit().putInt("theme_mode", mode).apply();
    }

    public boolean https() {
        return sp.getBoolean("https", false);
    }

    public void setHttps(boolean v) {
        sp.edit().putBoolean("https", v).apply();
    }


    public boolean agreed() {
        return sp.getBoolean("agreed", false);
    }

    public void setAgreed(boolean v) {
        sp.edit().putBoolean("agreed", v).apply();
    }


    public String lang() {
        String s = sp.getString("lang", "");
        return s == null ? "" : s;
    }

    public void setLang(String v) {
        sp.edit().putString("lang", v == null ? "" : v).apply();
    }

    public String treeUri() {
        String s = sp.getString("tree_uri", "");
        return s == null ? "" : s;
    }

    public void setTreeUri(String v) {
        sp.edit().putString("tree_uri", v).apply();
    }


    public List<String> banned() {
        List<String> out = new ArrayList<String>();
        String raw = sp.getString("banned", "");
        if (raw != null) {
            String[] arr = raw.split("\n");
            for (int i = 0; i < arr.length; i++) {
                String t = arr[i].trim();
                if (t.length() > 0) out.add(t);
            }
        }
        return out;
    }

    public void setBanned(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(list.get(i));
        }
        sp.edit().putString("banned", sb.toString()).apply();
    }
}
