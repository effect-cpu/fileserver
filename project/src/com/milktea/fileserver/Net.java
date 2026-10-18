package com.milktea.fileserver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;


public final class Net {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private Net() {
    }

    public static String get(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android) FileServer/1.0");
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Accept-Encoding", "gzip");
            c.connect();

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) {
                in = new GZIPInputStream(in);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) throw new Exception("内容过大");
                bos.write(buf, 0, n);
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
