package com.milktea.fileserver;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class GlassDialog extends Dialog {

    public interface OnText {
        void onText(String text);
    }

    public interface OnChoose {
        void onChoose(int index);
    }

    private final Activity host;
    private Bitmap backdrop;

    private GlassDialog(Activity host) {
        super(host, R.style.GlassDialogTheme);
        this.host = host;
    }


    public static GlassDialog input(Activity host, String title, String message, String hint,
                                   String initial, boolean digitsOnly, final OnText callback) {
        final GlassDialog d = new GlassDialog(host);
        d.setContentView(R.layout.dialog_glass_input);
        d.takeBackdrop();

        ((TextView) d.findViewById(R.id.dlgTitle)).setText(title);
        TextView msg = (TextView) d.findViewById(R.id.dlgMsg);
        if (message != null && message.length() > 0) {
            msg.setText(message);
            msg.setVisibility(View.VISIBLE);
        }

        final EditText input = (EditText) d.findViewById(R.id.dlgInput);
        input.setHint(hint == null ? "" : hint);
        if (initial != null) {
            input.setText(initial);
            input.setSelection(input.getText().length());
        }
        input.setInputType(digitsOnly ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);

        ((Button) d.findViewById(R.id.dlgCancel)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        ((Button) d.findViewById(R.id.dlgOk)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String text = input.getText().toString();
                d.dismiss();
                if (callback != null) callback.onText(text);
            }
        });

        d.bindDismissOnOutside();
        d.present(R.id.dlgCard, true);
        return d;
    }


    public static GlassDialog choose(Activity host, String title, String[] items,
                                    int checked, final OnChoose callback) {
        final GlassDialog d = new GlassDialog(host);
        d.setContentView(R.layout.dialog_glass_choice);
        d.takeBackdrop();

        ((TextView) d.findViewById(R.id.dlgTitle)).setText(title);
        LinearLayout list = (LinearLayout) d.findViewById(R.id.chList);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.cell_bg);
            row.setPadding(d.dp(14), d.dp(14), d.dp(14), d.dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = d.dp(8);
            row.setLayoutParams(lp);

            TextView name = new TextView(host);
            name.setText(items[i]);
            name.setTextSize(15);
            name.setTextColor(host.getResources().getColor(R.color.textMain));
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView mark = new TextView(host);
            mark.setText(index == checked ? "✓" : "");
            mark.setTextSize(16);
            mark.setTextColor(host.getResources().getColor(R.color.colorPrimary));
            row.addView(mark);

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    d.dismiss();
                    if (callback != null) callback.onChoose(index);
                }
            });
            list.addView(row);
        }

        ((Button) d.findViewById(R.id.dlgCancel)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });

        d.bindDismissOnOutside();
        d.present(R.id.dlgCard, false);
        return d;
    }


    public static class InfoContent {
        public String sub;
        public List<RemoteInfo.Row> rows = new ArrayList<RemoteInfo.Row>();
        public String actionText;
        public String actionUrl;
        public String error;
    }


    public interface InfoLoader {
        InfoContent load() throws Exception;
    }


    public static GlassDialog info(final Activity host, String title, final String loadingSub,
                                   final InfoLoader loader) {
        final GlassDialog d = new GlassDialog(host);
        d.setContentView(R.layout.dialog_glass_info);
        d.takeBackdrop();

        ((TextView) d.findViewById(R.id.dlgTitle)).setText(title);
        final TextView sub = (TextView) d.findViewById(R.id.dlgSub);
        final LinearLayout content = (LinearLayout) d.findViewById(R.id.dlgContent);
        final ScrollView scroll = (ScrollView) d.findViewById(R.id.dlgScroll);
        final Button close = (Button) d.findViewById(R.id.dlgClose);
        final Button action = (Button) d.findViewById(R.id.dlgAction);

        sub.setText(loadingSub == null ? host.getString(R.string.loading) : loadingSub);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        d.bindDismissOnOutside();
        d.present(R.id.dlgCard, false);

        final Runnable[] again = new Runnable[1];
        again[0] = new Runnable() {
            public void run() {
                if (!d.isShowing()) return;
                sub.setText(host.getString(R.string.loading));
                action.setVisibility(View.GONE);
                content.removeAllViews();
                new Thread(new Runnable() {
                    public void run() {
                        InfoContent ic;
                        try {
                            ic = loader.load();
                            if (ic == null) ic = new InfoContent();
                        } catch (Throwable t) {
                            ic = new InfoContent();
                            ic.error = String.valueOf(t.getMessage());
                        }
                        final InfoContent f = ic;
                        host.runOnUiThread(new Runnable() {
                            public void run() {
                                if (!d.isShowing()) return;
                                if (f.error != null) {
                                    sub.setText(host.getString(R.string.load_failed) + ": " + f.error);
                                    action.setText(host.getString(R.string.retry));
                                    action.setVisibility(View.VISIBLE);
                                    action.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            again[0].run();
                                        }
                                    });
                                    return;
                                }
                                sub.setText(f.sub == null ? "" : f.sub);
                                for (int i = 0; i < f.rows.size(); i++) {
                                    addInfoRow(host, content, f.rows.get(i));
                                }
                                if (f.actionUrl != null && f.actionUrl.length() > 0) {
                                    final String u = f.actionUrl;
                                    action.setText(f.actionText == null
                                            ? host.getString(R.string.open_link) : f.actionText);
                                    action.setVisibility(View.VISIBLE);
                                    action.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            openUrl(host, u);
                                        }
                                    });
                                }
                                capScroll(host, scroll, content);
                            }
                        });
                    }
                }, "remote-info").start();
            }
        };
        again[0].run();
        return d;
    }

    private static void addInfoRow(Activity host, LinearLayout parent, RemoteInfo.Row r) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.cell_bg);
        row.setPadding(dp(host, 13), dp(host, 10), dp(host, 13), dp(host, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(host, 8);
        row.setLayoutParams(lp);

        if (r.label != null && r.label.length() > 0) {
            TextView lab = new TextView(host);
            lab.setText(r.label);
            lab.setTextSize(11);
            lab.setTextColor(host.getResources().getColor(R.color.textSub));
            row.addView(lab);
        }

        TextView val = new TextView(host);
        val.setText(r.value == null ? "" : r.value);
        val.setTextSize(14);
        boolean link = r.url != null && r.url.length() > 0;
        val.setTextColor(host.getResources().getColor(link ? R.color.colorPrimary : R.color.textMain));
        if (link) {
            val.setPaintFlags(val.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            final String u = r.url;
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openUrl(host, u);
                }
            });

            TextView hint = new TextView(host);
            hint.setText(host.getString(R.string.open_in_browser));
            hint.setTextSize(11);
            hint.setTextColor(host.getResources().getColor(R.color.textSub));
            row.addView(hint);
        }
        row.addView(val);
        parent.addView(row);
    }

    static void openUrl(Activity host, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(host, host.getString(R.string.cannot_open) + ":\n" + url,
                    Toast.LENGTH_SHORT).show();
        }
    }


    private static void capScroll(final Activity host, final ScrollView scroll, final LinearLayout content) {
        scroll.post(new Runnable() {
            public void run() {
                try {
                    int max = (int) (host.getResources().getDisplayMetrics().heightPixels * 0.55f);
                    int h = content.getHeight();
                    ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                    int want = Math.max(1, Math.min(h, max));
                    if (lp.height != want) {
                        lp.height = want;
                        scroll.setLayoutParams(lp);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }


    public static class HeadlineContent {
        public String label;
        public String headline;
        public String body;
        public boolean bodyMarkdown;
        public String buttonText;
        public String actionUrl;
        public List<RemoteInfo.Row> extra = new ArrayList<RemoteInfo.Row>();
        public String error;
    }

    public interface HeadlineLoader {
        HeadlineContent load() throws Exception;
    }


    public static GlassDialog headline(final Activity host, final HeadlineLoader loader) {
        final GlassDialog d = new GlassDialog(host);
        d.setContentView(R.layout.dialog_glass_dev);
        d.takeBackdrop();

        final TextView label = (TextView) d.findViewById(R.id.dlgLabel);
        final TextView head = (TextView) d.findViewById(R.id.dlgHeadline);
        final TextView body = (TextView) d.findViewById(R.id.dlgBody);
        final ScrollView scroll = (ScrollView) d.findViewById(R.id.dlgScroll);
        final LinearLayout extra = (LinearLayout) d.findViewById(R.id.dlgExtra);
        final Button ok = (Button) d.findViewById(R.id.dlgOk);

        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        d.bindDismissOnOutside();
        d.present(R.id.dlgCard, false);

        final Runnable[] again = new Runnable[1];
        again[0] = new Runnable() {
            public void run() {
                if (!d.isShowing()) return;
                head.setText(host.getString(R.string.loading));
                body.setText("");
                body.setVisibility(View.GONE);
                scroll.setVisibility(View.GONE);
                extra.removeAllViews();
                ok.setText(host.getString(R.string.ok));
                ok.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        d.dismiss();
                    }
                });
                new Thread(new Runnable() {
                    public void run() {
                        HeadlineContent hc;
                        try {
                            hc = loader.load();
                            if (hc == null) hc = new HeadlineContent();
                        } catch (Throwable t) {
                            hc = new HeadlineContent();
                            hc.error = String.valueOf(t.getMessage());
                        }
                        final HeadlineContent f = hc;
                        host.runOnUiThread(new Runnable() {
                            public void run() {
                                if (!d.isShowing()) return;
                                if (f.error != null) {
                                    head.setText(host.getString(R.string.load_failed));
                                    body.setVisibility(View.VISIBLE);
                                    body.setText(f.error);
                                    ok.setText(host.getString(R.string.retry));
                                    ok.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            again[0].run();
                                        }
                                    });
                                    return;
                                }
                                if (f.label != null && f.label.length() > 0) {
                                    label.setText(f.label);
                                    label.setVisibility(View.VISIBLE);
                                } else {
                                    label.setVisibility(View.GONE);
                                }
                                head.setText(f.headline == null || f.headline.length() == 0
                                        ? "—" : f.headline);
                                if (f.body != null && f.body.length() > 0) {
                                    if (f.bodyMarkdown) Md.into(body, f.body);
                                    else body.setText(f.body);
                                    body.setVisibility(View.VISIBLE);
                                }
                                if (!f.extra.isEmpty()) {
                                    scroll.setVisibility(View.VISIBLE);
                                    for (int i = 0; i < f.extra.size(); i++) {
                                        addInfoRow(host, extra, f.extra.get(i));
                                    }
                                }
                                ok.setText(f.buttonText == null || f.buttonText.length() == 0
                                        ? host.getString(R.string.ok) : f.buttonText);
                                if (f.actionUrl != null && f.actionUrl.length() > 0) {
                                    final String u = f.actionUrl;
                                    ok.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            openUrl(host, u);
                                            d.dismiss();
                                        }
                                    });
                                }
                                capScroll(host, scroll, extra);
                            }
                        });
                    }
                }, "remote-dev").start();
            }
        };
        again[0].run();
        return d;
    }


    private void bindDismissOnOutside() {
        View root = findViewById(R.id.dlgRoot);
        if (root != null) {
            root.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dismiss();
                }
            });
        }
        View card = findViewById(R.id.dlgCard);
        if (card != null) {
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {

                }
            });
        }
        setCanceledOnTouchOutside(true);
    }


    private void takeBackdrop() {
        try {
            View decor = host.getWindow().getDecorView();
            int w = decor.getWidth();
            int h = decor.getHeight();
            if (w <= 0 || h <= 0) return;
            float scale = 1f / 3f;
            Bitmap shot = LiquidGlass.capture(decor, 0, 0, w, h, scale);
            backdrop = LiquidGlass.frost(shot, LiquidGlass.SIGMA_DIALOG);
        } catch (Throwable ignored) {
        }
    }

    private void present(int cardId, final boolean focusInput) {
        present(cardId, focusInput, 0f);
    }

    private void present(int cardId, final boolean focusInput, float heightFraction) {
        show();
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (backdrop != null) {
                window.setBackgroundDrawable(new BitmapDrawable(host.getResources(), backdrop));
            } else {
                window.setBackgroundDrawable(new ColorDrawable(0x99000000));
            }
            int flags = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            if (focusInput) flags |= WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
            window.setSoftInputMode(flags);
        }
        if (heightFraction > 0f) {
            View cardView = findViewById(cardId);
            if (cardView != null) {
                int h = (int) (host.getResources().getDisplayMetrics().heightPixels * heightFraction);
                ViewGroup.LayoutParams lp = cardView.getLayoutParams();
                lp.height = h;
                cardView.setLayoutParams(lp);
            }
        }
        View card = findViewById(cardId);
        if (card != null) {
            card.setAlpha(0f);
            card.setScaleX(0.92f);
            card.setScaleY(0.92f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        }
    }


    public interface AgreeAction {
        void onAgree();

        void onReject();
    }

    public static class MdContent {
        public String title;
        public String sub;
        public String markdown;
        public String error;
    }

    public interface MdLoader {
        MdContent load() throws Exception;
    }


    public static GlassDialog agreement(final Activity host, String defaultTitle,
                                        final MdLoader loader, final AgreeAction action) {
        final GlassDialog d = new GlassDialog(host);
        d.setContentView(R.layout.dialog_glass_agree);
        d.takeBackdrop();

        final TextView title = (TextView) d.findViewById(R.id.dlgTitle);
        final TextView sub = (TextView) d.findViewById(R.id.dlgSub);
        final TextView body = (TextView) d.findViewById(R.id.dlgBody);
        final Button agree = (Button) d.findViewById(R.id.dlgAgree);
        final Button reject = (Button) d.findViewById(R.id.dlgReject);

        title.setText(defaultTitle == null ? "" : defaultTitle);
        sub.setText(host.getString(R.string.loading));

        agree.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                if (action != null) action.onAgree();
            }
        });
        reject.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                if (action != null) action.onReject();
            }
        });

        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);
        d.present(R.id.dlgCard, false, 0.80f);

        final Runnable[] again = new Runnable[1];
        again[0] = new Runnable() {
            public void run() {
                if (!d.isShowing()) return;
                sub.setText(host.getString(R.string.loading));
                body.setOnClickListener(null);
                body.setText("");
                new Thread(new Runnable() {
                    public void run() {
                        MdContent mc;
                        try {
                            mc = loader.load();
                            if (mc == null) mc = new MdContent();
                        } catch (Throwable t) {
                            mc = new MdContent();
                            mc.error = String.valueOf(t.getMessage());
                        }
                        final MdContent f = mc;
                        host.runOnUiThread(new Runnable() {
                            public void run() {
                                if (!d.isShowing()) return;
                                if (f.title != null && f.title.length() > 0) title.setText(f.title);
                                if (f.error != null) {
                                    sub.setText(host.getString(R.string.load_failed));
                                    body.setText(host.getString(R.string.load_failed) + ": " + f.error
                                        + "\n\n(" + host.getString(R.string.retry) + ")");
                                    body.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            again[0].run();
                                        }
                                    });
                                    return;
                                }
                                sub.setText(f.sub == null ? "" : f.sub);
                                Md.into(body, f.markdown);
                            }
                        });
                    }
                }, "guide").start();
            }
        };
        again[0].run();
        return d;
    }

    private int dp(int v) {
        return (int) (v * host.getResources().getDisplayMetrics().density + 0.5f);
    }
}
