package com.milktea.fileserver;

import java.util.ArrayList;
import java.util.List;


public final class MiniMarkdown {

    private MiniMarkdown() {
    }

    public static String toHtml(String md) {
        if (md == null) return "";
        String text = md.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);

        StringBuilder out = new StringBuilder();
        List<String> para = new ArrayList<String>();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            String t = line.trim();


            if (t.length() == 0) {
                flushPara(out, para);
                i++;
                continue;
            }


            if (t.startsWith("```")) {
                flushPara(out, para);
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append('\n');
                    i++;
                }
                i++;
                out.append("<pre>").append(esc(code.toString())).append("</pre>");
                continue;
            }


            int hashes = 0;
            while (hashes < t.length() && t.charAt(hashes) == '#') hashes++;
            if (hashes >= 1 && hashes <= 6 && hashes < t.length()
                    && (t.charAt(hashes) == ' ' || t.charAt(hashes) == '\t')) {
                flushPara(out, para);
                String body = t.substring(hashes).trim();
                out.append("<h").append(hashes).append(">")
                        .append(inline(body)).append("</h").append(hashes).append(">");
                i++;
                continue;
            }


            if (isRule(t)) {
                flushPara(out, para);
                out.append("<p>&#8212;&#8212;&#8212;&#8212;&#8212;&#8212;&#8212;&#8212;</p>");
                i++;
                continue;
            }


            if (isBullet(t)) {
                flushPara(out, para);
                out.append("<ul>");
                while (i < lines.length && isBullet(lines[i].trim())) {
                    out.append("<li>").append(inline(bulletText(lines[i].trim()))).append("</li>");
                    i++;
                }
                out.append("</ul>");
                continue;
            }


            if (isOrdered(t)) {
                flushPara(out, para);
                out.append("<ol>");
                while (i < lines.length && isOrdered(lines[i].trim())) {
                    out.append("<li>").append(inline(orderedText(lines[i].trim()))).append("</li>");
                    i++;
                }
                out.append("</ol>");
                continue;
            }


            if (t.startsWith(">")) {
                flushPara(out, para);
                out.append("<blockquote>");
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    String q = lines[i].trim();
                    q = q.substring(1).trim();
                    out.append("<p>").append(inline(q)).append("</p>");
                    i++;
                }
                out.append("</blockquote>");
                continue;
            }

            para.add(t);
            i++;
        }
        flushPara(out, para);
        return out.toString();
    }

    private static void flushPara(StringBuilder out, List<String> para) {
        if (para.isEmpty()) return;
        out.append("<p>");
        for (int k = 0; k < para.size(); k++) {
            if (k > 0) out.append("<br>");
            out.append(inline(para.get(k)));
        }
        out.append("</p>");
        para.clear();
    }

    private static boolean isRule(String t) {
        if (t.length() < 3) return false;
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int i = 0; i < t.length(); i++) {
            char x = t.charAt(i);
            if (x != c && x != ' ' && x != '\t') return false;
        }
        return true;
    }

    private static boolean isBullet(String t) {
        if (t.length() < 2) return false;
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '+') return false;
        return t.charAt(1) == ' ' || t.charAt(1) == '\t';
    }

    private static String bulletText(String t) {
        return t.substring(1).trim();
    }

    private static boolean isOrdered(String t) {
        int i = 0;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        if (i == 0 || i + 1 >= t.length()) return false;
        char c = t.charAt(i);
        if (c != '.' && c != ')') return false;
        char sp = t.charAt(i + 1);
        return sp == ' ' || sp == '\t';
    }

    private static String orderedText(String t) {
        int i = 0;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        i++;
        return t.substring(i).trim();
    }


    private static String inline(String s) {
        return inline(s, true);
    }


    private static String inline(String s, boolean linkify) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);


            if (c == '`') {
                int e = s.indexOf('`', i + 1);
                if (e > i) {
                    out.append("<tt>").append(esc(s.substring(i + 1, e))).append("</tt>");
                    i = e + 1;
                    continue;
                }
            }


            if (c == '*' && i + 1 < n && s.charAt(i + 1) == '*') {
                int e = s.indexOf("**", i + 2);
                if (e > i) {
                    out.append("<b>").append(inline(s.substring(i + 2, e), linkify)).append("</b>");
                    i = e + 2;
                    continue;
                }
            }
            if (c == '_' && i + 1 < n && s.charAt(i + 1) == '_') {
                int e = s.indexOf("__", i + 2);
                if (e > i) {
                    out.append("<b>").append(inline(s.substring(i + 2, e), linkify)).append("</b>");
                    i = e + 2;
                    continue;
                }
            }


            if (c == '*' || c == '_') {
                int e = s.indexOf(c, i + 1);
                if (e > i + 1) {
                    out.append("<i>").append(inline(s.substring(i + 1, e), linkify)).append("</i>");
                    i = e + 1;
                    continue;
                }
            }


            if (c == '[') {
                int rb = s.indexOf(']', i + 1);
                if (rb > i && rb + 1 < n && s.charAt(rb + 1) == '(') {
                    int rp = s.indexOf(')', rb + 2);
                    if (rp > rb) {
                        String label = s.substring(i + 1, rb);
                        String url = s.substring(rb + 2, rp).trim();
                        out.append("<a href=\"").append(esc(url)).append("\">")
                                .append(inline(label, false)).append("</a>");
                        i = rp + 1;
                        continue;
                    }
                }
            }


            if (linkify && (s.startsWith("http://", i) || s.startsWith("https://", i))) {
                int e = i;
                while (e < n) {
                    char x = s.charAt(e);
                    if (Character.isWhitespace(x) || x == '<' || x == '>' || x == '"') break;
                    e++;
                }
                String url = s.substring(i, e);
                out.append("<a href=\"").append(esc(url)).append("\">")
                        .append(esc(url)).append("</a>");
                i = e;
                continue;
            }


            if (linkify && c != '@') {
                int e = matchEmail(s, i);
                if (e > i) {
                    String mail = s.substring(i, e);
                    out.append("<a href=\"mailto:").append(esc(mail)).append("\">")
                            .append(esc(mail)).append("</a>");
                    i = e;
                    continue;
                }
            }

            out.append(esc(String.valueOf(c)));
            i++;
        }
        return out.toString();
    }


    private static int matchEmail(String s, int i) {
        int n = s.length();
        if (i > 0) {
            char prev = s.charAt(i - 1);
            if (Character.isLetterOrDigit(prev) || prev == '@' || prev == '.' || prev == '-') return -1;
        }
        int j = i;
        while (j < n) {
            char x = s.charAt(j);
            if (Character.isLetterOrDigit(x) || x == '.' || x == '_' || x == '%' || x == '+' || x == '-') j++;
            else break;
        }
        if (j == i || j >= n || s.charAt(j) != '@') return -1;
        j++;
        int domStart = j;
        while (j < n) {
            char x = s.charAt(j);
            if (Character.isLetterOrDigit(x) || x == '-' || x == '.') j++;
            else break;
        }
        String domain = s.substring(domStart, j);
        int dot = domain.lastIndexOf('.');
        if (dot <= 0 || dot >= domain.length() - 2) return -1;
        String tld = domain.substring(dot + 1);
        for (int k = 0; k < tld.length(); k++) {
            if (!Character.isLetter(tld.charAt(k))) return -1;
        }
        return j;
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
