package com.milktea.fileserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class RemoteInfo {

    public static class Row {
        public String label;
        public String value;
        public String url;

        public Row(String label, String value, String url) {
            this.label = label;
            this.value = value;
            this.url = url;
        }
    }


    public static class Version {
        public String versionName;
        public long versionCode;
        public String content;
        public String url;
        public List<Row> extra = new ArrayList<Row>();
    }

    private RemoteInfo() {
    }


    private static Object get(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (int k = 0; k < keys.length; k++) {
            String want = keys[k];
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(want)) {
                    Object v = e.getValue();
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = MiniJson.toDisplay(o);
        return s == null ? null : s.trim();
    }

    public static boolean isUrl(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.US);
        return t.startsWith("http://") || t.startsWith("https://");
    }


    private static String friendly(String key) {
        String k = key.toLowerCase(Locale.US);
        if (k.equals("author") || k.equals("writer")) return "作者";
        if (k.equals("content") || k.equals("text") || k.equals("body")) return "内容";
        if (k.equals("name") || k.equals("nick") || k.equals("nickname")) return "名称";
        if (k.equals("title")) return "标题";
        if (k.equals("qq")) return "QQ";
        if (k.equals("qqgroup") || k.equals("qq_group") || k.equals("group")) return "QQ 群";
        if (k.equals("wechat") || k.equals("wx") || k.equals("weixin")) return "微信";
        if (k.equals("email") || k.equals("mail")) return "邮箱";
        if (k.equals("weibo")) return "微博";
        if (k.equals("bilibili") || k.equals("b站")) return "B 站";
        if (k.equals("coolapk") || k.equals("酷安")) return "酷安";
        if (k.equals("telegram") || k.equals("tg")) return "Telegram";
        if (k.equals("blog") || k.equals("site") || k.equals("website") || k.equals("homepage")) return "主页";
        if (k.equals("github")) return "GitHub";
        if (k.equals("gitee")) return "Gitee";
        if (k.equals("url") || k.equals("link")) return "链接";
        if (k.equals("desc") || k.equals("describe") || k.equals("description") || k.equals("intro")) return "简介";
        if (k.equals("version")) return "版本";
        if (k.equals("msg") || k.equals("message")) return "消息";
        if (k.equals("thanks") || k.equals("thank")) return "鸣谢";
        return key;
    }


    public static List<Row> rows(Map<String, Object> map) {
        List<Row> out = new ArrayList<Row>();
        if (map == null) return out;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey();
            Object v = e.getValue();
            String text = str(v);
            if (text == null || text.length() == 0) continue;
            Row r = new Row(friendly(key), text, isUrl(text) && !text.contains("\n") ? text : null);
            out.add(r);
        }
        return out;
    }


    public static List<Row> rows(Map<String, Object> map, String... exclude) {
        List<Row> out = new ArrayList<Row>();
        if (map == null) return out;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey();
            boolean skip = false;
            if (exclude != null) {
                for (int i = 0; i < exclude.length; i++) {
                    if (exclude[i] != null && key.equalsIgnoreCase(exclude[i])) {
                        skip = true;
                        break;
                    }
                }
            }
            if (skip) continue;
            String text = str(e.getValue());
            if (text == null || text.length() == 0) continue;
            out.add(new Row(friendly(key), text, isUrl(text) && !text.contains("\n") ? text : null));
        }
        return out;
    }


    public static String findKey(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (int k = 0; k < keys.length; k++) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(keys[k]) && e.getValue() != null) {
                    return e.getKey();
                }
            }
        }
        return null;
    }


    public static String text(Map<String, Object> map, String... keys) {
        String k = findKey(map, keys);
        return k == null ? null : str(map.get(k));
    }


    public static Version version(Map<String, Object> map) {
        Version v = new Version();
        v.versionName = str(get(map, "versionName", "version", "ver", "name", "tag", "version_name"));
        Object code = get(map, "versionCode", "code", "version_code", "build", "buildNumber");
        if (code != null) {
            try {
                v.versionCode = (long) Double.parseDouble(str(code));
            } catch (Throwable ignored) {
            }
        }
        v.content = str(get(map, "updateContent", "updateDescribe", "updateInfo", "changelog",
                "content", "describe", "description", "desc", "update", "msg", "message", "info"));
        v.url = str(get(map, "downloadUrl", "downloadURL", "updateUrl", "apkUrl", "url",
                "download", "link", "apk"));
        if (!isUrl(v.url) && v.url != null) v.url = null;


        String[] known = {"versionname", "version", "ver", "name", "tag", "version_name",
                "versioncode", "code", "version_code", "build", "buildnumber",
                "updatecontent", "updatedescribe", "updateinfo", "changelog", "content",
                "describe", "description", "desc", "update", "msg", "message", "info",
                "downloadurl", "updateurl", "apkurl", "url", "download", "link", "apk"};
        if (map != null) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey();
                boolean skip = false;
                for (int k = 0; k < known.length; k++) {
                    if (key.equalsIgnoreCase(known[k])) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                String text = str(e.getValue());
                if (text == null || text.length() == 0) continue;
                v.extra.add(new Row(friendly(key), text, isUrl(text) && !text.contains("\n") ? text : null));
            }
        }
        return v;
    }


    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        long[] pa = numbers(a);
        long[] pb = numbers(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            long va = i < pa.length ? pa[i] : 0;
            long vb = i < pb.length ? pb[i] : 0;
            if (va != vb) return va > vb ? 1 : -1;
        }
        return 0;
    }


    private static long[] numbers(String s) {
        List<Long> out = new ArrayList<Long>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            if (s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                int j = i;
                while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
                out.add(parseLong(s.substring(i, j)));
                i = j;
            } else {
                i++;
            }
        }
        long[] r = new long[out.size()];
        for (int k = 0; k < r.length; k++) r[k] = out.get(k).longValue();
        return r;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }


    public static boolean isNewer(Version remote, String localName, long localCode) {
        if (remote == null) return false;
        if (remote.versionCode > 0 && localCode > 0) {
            if (remote.versionCode != localCode) return remote.versionCode > localCode;
        }
        return compareVersions(remote.versionName, localName) > 0;
    }
}
