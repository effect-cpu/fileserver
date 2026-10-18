package com.milktea.fileserver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;


public final class Md {

    private Md() {
    }

    public static CharSequence render(Context ctx, String markdown) {
        if (markdown == null || markdown.trim().length() == 0) return "";
        CharSequence out;
        try {
            String html = MiniMarkdown.toHtml(markdown);
            if (Build.VERSION.SDK_INT >= 24) {
                out = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
            } else {
                out = Html.fromHtml(html);
            }
        } catch (Throwable t) {
            return markdown;
        }
        return replaceLinks(ctx, out);
    }


    public static void into(TextView tv, String markdown) {
        if (tv == null) return;
        tv.setText(render(tv.getContext(), markdown));
        try {
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (Throwable ignored) {
        }
    }

    private static CharSequence replaceLinks(Context ctx, CharSequence cs) {
        if (!(cs instanceof Spannable)) return cs;
        int color = 0xFF1E88E5;
        try {
            color = ctx.getResources().getColor(R.color.colorPrimary);
        } catch (Throwable ignored) {
        }
        Spannable sp = (Spannable) cs;
        URLSpan[] spans = sp.getSpans(0, sp.length(), URLSpan.class);
        for (int i = 0; i < spans.length; i++) {
            URLSpan old = spans[i];
            int start = sp.getSpanStart(old);
            int end = sp.getSpanEnd(old);
            String url = old.getURL();
            sp.removeSpan(old);
            sp.setSpan(new SafeUrlSpan(url, color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }


    private static final class SafeUrlSpan extends ClickableSpan {

        private final String url;
        private final int color;

        SafeUrlSpan(String url, int color) {
            this.url = url;
            this.color = color;
        }

        @Override
        public void onClick(View widget) {
            Context ctx = widget.getContext();
            try {
                String low = url.toLowerCase(Locale.US);
                Intent intent;
                if (low.startsWith("mailto:")) {
                    intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } catch (Throwable t) {
                try {
                    Toast.makeText(ctx, ctx.getString(R.string.cannot_open) + ":\n" + url,
                            Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setColor(color);
            ds.setUnderlineText(true);
        }
    }
}
