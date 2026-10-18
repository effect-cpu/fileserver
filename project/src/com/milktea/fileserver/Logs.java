package com.milktea.fileserver;

import java.util.ArrayList;
import java.util.List;


public class Logs {

    public interface Listener {
        void onLog(String line);
    }

    private static final int MAX_LINES = 2000;

    private static final ArrayList<String> LINES = new ArrayList<String>();
    private static final ArrayList<Listener> LISTENERS = new ArrayList<Listener>();

    public static synchronized void add(String line) {
        if (line == null) return;
        LINES.add(line);
        while (LINES.size() > MAX_LINES) LINES.remove(0);
        Listener[] arr = LISTENERS.toArray(new Listener[LISTENERS.size()]);
        for (int i = 0; i < arr.length; i++) {
            try {
                arr[i].onLog(line);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized List<String> snapshot() {
        return new ArrayList<String>(LINES);
    }

    public static synchronized void clear() {
        LINES.clear();
    }

    public static synchronized void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static synchronized void removeListener(Listener l) {
        LISTENERS.remove(l);
    }
}
