package com.milktea.fileserver;

import android.graphics.Bitmap;


public class BlurUtil {

    public static void blur(Bitmap bmp, int radius) {
        if (bmp == null || radius < 1) return;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= 0 || h <= 0) return;

        int[] pix = new int[w * h];
        bmp.getPixels(pix, 0, w, 0, 0, w, h);
        int[] tmp = new int[pix.length];

        for (int pass = 0; pass < 3; pass++) {
            blurHorizontal(pix, tmp, w, h, radius);
            blurVertical(tmp, pix, w, h, radius);
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h);
    }

    private static void blurHorizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int a = 0, cr = 0, cg = 0, cb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[row + clamp(i, w)];
                a += (p >>> 24);
                cr += (p >> 16) & 0xFF;
                cg += (p >> 8) & 0xFF;
                cb += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = ((a / div) << 24) | ((cr / div) << 16) | ((cg / div) << 8) | (cb / div);
                int pOut = src[row + clamp(x - r, w)];
                int pIn = src[row + clamp(x + r + 1, w)];
                a += (pIn >>> 24) - (pOut >>> 24);
                cr += ((pIn >> 16) & 0xFF) - ((pOut >> 16) & 0xFF);
                cg += ((pIn >> 8) & 0xFF) - ((pOut >> 8) & 0xFF);
                cb += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }

    private static void blurVertical(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int a = 0, cr = 0, cg = 0, cb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[clamp(i, h) * w + x];
                a += (p >>> 24);
                cr += (p >> 16) & 0xFF;
                cg += (p >> 8) & 0xFF;
                cb += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = ((a / div) << 24) | ((cr / div) << 16) | ((cg / div) << 8) | (cb / div);
                int pOut = src[clamp(y - r, h) * w + x];
                int pIn = src[clamp(y + r + 1, h) * w + x];
                a += (pIn >>> 24) - (pOut >>> 24);
                cr += ((pIn >> 16) & 0xFF) - ((pOut >> 16) & 0xFF);
                cg += ((pIn >> 8) & 0xFF) - ((pOut >> 8) & 0xFF);
                cb += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }

    private static int clamp(int v, int max) {
        if (v < 0) return 0;
        if (v >= max) return max - 1;
        return v;
    }
}
