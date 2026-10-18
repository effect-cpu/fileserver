package com.milktea.fileserver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;


public class NetUtil {

    public static List<String> ipv4List() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isAnyLocalAddress()) {
                        out.add(a.getHostAddress());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static List<String> ipv6List() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet6Address)) continue;
                    if (a.isLoopbackAddress() || a.isAnyLocalAddress()) continue;
                    if (a.isLinkLocalAddress() || a.isMulticastAddress()) continue;
                    String h = a.getHostAddress();
                    int p = h.indexOf('%');
                    if (p >= 0) h = h.substring(0, p);
                    if (h.startsWith("fe80") || h.startsWith("fec0")) continue;
                    out.add(h);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }


    public static String pickIpv4() {
        List<String> all = ipv4List();
        sortByCommonLan(all);
        return all.isEmpty() ? null : all.get(0);
    }


    public static String pickIpv6() {
        List<String> all = ipv6List();
        List<String> global = new ArrayList<String>();
        List<String> ula = new ArrayList<String>();
        for (int i = 0; i < all.size(); i++) {
            String s = all.get(i);
            if (s.startsWith("fc") || s.startsWith("fd")) ula.add(s);
            else global.add(s);
        }
        Collections.sort(global);
        Collections.sort(ula);
        if (!global.isEmpty()) return global.get(0);
        if (!ula.isEmpty()) return ula.get(0);
        return null;
    }

    private static void sortByCommonLan(List<String> list) {
        final List<String> order = new ArrayList<String>();
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s.startsWith("192.168.")) order.add(s);
        }
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s.startsWith("10.")) order.add(s);
        }
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s.startsWith("172.")) {
                try {
                    int second = Integer.parseInt(s.split("\\.")[1]);
                    if (second >= 16 && second <= 31) order.add(s);
                } catch (Throwable ignored) {
                }
            }
        }
        for (int i = 0; i < list.size(); i++) {
            if (!order.contains(list.get(i))) order.add(list.get(i));
        }
        list.clear();
        list.addAll(order);
    }
}
