package com.milktea.fileserver;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;


public final class LiquidGlass {


    public static final float SIGMA_BAR = 16f;

    public static final float SIGMA_DIALOG = 14f;

    private LiquidGlass() {
    }

    public static boolean nativeBlurAvailable() {
        return false;
    }

    public static String blurBackend() {
        return "自研盒式模糊(降采样 1/4)";
    }


    public static Bitmap frost(Bitmap src, float sigma) {
        if (src == null || src.isRecycled()) return null;
        try {
            int sw = Math.max(1, src.getWidth() / 4);
            int sh = Math.max(1, src.getHeight() / 4);
            Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
            if (small != src && !src.isRecycled()) src.recycle();
            int radius = sigma >= 20f ? 3 : (sigma >= 10f ? 2 : 1);
            BlurUtil.blur(small, radius);
            return small;
        } catch (Throwable t) {
            return null;
        }
    }


    public static Bitmap capture(View view, int x, int y, int w, int h, float scale) {
        if (view == null || w <= 0 || h <= 0) return null;
        try {
            int tw = Math.max(1, (int) (w * scale));
            int th = Math.max(1, (int) (h * scale));
            Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.scale(scale, scale);
            canvas.translate(-x, -y);
            view.draw(canvas);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }
}
