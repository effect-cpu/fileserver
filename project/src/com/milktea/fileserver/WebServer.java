package com.milktea.fileserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLServerSocketFactory;


public class WebServer {

    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int COPY_BUFFER = 32 * 1024;
    private static final long IN_MEMORY_LIMIT = 64L * 1024 * 1024;
    private static final int RATE_WINDOW_MS = 10000;
    private static final int RATE_MAX = 120;
    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000L;
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final String[] BOT_UA = {
            "bot", "spider", "crawl", "scrapy", "curl", "wget", "python", "java/",
            "okhttp", "go-http", "httpclient", "libwww", "axios", "node-fetch",
            "postman", "scanner", "nmap", "nikto", "masscan", "zgrab", "http_request",
    };

    private final DocStore store;
    private final Prefs prefs;
    private final int port;

    private final List<ServerSocket> sockets = new ArrayList<ServerSocket>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    private final Map<String, String[]> captchaSessions = new ConcurrentHashMap<String, String[]>();
    private final Map<String, long[]> rateCounters = new ConcurrentHashMap<String, long[]>();
    private final Random random = new Random();
    private SSLServerSocketFactory tls;

    private final ThreadLocal<String> logReason = new ThreadLocal<String>();

    public WebServer(DocStore store, Prefs prefs, int port) {
        this.store = store;
        this.prefs = prefs;
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }


    public void start() throws IOException {
        if (prefs.https()) {
            try {
                tls = TlsFactory.create();
            } catch (Throwable t) {
                tls = null;
                Logs.add("HTTPS 初始化失败,已回退到 HTTP: " + t);
            }
        }
        ServerSocket v6 = bind("::");
        ServerSocket v4 = bind("0.0.0.0");
        if (v6 != null) sockets.add(v6);
        if (v4 != null) sockets.add(v4);
        if (sockets.isEmpty()) {
            throw new IOException("端口 " + port + " 绑定失败(可能被其它应用占用)");
        }
        running = true;
        for (int i = 0; i < sockets.size(); i++) {
            final ServerSocket ss = sockets.get(i);
            Thread t = new Thread(new Runnable() {
                public void run() {
                    acceptLoop(ss);
                }
            }, "fs-accept");
            t.setDaemon(true);
            t.start();
        }
        Logs.add((tls != null ? "HTTPS " : "HTTP ") + "服务已启动 · 监听端口 " + port
                + (v6 != null ? " [IPv6]" : "") + (v4 != null ? " [IPv4]" : ""));
    }

    public void stop() {
        running = false;
        for (int i = 0; i < sockets.size(); i++) {
            try {
                sockets.get(i).close();
            } catch (IOException ignored) {
            }
        }
        sockets.clear();
        pool.shutdownNow();
        Logs.add("服务已停止");
    }

    private ServerSocket bind(String host) {
        try {
            ServerSocket ss;
            if (tls != null) {
                ss = tls.createServerSocket();
            } else {
                ss = new ServerSocket();
            }
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(host), port));
            return ss;
        } catch (Throwable t) {
            return null;
        }
    }

    private void acceptLoop(ServerSocket ss) {
        while (running) {
            try {
                final Socket client = ss.accept();
                pool.execute(new Runnable() {
                    public void run() {
                        handle(client);
                    }
                });
            } catch (Throwable t) {
                break;
            }
        }
    }


    private void handle(Socket client) {
        String ip = "-";
        String method = "-";
        String path = "-";
        int code = 500;
        boolean logIt = true;
        logReason.remove();
        try {
            client.setSoTimeout(READ_TIMEOUT_MS);
            client.setTcpNoDelay(true);
            InetAddress remote = client.getInetAddress();
            ip = normalize(remote);

            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = new BufferedOutputStream(client.getOutputStream());

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.length() == 0) {
                logIt = false;
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 3 || !parts[2].startsWith("HTTP/")) {
                logIt = false;
                return;
            }
            method = parts[0];
            String target = parts[1];
            Map<String, String> headers = readHeaders(in);

            String query = "";
            String rawPath = target;
            int qi = rawPath.indexOf('?');
            if (qi >= 0) {
                query = rawPath.substring(qi + 1);
                rawPath = rawPath.substring(0, qi);
            }
            path = percentDecode(rawPath);
            code = route(out, method, path, query, headers, ip);
            out.flush();
        } catch (javax.net.ssl.SSLException ssl) {
            logIt = false;
        } catch (Throwable t) {
            code = code == 0 ? 500 : code;
        } finally {
            try {
                client.close();
            } catch (Throwable ignored) {
            }
            if (logIt) writeLog(ip, method, path, code);
        }
    }

    private Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        int total = 0;
        String line;
        while ((line = readLine(in)) != null) {
            if (line.length() == 0) break;
            total += line.length();
            if (total > MAX_HEADER_BYTES) break;
            int c = line.indexOf(':');
            if (c > 0) {
                headers.put(line.substring(0, c).trim().toLowerCase(Locale.US),
                        line.substring(c + 1).trim());
            }
        }
        return headers;
    }

    private int route(OutputStream out, String method, String path, String query,
                      Map<String, String> headers, String ip) throws IOException {
        boolean head = "HEAD".equalsIgnoreCase(method);
        if (!"GET".equalsIgnoreCase(method) && !head) {
            sendError(out, 405, "Method Not Allowed", "方法不允许", "仅支持 GET / HEAD 请求", false);
            return 405;
        }


        if (isBanned(ip)) {
            sendError(out, 403, "Forbidden", "IP 已被封禁", "你的 IP 已被封禁: " + ip, head);
            return 403;
        }


        if (prefs.antiCrawler()) {
            String ua = headers.get("user-agent");
            if (isBot(ua)) {
                sendError(out, 403, "Forbidden", "检测到爬虫特征", "疑似机器人访问,已拦截", head);
                return 403;
            }
            if (rateLimited(ip)) {
                sendError(out, 429, "Too Many Requests", "请求过于频繁", "10 秒内请求次数超过上限,请稍后再试", head);
                return 429;
            }
        }


        if (prefs.captcha()) {
            if (path.equals("/__captcha")) {
                return handleCaptcha(out, query, head);
            }
            if (!hasValidCaptcha(headers)) {
                String next = query.length() > 0 ? path + "?" + query : path;
                sendCaptchaPage(out, next, null, head);
                return 200;
            }
        }


        return servePath(out, path, query, head);
    }


    private int servePath(OutputStream out, String path, String query, boolean head) throws IOException {
        String[] segs = DocStore.splitPath(path);
        if (segs == null) {
            sendError(out, 403, "Forbidden", "非法路径", "请求路径包含非法的上级目录引用", head);
            return 403;
        }

        DocStore.Doc doc;
        try {
            doc = store.resolve(path);
        } catch (IOException e) {
            sendError(out, 500, "Internal Server Error", "读取失败", String.valueOf(e.getMessage()), head);
            return 500;
        }
        if (doc == null) {
            sendError(out, 404, "Not Found", "文件或目录不存在", path, head);
            return 404;
        }

        if (doc.dir) {
            if (!path.endsWith("/")) {
                String location = encodePath(path) + "/" + (query.length() > 0 ? "?" + query : "");
                writeRedirect(out, location);
                return 302;
            }
            DocStore.Doc index = null;
            try {
                index = store.findChild(doc.docId, "index.html");
                if (index == null || index.dir) index = store.findChild(doc.docId, "index.htm");
            } catch (IOException ignored) {
            }
            if (index != null && !index.dir) return sendFile(out, index, head);
            if (!prefs.allowBrowse()) {
                sendError(out, 403, "Forbidden", "目录浏览已关闭", "该目录下没有 index.html,且已关闭目录浏览", head);
                return 403;
            }
            return sendListing(out, doc, path, head);
        }
        return sendFile(out, doc, head);
    }

    private int sendFile(OutputStream out, DocStore.Doc doc, boolean head) throws IOException {
        long len = doc.size;
        byte[] memory = null;
        InputStream in = null;
        try {
            if (len < 0) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                in = store.open(doc.docId);
                byte[] buf = new byte[COPY_BUFFER];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > IN_MEMORY_LIMIT) throw new IOException("文件过大且长度未知");
                    bos.write(buf, 0, n);
                }
                memory = bos.toByteArray();
                len = memory.length;
            }

            StringBuilder h = new StringBuilder();
            h.append("HTTP/1.1 200 OK\r\n");
            h.append("Content-Type: ").append(mimeOf(doc.name)).append("\r\n");
            h.append("Content-Length: ").append(len).append("\r\n");
            h.append("Cache-Control: no-cache\r\n");
            h.append("Accept-Ranges: none\r\n");
            h.append("Connection: close\r\n\r\n");
            out.write(h.toString().getBytes("UTF-8"));

            if (head) {
                out.flush();
                return 200;
            }
            if (memory != null) {
                out.write(memory);
            } else {
                in = store.open(doc.docId);
                byte[] buf = new byte[COPY_BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            out.flush();
            return 200;
        } catch (IOException e) {
            throw e;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private int sendListing(OutputStream out, DocStore.Doc dir, String path, boolean head) throws IOException {
        List<DocStore.Doc> list;
        try {
            list = store.children(dir.docId);
        } catch (IOException e) {
            sendError(out, 500, "Internal Server Error", "读取目录失败", null, head);
            return 500;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>目录 ").append(esc(path)).append("</title>");
        sb.append("<style>")
                .append("body{font-family:-apple-system,Roboto,'Segoe UI',sans-serif;background:#f2f4f7;margin:0;padding:16px;color:#1f2937}")
                .append("h1{font-size:18px;margin:0 0 4px}")
                .append(".path{color:#6b7280;font-size:13px;word-break:break-all;margin-bottom:14px}")
                .append("a{text-decoration:none;color:#1f2937}")
                .append(".item{display:flex;align-items:center;padding:12px;background:#fff;border:1px solid #e3e6ea;border-radius:10px;margin-bottom:8px}")
                .append(".name{flex:1;word-break:break-all}")
                .append(".meta{color:#9ca3af;font-size:12px;margin-left:10px;white-space:nowrap}")
                .append(".dir{color:#1e88e5;font-weight:600}")
                .append(".empty{color:#9ca3af;font-size:13px}")
                .append("</style></head><body>");
        sb.append("<h1>目录浏览</h1>");
        sb.append("<div class=\"path\">").append(esc(path)).append("</div>");

        if (!path.equals("/")) {
            String parent = path.substring(0, path.endsWith("/") ? path.length() - 1 : path.length());
            int slash = parent.lastIndexOf('/');
            parent = slash <= 0 ? "/" : parent.substring(0, slash + 1);
            sb.append("<div class=\"item\"><span class=\"name\">📁 <a href=\"")
                    .append(encodePath(parent)).append("\">..</a></span></div>");
        }

        if (list.isEmpty()) {
            sb.append("<div class=\"empty\">(空目录)</div>");
        }
        for (int i = 0; i < list.size(); i++) {
            DocStore.Doc d = list.get(i);
            String name = d.name;
            String href = encodePath(path + name) + (d.dir ? "/" : "");
            sb.append("<div class=\"item\">");
            sb.append("<span class=\"name\">").append(d.dir ? "📁 " : "📄 ");
            sb.append("<a class=\"").append(d.dir ? "dir" : "").append("\" href=\"")
                    .append(href).append("\">").append(esc(name)).append("</a></span>");
            sb.append("<span class=\"meta\">").append(d.dir ? "目录" : human(d.size)).append("</span>");
            sb.append("</div>");
        }
        sb.append("</body></html>");

        writeResponse(out, 200, "OK", "text/html; charset=utf-8",
                sb.toString().getBytes("UTF-8"), head);
        return 200;
    }


    private boolean hasValidCaptcha(Map<String, String> headers) {
        String cookie = headers.get("cookie");
        if (cookie == null) return false;
        String token = null;
        String[] parts = cookie.split(";");
        for (int i = 0; i < parts.length; i++) {
            String kv = parts[i].trim();
            if (kv.startsWith("fscap=")) token = kv.substring(6).trim();
        }
        if (token == null) return false;
        String[] sess = captchaSessions.get(token);
        if (sess == null) return false;
        if (Long.parseLong(sess[1]) < System.currentTimeMillis()) {
            captchaSessions.remove(token);
            return false;
        }
        return true;
    }

    private int handleCaptcha(OutputStream out, String query, boolean head) throws IOException {
        Map<String, String> q = parseQuery(query);
        String token = q.get("t");
        String input = q.get("c");
        String next = q.get("next");
        if (next == null || !next.startsWith("/")) next = "/";

        String[] sess = token == null ? null : captchaSessions.get(token);
        if (sess != null && Long.parseLong(sess[1]) >= System.currentTimeMillis()) {
            if (sess[0].equalsIgnoreCase(input == null ? "" : input.trim())) {
                StringBuilder h = new StringBuilder();
                h.append("HTTP/1.1 302 Found\r\n");
                h.append("Location: ").append(encodePath(next)).append("\r\n");
                h.append("Set-Cookie: fscap=").append(token).append("; Path=/; Max-Age=3600; HttpOnly\r\n");
                h.append("Content-Length: 0\r\n");
                h.append("Connection: close\r\n\r\n");
                out.write(h.toString().getBytes("UTF-8"));
                out.flush();
                return 302;
            }
            sendCaptchaPage(out, next, "验证码不正确,请重新输入", head);
            return 200;
        }
        sendCaptchaPage(out, next, "验证码已过期,请重新获取", head);
        return 200;
    }

    private void sendCaptchaPage(OutputStream out, String next, String error, boolean head) throws IOException {
        cleanupCaptcha();

        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        String token = randomHex(16);
        captchaSessions.put(token, new String[]{code.toString(),
                String.valueOf(System.currentTimeMillis() + CAPTCHA_TTL_MS)});

        StringBuilder glyph = new StringBuilder();
        String[] colors = {"#1e88e5", "#00897b", "#e53935", "#6d4c41", "#3949ab"};
        for (int i = 0; i < code.length(); i++) {
            int rot = random.nextInt(31) - 15;
            glyph.append("<span style=\"display:inline-block;transform:rotate(")
                    .append(rot).append("deg);color:")
                    .append(colors[random.nextInt(colors.length)])
                    .append(";font-size:30px;font-weight:700;letter-spacing:4px\">")
                    .append(code.charAt(i)).append("</span>");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>安全验证</title><style>")
                .append("body{font-family:-apple-system,Roboto,'Segoe UI',sans-serif;background:#f2f4f7;margin:0;")
                .append("display:flex;align-items:center;justify-content:center;min-height:100vh}")
                .append(".box{background:#fff;border:1px solid #e3e6ea;border-radius:14px;padding:22px;width:300px;text-align:center}")
                .append("h2{margin:0 0 6px;font-size:18px;color:#1f2937}")
                .append("p{color:#6b7280;font-size:13px;margin:6px 0 14px}")
                .append(".code{background:#f7f9fc;border:1px dashed #c7ccd3;border-radius:10px;padding:10px;margin-bottom:14px;")
                .append("background-image:repeating-linear-gradient(45deg,#eef1f5 0 2px,transparent 2px 6px)}")
                .append("input{width:100%;box-sizing:border-box;padding:10px;border:1px solid #d7dbe0;border-radius:8px;font-size:15px;outline:none}")
                .append("button{width:100%;margin-top:12px;padding:11px;border:0;border-radius:8px;background:#1e88e5;color:#fff;font-size:15px}")
                .append(".err{color:#e53935;font-size:12px;margin-top:10px}")
                .append("</style></head><body><div class=\"box\">");
        sb.append("<h2>安全验证</h2>");
        sb.append("<p>请输入下方字符以继续访问</p>");
        sb.append("<div class=\"code\">").append(glyph).append("</div>");
        sb.append("<form method=\"get\" action=\"/__captcha\">");
        sb.append("<input type=\"hidden\" name=\"t\" value=\"").append(token).append("\">");
        sb.append("<input type=\"hidden\" name=\"next\" value=\"").append(esc(next)).append("\">");
        sb.append("<input name=\"c\" placeholder=\"验证码\" autocomplete=\"off\" autocapitalize=\"characters\">");
        sb.append("<button type=\"submit\">验证</button>");
        sb.append("</form>");
        if (error != null) sb.append("<div class=\"err\">").append(esc(error)).append("</div>");
        sb.append("</div></body></html>");

        writeResponse(out, 200, "OK", "text/html; charset=utf-8",
                sb.toString().getBytes("UTF-8"), head);
    }

    private void cleanupCaptcha() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String[]> e : captchaSessions.entrySet()) {
            try {
                if (Long.parseLong(e.getValue()[1]) < now) captchaSessions.remove(e.getKey());
            } catch (Throwable ignored) {
            }
        }
    }

    private String randomHex(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append("0123456789abcdef".charAt(random.nextInt(16)));
        return sb.toString();
    }


    private boolean isBanned(String ip) {
        List<String> list = prefs.banned();
        for (int i = 0; i < list.size(); i++) {
            String pattern = list.get(i);
            if (pattern.length() == 0) continue;
            if (pattern.equals(ip)) return true;
            if (pattern.indexOf('*') >= 0 && wildcard(pattern, ip)) return true;
            if (pattern.endsWith(".") && ip.startsWith(pattern)) return true;
        }
        return false;
    }

    private static boolean wildcard(String p, String s) {
        return wc(p, 0, s, 0);
    }

    private static boolean wc(String p, int pi, String s, int si) {
        if (pi == p.length()) return si == s.length();
        char pc = p.charAt(pi);
        if (pc == '*') {
            for (int k = si; k <= s.length(); k++) {
                if (wc(p, pi + 1, s, k)) return true;
            }
            return false;
        }
        if (si < s.length() && s.charAt(si) == pc) return wc(p, pi + 1, s, si + 1);
        return false;
    }

    private boolean isBot(String ua) {
        if (ua == null || ua.trim().length() == 0) return true;
        String low = ua.toLowerCase(Locale.US);
        for (int i = 0; i < BOT_UA.length; i++) {
            if (low.contains(BOT_UA[i])) return true;
        }
        return false;
    }

    private boolean rateLimited(String ip) {
        long now = System.currentTimeMillis();
        long[] cell = rateCounters.get(ip);
        if (cell == null) {
            rateCounters.put(ip, new long[]{1, now});
            return false;
        }
        if (now - cell[1] > RATE_WINDOW_MS) {
            cell[0] = 1;
            cell[1] = now;
            return false;
        }
        cell[0]++;
        return cell[0] > RATE_MAX;
    }


    private void writeResponse(OutputStream out, int code, String reason,
                               String contentType, byte[] body, boolean head) throws IOException {
        int len = body == null ? 0 : body.length;
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        h.append("Content-Type: ").append(contentType).append("\r\n");
        h.append("Content-Length: ").append(len).append("\r\n");
        h.append("Cache-Control: no-cache\r\n");
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes("UTF-8"));
        if (!head && body != null) out.write(body);
        out.flush();
    }

    private void writeRedirect(OutputStream out, String location) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 302 Found\r\n");
        h.append("Location: ").append(location).append("\r\n");
        h.append("Content-Length: 0\r\n");
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes("UTF-8"));
        out.flush();
    }


    private void sendError(OutputStream out, int code, String reason, String label,
                           String detail, boolean head) throws IOException {
        logReason.set(label);
        boolean hasDetail = detail != null && detail.length() > 0 && !detail.equals(label);
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + code + " " + esc(label) + "</title><style>"
                + "body{font-family:-apple-system,Roboto,'Segoe UI',sans-serif;background:#f2f4f7;color:#1f2937;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}"
                + ".box{background:#fff;border:1px solid #e3e6ea;border-radius:14px;padding:24px;text-align:center;max-width:360px}"
                + "h1{font-size:34px;margin:0;color:#1e88e5}"
                + ".lbl{margin-top:6px;font-size:15px;font-weight:600;color:#1f2937}"
                + "p{color:#6b7280;font-size:13px;margin:8px 0 0;word-break:break-all}"
                + "</style></head><body><div class=\"box\"><h1>" + code + "</h1>"
                + "<div class=\"lbl\">" + esc(label) + "</div>"
                + (hasDetail ? "<p>" + esc(detail) + "</p>" : "")
                + "</div></body></html>";
        writeResponse(out, code, reason, "text/html; charset=utf-8", html.getBytes("UTF-8"), head);
    }

    private void writeLog(String ip, String method, String path, int code) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append(time).append(" | ").append(ip).append(" | ").append(method)
                .append(" | ").append(path).append(" | ").append(code);
        String why = logReason.get();
        if (why != null && why.length() > 0) sb.append(' ').append(why);
        Logs.add(sb.toString());
    }


    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c == '\r') continue;
            bos.write(c);
            if (bos.size() > 8192) break;
        }
        if (c == -1 && bos.size() == 0) return null;
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String normalize(InetAddress addr) {
        if (addr == null) return "-";
        String h = addr.getHostAddress();
        if (h == null) return "-";
        int p = h.indexOf('%');
        if (p >= 0) h = h.substring(0, p);
        if (h.startsWith("::ffff:")) h = h.substring(7);
        if (h.equals("::1")) h = "127.0.0.1";
        return h;
    }

    private static String percentDecode(String s) throws IOException {
        if (s == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 <= s.length() - 1) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    bos.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            if (c < 0x80) {
                bos.write(c);
            } else {
                byte[] bb = String.valueOf(c).getBytes("UTF-8");
                bos.write(bb, 0, bb.length);
            }
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String encodePath(String p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            boolean safe = c == '/' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (safe) {
                sb.append(c);
            } else {
                byte[] bb = String.valueOf(c).getBytes("UTF-8");
                for (int k = 0; k < bb.length; k++) {
                    sb.append('%');
                    String hex = Integer.toHexString(bb[k] & 0xFF).toUpperCase(Locale.US);
                    if (hex.length() < 2) sb.append('0');
                    sb.append(hex);
                }
            }
        }
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<String, String>();
        if (query == null || query.length() == 0) return map;
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i].length() == 0) continue;
            int eq = pairs[i].indexOf('=');
            String k = eq >= 0 ? pairs[i].substring(0, eq) : pairs[i];
            String v = eq >= 0 ? pairs[i].substring(eq + 1) : "";
            try {
                map.put(percentDecode(k), percentDecode(v.replace('+', ' ')));
            } catch (IOException ignored) {
            }
        }
        return map;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String human(long size) {
        if (size < 0) return "";
        if (size < 1024) return size + " B";
        double kb = size / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static String mimeOf(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        if (ext.equals("html") || ext.equals("htm")) return "text/html; charset=utf-8";
        if (ext.equals("css")) return "text/css; charset=utf-8";
        if (ext.equals("js") || ext.equals("mjs")) return "application/javascript; charset=utf-8";
        if (ext.equals("json")) return "application/json; charset=utf-8";
        if (ext.equals("txt") || ext.equals("log") || ext.equals("md")) return "text/plain; charset=utf-8";
        if (ext.equals("xml")) return "application/xml; charset=utf-8";
        if (ext.equals("csv")) return "text/csv; charset=utf-8";
        if (ext.equals("png")) return "image/png";
        if (ext.equals("jpg") || ext.equals("jpeg")) return "image/jpeg";
        if (ext.equals("gif")) return "image/gif";
        if (ext.equals("webp")) return "image/webp";
        if (ext.equals("svg")) return "image/svg+xml";
        if (ext.equals("ico")) return "image/x-icon";
        if (ext.equals("bmp")) return "image/bmp";
        if (ext.equals("heic") || ext.equals("heif")) return "image/heic";
        if (ext.equals("mp4") || ext.equals("m4v")) return "video/mp4";
        if (ext.equals("webm")) return "video/webm";
        if (ext.equals("mkv")) return "video/x-matroska";
        if (ext.equals("mp3")) return "audio/mpeg";
        if (ext.equals("wav")) return "audio/wav";
        if (ext.equals("ogg")) return "audio/ogg";
        if (ext.equals("flac")) return "audio/flac";
        if (ext.equals("m4a")) return "audio/mp4";
        if (ext.equals("pdf")) return "application/pdf";
        if (ext.equals("zip")) return "application/zip";
        if (ext.equals("rar")) return "application/vnd.rar";
        if (ext.equals("7z")) return "application/x-7z-compressed";
        if (ext.equals("tar")) return "application/x-tar";
        if (ext.equals("gz")) return "application/gzip";
        if (ext.equals("apk")) return "application/vnd.android.package-archive";
        if (ext.equals("doc")) return "application/msword";
        if (ext.equals("docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (ext.equals("xls")) return "application/vnd.ms-excel";
        if (ext.equals("xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (ext.equals("ppt")) return "application/vnd.ms-powerpoint";
        if (ext.equals("pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/octet-stream";
    }
}
