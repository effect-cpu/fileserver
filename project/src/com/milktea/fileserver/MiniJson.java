package com.milktea.fileserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class MiniJson {

    public static class JsonException extends Exception {
        public JsonException(String m) {
            super(m);
        }
    }

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }


    public static Object parse(String text) throws JsonException {
        if (text == null) throw new JsonException("内容为空");
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') text = text.substring(1);
        MiniJson p = new MiniJson(text);
        p.ws();
        if (p.i >= p.s.length()) throw new JsonException("内容为空");
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) throw new JsonException("第 " + p.i + " 字符后有多余内容");
        return v;
    }


    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) throws JsonException {
        Object o = parse(text);
        if (o instanceof Map) return (Map<String, Object>) o;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("value", o);
        return m;
    }


    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() throws JsonException {
        if (i >= s.length()) throw new JsonException("内容意外结束");
        return s.charAt(i);
    }

    private Object value() throws JsonException {
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() throws JsonException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++;
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            String k = string();
            ws();
            if (peek() != ':') throw new JsonException("缺少 ':' @" + i);
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = peek();
            if (c == ',') {
                i++;
                continue;
            }
            if (c == '}') {
                i++;
                return m;
            }
            throw new JsonException("对象里缺少 ',' 或 '}' @" + i);
        }
    }

    private List<Object> array() throws JsonException {
        List<Object> l = new ArrayList<Object>();
        i++;
        ws();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = peek();
            if (c == ',') {
                i++;
                continue;
            }
            if (c == ']') {
                i++;
                return l;
            }
            throw new JsonException("数组里缺少 ',' 或 ']' @" + i);
        }
    }

    private String string() throws JsonException {
        if (peek() != '"') throw new JsonException("期望字符串 @" + i);
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new JsonException("字符串未闭合");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) throw new JsonException("转义未完成");
            char e = s.charAt(i++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new JsonException("\\u 转义不完整");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: throw new JsonException("非法转义 \\" + e);
            }
        }
    }

    private Object number() throws JsonException {
        int start = i;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') i++;
            else break;
        }
        String t = s.substring(start, i);
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException ex) {
            throw new JsonException("非法数字 '" + t + "'");
        }
    }


    public static String toDisplay(Object o) {
        if (o == null) return "";
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder();
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(e.getKey()).append(": ").append(toDisplay(e.getValue()));
            }
            return sb.toString();
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) o;
            for (int k = 0; k < l.size(); k++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("• ").append(toDisplay(l.get(k)));
            }
            return sb.toString();
        }
        if (o instanceof Double) {
            double d = ((Double) o).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(o);
    }
}
