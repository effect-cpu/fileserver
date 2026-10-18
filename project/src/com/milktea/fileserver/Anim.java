package com.milktea.fileserver;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.WeakHashMap;


public final class Anim {

    public static final long DUR_EXPAND = 320L;
    public static final long DUR_COLLAPSE = 240L;


    private static final WeakHashMap<View, ValueAnimator> RUNNING = new WeakHashMap<View, ValueAnimator>();

    private Anim() {
    }

    private static void cancelHeight(View v) {
        ValueAnimator a = RUNNING.remove(v);
        if (a != null) {
            try {
                a.cancel();
            } catch (Throwable ignored) {
            }
        }
    }


    public static int naturalHeight(View v) {
        int parentW = 0;
        ViewParent p = v.getParent();
        if (p instanceof View) {
            View pv = (View) p;
            parentW = pv.getWidth() - pv.getPaddingLeft() - pv.getPaddingRight();
        }
        if (parentW <= 0) {
            parentW = v.getResources().getDisplayMetrics().widthPixels;
        }
        int wSpec = View.MeasureSpec.makeMeasureSpec(Math.max(1, parentW), View.MeasureSpec.AT_MOST);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        v.measure(wSpec, hSpec);
        return Math.max(v.getMeasuredHeight(), 1);
    }


    public static void expand(final View v, long duration) {
        if (v.getVisibility() == View.VISIBLE && v.getHeight() > 0) return;
        cancelHeight(v);

        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        final int target = naturalHeight(v);

        v.animate().cancel();
        lp.height = 0;
        v.setLayoutParams(lp);
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setTranslationY(-v.getResources().getDisplayMetrics().density * 8f);
        v.setScaleY(0.97f);
        v.setPivotY(0f);

        v.animate().alpha(1f).translationY(0f).scaleY(1f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        ValueAnimator a = ValueAnimator.ofInt(0, target);
        a.setDuration(duration);
        a.setInterpolator(new OvershootInterpolator(0.6f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator an) {
                lp.height = (Integer) an.getAnimatedValue();
                v.setLayoutParams(lp);
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                RUNNING.remove(v);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setLayoutParams(lp);
            }
        });
        RUNNING.put(v, a);
        a.start();
    }


    public static void collapse(final View v, long duration) {
        final int start = v.getHeight();
        if (v.getVisibility() != View.VISIBLE) return;
        if (start <= 0) {
            v.setVisibility(View.GONE);
            return;
        }
        cancelHeight(v);

        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        v.animate().cancel();
        v.animate().alpha(0f).translationY(-v.getResources().getDisplayMetrics().density * 6f)
                .setDuration(duration).setInterpolator(new DecelerateInterpolator()).start();

        ValueAnimator a = ValueAnimator.ofInt(start, 0);
        a.setDuration(duration);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator an) {
                lp.height = (Integer) an.getAnimatedValue();
                v.setLayoutParams(lp);
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                RUNNING.remove(v);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setLayoutParams(lp);
                v.setVisibility(View.GONE);
                v.setAlpha(1f);
                v.setTranslationY(0f);
            }
        });
        RUNNING.put(v, a);
        a.start();
    }


    public static void fadeInUp(View v, float dyDp, long duration) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setTranslationY(v.getResources().getDisplayMetrics().density * dyDp);
        v.animate().alpha(1f).translationY(0f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }


    public static void popIn(View v, long duration) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setScaleX(0.88f);
        v.setScaleY(0.88f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(duration)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();
    }


    public static void popOut(final View v, long duration) {
        v.animate().cancel();
        v.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    public void run() {
                        v.setVisibility(View.GONE);
                        v.setAlpha(1f);
                        v.setScaleX(1f);
                        v.setScaleY(1f);
                    }
                })
                .start();
    }
}
