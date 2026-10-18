package com.milktea.fileserver;

import java.io.IOException;


public class ServerHub {

    private static WebServer server;

    public static synchronized WebServer get() {
        return server;
    }

    public static synchronized boolean isRunning() {
        return server != null && server.isRunning();
    }

    public static synchronized void start(DocStore store, Prefs prefs, int port) throws IOException {
        if (server != null && server.isRunning()) return;
        WebServer s = new WebServer(store, prefs, port);
        s.start();
        server = s;
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
