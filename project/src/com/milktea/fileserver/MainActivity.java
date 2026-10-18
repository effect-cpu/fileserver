package com.milktea.fileserver;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_TREE = 1001;
    private static final int REQ_NOTIFY = 1002;
    private static final int MAX_LOG_CHARS = 60000;

    private Prefs prefs;
    private DocStore store;
    private Handler ui;

    private final StringBuilder logBuffer = new StringBuilder();

    private FrameLayout content;
    private FrameLayout tabBar;
    private ImageView tabBarBlur;
    private View tabIndicator;
    private View item1, item2, item3;
    private ImageView iv1, iv2, iv3;

    private View tabService, tabLog, tabSettings;
    private TextView tab1, tab2, tab3;
    private TextView tvDir, tvFileCount, tvLog, tvIpv4, tvIpv6, tvBanEmpty, tvQrHint;
    private TextView tvPortValue, tvHttpsHint, segSystem, segLight, segDark;
    private TextView tvCurVersion, tvUpdateBadge;
    private View rowDev, rowUpdate, rowAgreement, rowLang, rowRepo;
    private TextView tvLangValue;
    private FrameLayout segTrack;
    private View segThumb;
    private LinearLayout fileList, banList, cardAddr, rowIpv4, rowIpv6;
    private Button btnPick, btnStart, btnClearLog, btnSetPort;
    private ImageButton btnQr, btnAddBan;
    private ImageView imgQr;
    private ScrollView logScroll;
    private Switch swIpv6, swBrowse, swAnti, swCaptcha, swHttps;

    private boolean qrShown = false;
    private String curUrl4 = "";
    private String curUrl6 = "";
    private int currentTab = 0;
    private boolean blurPending = false;
    private boolean panelDark = false;
    private boolean tintApplied = false;

    private final Logs.Listener logListener = new Logs.Listener() {
        public void onLog(final String line) {
            ui.post(new Runnable() {
                public void run() {
                    appendLog(line);
                }
            });
        }
    };


    @Override
    protected void attachBaseContext(Context base) {
        Prefs p = new Prefs(base);
        super.attachBaseContext(wrapLocale(wrapTheme(base, p.themeMode()), p.lang()));
    }


    private static Context wrapLocale(Context base, String lang) {
        if (base == null || lang == null || lang.length() == 0) return base;
        try {
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.setLocale(locale);
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            return base;
        }
    }

    private static Context wrapTheme(Context base, int mode) {
        if (mode == 0 || base == null) return base;
        try {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            int night = (mode == 2)
                    ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            return base;
        }
    }

    private boolean isDarkNow() {
        int mode = prefs.themeMode();
        if (mode == 1) return false;
        if (mode == 2) return true;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Handler(Looper.getMainLooper());
        prefs = new Prefs(this);
        setContentView(R.layout.activity_main);

        bindViews();
        setupTabs();
        setupSettings();
        setupServiceTab();
        setupLogTab();
        setupBlur();
        restoreState();
        requestNotifyPermission();
        Logs.add(getString(R.string.blur_backend, LiquidGlass.blurBackend()));
        if (prefs.agreed()) {
            autoCheckUpdate();
        } else {
            ui.postDelayed(new Runnable() {
                public void run() {
                    showAgreement();
                }
            }, 300);
        }
    }

    @Override
    protected void onDestroy() {
        Logs.removeListener(logListener);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) scheduleBlur();
    }

    private void bindViews() {
        content = (FrameLayout) findViewById(R.id.content);
        tabBar = (FrameLayout) findViewById(R.id.tabBar);
        tabBarBlur = (ImageView) findViewById(R.id.tabBarBlur);
        tabIndicator = findViewById(R.id.tabIndicator);
        item1 = findViewById(R.id.item1);
        item2 = findViewById(R.id.item2);
        item3 = findViewById(R.id.item3);
        iv1 = (ImageView) findViewById(R.id.iv1);
        iv2 = (ImageView) findViewById(R.id.iv2);
        iv3 = (ImageView) findViewById(R.id.iv3);

        tabService = findViewById(R.id.tabService);
        tabLog = findViewById(R.id.tabLog);
        tabSettings = findViewById(R.id.tabSettings);
        tab1 = (TextView) findViewById(R.id.tab1);
        tab2 = (TextView) findViewById(R.id.tab2);
        tab3 = (TextView) findViewById(R.id.tab3);

        tvDir = (TextView) findViewById(R.id.tvDir);
        tvFileCount = (TextView) findViewById(R.id.tvFileCount);
        tvLog = (TextView) findViewById(R.id.tvLog);
        tvIpv4 = (TextView) findViewById(R.id.tvIpv4);
        tvIpv6 = (TextView) findViewById(R.id.tvIpv6);
        tvBanEmpty = (TextView) findViewById(R.id.tvBanEmpty);
        tvQrHint = (TextView) findViewById(R.id.tvQrHint);
        tvPortValue = (TextView) findViewById(R.id.tvPortValue);
        tvHttpsHint = (TextView) findViewById(R.id.tvHttpsHint);
        segSystem = (TextView) findViewById(R.id.segSystem);
        segLight = (TextView) findViewById(R.id.segLight);
        segDark = (TextView) findViewById(R.id.segDark);
        segTrack = (FrameLayout) findViewById(R.id.segTrack);
        segThumb = findViewById(R.id.segThumb);
        tvCurVersion = (TextView) findViewById(R.id.tvCurVersion);
        tvUpdateBadge = (TextView) findViewById(R.id.tvUpdateBadge);
        rowDev = findViewById(R.id.rowDev);
        rowUpdate = findViewById(R.id.rowUpdate);
        rowAgreement = findViewById(R.id.rowAgreement);
        rowLang = findViewById(R.id.rowLang);
        rowRepo = findViewById(R.id.rowRepo);
        tvLangValue = (TextView) findViewById(R.id.tvLangValue);

        fileList = (LinearLayout) findViewById(R.id.fileList);
        banList = (LinearLayout) findViewById(R.id.banList);
        cardAddr = (LinearLayout) findViewById(R.id.cardAddr);
        rowIpv4 = (LinearLayout) findViewById(R.id.rowIpv4);
        rowIpv6 = (LinearLayout) findViewById(R.id.rowIpv6);

        btnPick = (Button) findViewById(R.id.btnPick);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnClearLog = (Button) findViewById(R.id.btnClearLog);
        btnSetPort = (Button) findViewById(R.id.btnSetPort);
        btnQr = (ImageButton) findViewById(R.id.btnQr);
        btnAddBan = (ImageButton) findViewById(R.id.btnAddBan);
        imgQr = (ImageView) findViewById(R.id.imgQr);
        logScroll = (ScrollView) findViewById(R.id.logScroll);

        swIpv6 = (Switch) findViewById(R.id.swIpv6);
        swBrowse = (Switch) findViewById(R.id.swBrowse);
        swAnti = (Switch) findViewById(R.id.swAnti);
        swCaptcha = (Switch) findViewById(R.id.swCaptcha);
        swHttps = (Switch) findViewById(R.id.swHttps);
    }


    private void setupBlur() {
        tabBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (tabBar.getHeight() > 0) layoutIndicator(false);
                scheduleBlur();
            }
        });
        content.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {
                    public void onScrollChanged() {
                        scheduleBlur();
                    }
                });
        scheduleBlur();
    }

    private void scheduleBlur() {
        if (blurPending) return;
        blurPending = true;
        ui.postDelayed(new Runnable() {
            public void run() {
                blurPending = false;
                updateBlur();
            }
        }, 60);
    }


    private void updateBlur() {
        try {
            int bw = tabBar.getWidth();
            int bh = tabBar.getHeight();
            int cw = content.getWidth();
            int ch = content.getHeight();
            if (bw <= 0 || bh <= 0 || cw <= 0 || ch <= 0) return;

            int[] barLoc = new int[2];
            int[] contentLoc = new int[2];
            tabBar.getLocationInWindow(barLoc);
            content.getLocationInWindow(contentLoc);
            int ox = barLoc[0] - contentLoc[0];
            int oy = barLoc[1] - contentLoc[1];
            if (ox < 0 || oy < 0 || ox + bw > cw || oy + bh > ch) return;

            Bitmap shot = LiquidGlass.capture(content, ox, oy, bw, bh, 1f);
            Bitmap frosted = LiquidGlass.frost(shot, LiquidGlass.SIGMA_BAR);
            if (frosted == null) return;
            tabBarBlur.setImageBitmap(frosted);
            applyPanelTint(avgLuminance(frosted));
        } catch (Throwable ignored) {
        }
    }


    private static float avgLuminance(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return 1f;
        try {
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int stepX = Math.max(1, w / 40);
            int stepY = Math.max(1, h / 20);
            long sum = 0;
            int n = 0;
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int p = bmp.getPixel(x, y);
                    int r = (p >> 16) & 0xFF;
                    int g = (p >> 8) & 0xFF;
                    int b = p & 0xFF;
                    sum += (long) (0.2126 * r + 0.7152 * g + 0.0722 * b);
                    n++;
                }
            }
            return n == 0 ? 1f : (sum / (float) n) / 255f;
        } catch (Throwable t) {
            return 1f;
        }
    }


    private void applyPanelTint(float luminance) {
        boolean dark = isDarkNow() || luminance < 0.55f;
        if (tintApplied && dark == panelDark) return;
        panelDark = dark;
        tintApplied = true;
        int top, bottom, stroke;
        if (dark) {
            top = 0xF0243041;
            bottom = 0xD118212E;
            stroke = 0x47FFFFFF;
        } else {
            top = 0xF0FFFFFF;
            bottom = 0xB8FFFFFF;
            stroke = 0xB3FFFFFF;
        }
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(Math.max(tabBar.getHeight(), 1));
        d.setStroke(dp(1), stroke);
        tabBar.setBackground(d);
    }


    private void setupTabs() {
        item1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(0);
            }
        });
        item2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(1);
            }
        });
        item3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(2);
            }
        });
        selectTab(0);
    }

    private void selectTab(int index) {
        boolean animate = indicatorPlaced && index != currentTab;
        currentTab = index;

        View[] tabs = new View[]{tabService, tabLog, tabSettings};
        for (int i = 0; i < tabs.length; i++) {
            View t = tabs[i];
            if (i == index) {
                if (t.getVisibility() != View.VISIBLE) Anim.fadeInUp(t, 10f, 220L);
            } else if (t.getVisibility() == View.VISIBLE) {
                t.animate().cancel();
                t.setAlpha(1f);
                t.setTranslationY(0f);
                t.setVisibility(View.GONE);
            }
        }

        tabService.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabLog.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        tabService.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabLog.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        int active = getResources().getColor(R.color.colorPrimary);
        int idle = getResources().getColor(R.color.tabIdle);
        tab1.setTextColor(index == 0 ? active : idle);
        tab2.setTextColor(index == 1 ? active : idle);
        tab3.setTextColor(index == 2 ? active : idle);
        tab1.setTypeface(null, index == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tab2.setTypeface(null, index == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tab3.setTypeface(null, index == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        iv1.setColorFilter(index == 0 ? active : idle);
        iv2.setColorFilter(index == 1 ? active : idle);
        iv3.setColorFilter(index == 2 ? active : idle);

        if (index == 1) scrollLogToEnd();
        layoutIndicator(animate);
        indicatorPlaced = true;
        scheduleBlur();
    }


    private boolean indicatorPlaced = false;


    private void layoutIndicator(boolean animate) {
        try {
            int w = tabBar.getWidth();
            int h = tabBar.getHeight();
            if (w <= 0 || h <= 0) return;
            int itemW = w / 3;
            int padX = dp(7);
            int padY = dp(7);
            int iw = Math.max(1, itemW - padX * 2);

            int ih = Math.max(1, Math.min(h - padY * 2, dp(56)));
            ViewGroup.LayoutParams lp = tabIndicator.getLayoutParams();
            if (lp.width != iw || lp.height != ih) {
                lp.width = iw;
                lp.height = ih;
                tabIndicator.setLayoutParams(lp);
            }
            tabIndicator.setTranslationY(padY);
            float target = currentTab * itemW + padX;
            tabIndicator.animate().cancel();
            if (!animate) {
                tabIndicator.setScaleX(1f);
                tabIndicator.setTranslationX(target);
                return;
            }
            tabIndicator.setScaleX(1.08f);
            tabIndicator.animate()
                    .translationX(target)
                    .scaleX(1f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } catch (Throwable ignored) {
        }
    }


    private void setupSettings() {
        swIpv6.setChecked(prefs.showIpv6());
        swBrowse.setChecked(prefs.allowBrowse());
        swAnti.setChecked(prefs.antiCrawler());
        swCaptcha.setChecked(prefs.captcha());
        swHttps.setChecked(prefs.https());

        swIpv6.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setShowIpv6(checked);
                applyIpv6Visibility();
                if (qrShown) renderQr();
                scheduleBlur();
            }
        });
        swBrowse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setAllowBrowse(checked);
            }
        });
        swAnti.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setAntiCrawler(checked);
                toast(checked ? getString(R.string.anti_on) : getString(R.string.anti_off));
            }
        });
        swCaptcha.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setCaptcha(checked);
                toast(checked ? getString(R.string.captcha_on) : getString(R.string.captcha_off));
            }
        });
        swHttps.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setHttps(checked);
                toast(checked ? getString(R.string.https_on) : getString(R.string.https_off));
                if (ServerHub.isRunning()) {
                    toast(getString(R.string.restart_proto));
                    restartServer();
                }
                scheduleBlur();
            }
        });


        View.OnClickListener themeClick = new View.OnClickListener() {
            public void onClick(View v) {
                int mode = v == segSystem ? 0 : (v == segLight ? 1 : 2);
                applyThemeMode(mode);
            }
        };
        segSystem.setOnClickListener(themeClick);
        segLight.setOnClickListener(themeClick);
        segDark.setOnClickListener(themeClick);
        segTrack.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (segTrack.getWidth() > 0) updateThemeSegments(false);
            }
        });
        updateThemeSegments(false);


        tvPortValue.setText(String.valueOf(prefs.port()));
        btnSetPort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editPort();
            }
        });
        updateHttpsHint();

        btnAddBan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addBanDialog();
            }
        });
        refreshBanList();


        rowDev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDevDialog();
            }
        });
        rowUpdate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showUpdateDialog();
            }
        });
        rowAgreement.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showGuide();
            }
        });
        rowLang.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showLangDialog();
            }
        });
        rowRepo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                GlassDialog.openUrl(MainActivity.this, URL_REPO);
            }
        });
        tvLangValue.setText(langLabel());
        tvCurVersion.setText(getString(R.string.cur_version_fmt,
                appVersionName(), String.valueOf(appVersionCode())));
    }


    private static final String URL_VERSION =
            "https://raw.githubusercontent.com/effect-cpu/fileserver/main/version2.json";
    private static final String URL_GUIDE =
            "https://raw.githubusercontent.com/effect-cpu/fileserver/main/useanduser.md";

    private static final String URL_REPO =
            "https://github.com/effect-cpu/fileserver";


    private static class Guide {
        String markdown;
        String sub;
    }

    private byte[] readAsset(String name) throws Exception {
        InputStream in = null;
        try {
            in = getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }


    private boolean isEnglishUi() {
        try {
            Locale l = getResources().getConfiguration().getLocales().get(0);
            return "en".equalsIgnoreCase(l.getLanguage());
        } catch (Throwable t) {
            return false;
        }
    }


    private Guide loadGuide() throws Exception {
        Guide g = new Guide();
        String[] names = isEnglishUi()
                ? new String[]{"useanduser.en.md", "useanduser.md"}
                : new String[]{"useanduser.md", "useanduser.en.md"};
        for (int i = 0; i < names.length; i++) {
            try {
                byte[] raw = readAsset(names[i]);
                g.markdown = new String(raw, "UTF-8");
                g.sub = getString(R.string.using_bundled);
                Logs.add(getString(R.string.log_guide_asset));
                return g;
            } catch (Throwable ignored) {
            }
        }
        g.markdown = Net.get(URL_GUIDE, 15000);
        g.sub = getString(R.string.from_cloud);
        return g;
    }


    private void showGuide() {
        GlassDialog.headline(this, new GlassDialog.HeadlineLoader() {
            public GlassDialog.HeadlineContent load() throws Exception {
                Guide g = loadGuide();
                GlassDialog.HeadlineContent h = new GlassDialog.HeadlineContent();
                h.label = getString(R.string.guide_label, g.sub);
                h.headline = getString(R.string.guide_title);
                h.body = g.markdown;
                h.bodyMarkdown = true;
                h.buttonText = getString(R.string.close);
                return h;
            }
        });
    }


    private static boolean updateChecked = false;

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0";
        }
    }

    private long appVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }


    private void showDevDialog() {
        GlassDialog.headline(this, new GlassDialog.HeadlineLoader() {
            public GlassDialog.HeadlineContent load() {
                GlassDialog.HeadlineContent h = new GlassDialog.HeadlineContent();
                h.label = getString(R.string.about_dev);
                h.headline = "effect-cpu";
                h.body = getString(R.string.dev_desc);
                h.buttonText = getString(R.string.close);
                return h;
            }
        });
    }


    private void showUpdateDialog() {
        GlassDialog.headline(this, new GlassDialog.HeadlineLoader() {
            public GlassDialog.HeadlineContent load() throws Exception {
                String json = Net.get(URL_VERSION, 10000);
                RemoteInfo.Version v = RemoteInfo.version(MiniJson.parseObject(json));
                String local = appVersionName();
                boolean newer = RemoteInfo.isNewer(v, local, appVersionCode());

                GlassDialog.HeadlineContent h = new GlassDialog.HeadlineContent();
                h.label = getString(R.string.update_label, local);
                h.headline = newer
                        ? getString(R.string.found_new, v.versionName == null ? "" : v.versionName)
                        : getString(R.string.latest);
                h.body = v.content;
                h.bodyMarkdown = true;
                h.extra = v.extra;
                h.buttonText = newer ? getString(R.string.update_now) : getString(R.string.open_release);
                h.actionUrl = v.url;
                return h;
            }
        });
    }


    private void showAgreement() {
        GlassDialog.agreement(this, getString(R.string.guide_title), new GlassDialog.MdLoader() {
            public GlassDialog.MdContent load() throws Exception {
                Guide g = loadGuide();
                GlassDialog.MdContent c = new GlassDialog.MdContent();
                c.title = getString(R.string.guide_title);
                c.sub = g.sub;
                c.markdown = g.markdown;
                return c;
            }
        }, new GlassDialog.AgreeAction() {
            public void onAgree() {
                prefs.setAgreed(true);
                Logs.add(getString(R.string.log_agreed));
                toast(getString(R.string.thanks));
                autoCheckUpdate();
            }

            public void onReject() {
                Logs.add(getString(R.string.log_disagreed));
                finishAffinity();
            }
        });
    }

    private static final String[] LANGS = new String[]{"", "zh", "en"};

    private String langLabel() {
        String l = prefs.lang();
        if ("zh".equals(l)) return getString(R.string.lang_zh);
        if ("en".equals(l)) return getString(R.string.lang_en);
        return getString(R.string.lang_system);
    }

    private void showLangDialog() {
        String[] items = new String[]{
                getString(R.string.lang_system),
                getString(R.string.lang_zh),
                getString(R.string.lang_en)};
        int cur = 0;
        for (int i = 0; i < LANGS.length; i++) {
            if (LANGS[i].equals(prefs.lang())) cur = i;
        }
        GlassDialog.choose(this, getString(R.string.about_lang), items, cur,
                new GlassDialog.OnChoose() {
                    public void onChoose(int index) {
                        if (index < 0 || index >= LANGS.length) return;
                        String v = LANGS[index];
                        if (v.equals(prefs.lang())) return;
                        prefs.setLang(v);
                        recreate();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                });
    }


    private void autoCheckUpdate() {
        if (updateChecked) return;
        updateChecked = true;
        new Thread(new Runnable() {
            public void run() {
                String note;
                try {
                    String json = Net.get(URL_VERSION, 8000);
                    RemoteInfo.Version v = RemoteInfo.version(MiniJson.parseObject(json));
                    final String local = appVersionName();
                    boolean newer = RemoteInfo.isNewer(v, local, appVersionCode());
                    if (newer) {
                        final String remote = v.versionName;
                        note = getString(R.string.log_update_check, remote, local);
                        ui.post(new Runnable() {
                            public void run() {
                                tvUpdateBadge.setVisibility(View.VISIBLE);
                                toast(getString(R.string.found_new, remote));
                                showUpdateDialog();
                            }
                        });
                    } else {
                        note = getString(R.string.log_update_latest, local);
                        ui.post(new Runnable() {
                            public void run() {
                                tvUpdateBadge.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (Throwable t) {
                    note = getString(R.string.log_update_failed, String.valueOf(t.getMessage()));
                }
                Logs.add(note);
            }
        }, "update-check").start();
    }


    private void updateThemeSegments(boolean animate) {
        final int mode = prefs.themeMode();
        TextView[] segs = new TextView[]{segSystem, segLight, segDark};

        int w = segTrack.getWidth();
        int h = segTrack.getHeight();
        if (w > 0 && h > 0) {
            int inset = dp(4);
            int slot = w / 3;
            int tw = Math.max(1, slot - inset * 2);
            int th = Math.max(1, h - inset * 2);
            ViewGroup.LayoutParams lp = segThumb.getLayoutParams();
            if (lp.width != tw || lp.height != th) {
                lp.width = tw;
                lp.height = th;
                segThumb.setLayoutParams(lp);
            }
            segThumb.setTranslationY(inset);
            float target = mode * slot + inset;
            segThumb.animate().cancel();
            if (animate) {
                segThumb.animate().translationX(target)
                        .setDuration(240)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                segThumb.setTranslationX(target);
            }
        }

        int idle = getResources().getColor(R.color.textMain);
        for (int i = 0; i < segs.length; i++) {
            boolean on = (i == mode);
            segs[i].setTextColor(on ? Color.WHITE : idle);
            segs[i].setTypeface(null, on ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
        }
    }

    private void applyThemeMode(int mode) {
        if (prefs.themeMode() == mode) return;
        prefs.setThemeMode(mode);
        updateThemeSegments(true);

        ui.postDelayed(new Runnable() {
            public void run() {
                recreate();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, 200);
    }

    private void updateHttpsHint() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.https_tip));
        sb.append('\n').append(getString(R.string.blur_backend, LiquidGlass.blurBackend()));
        sb.append(" · ").append(getString(isDarkNow()
                ? R.string.theme_dark : R.string.theme_light));
        tvHttpsHint.setText(sb.toString());
    }

    private void editPort() {
        GlassDialog.input(this, getString(R.string.port_dialog_title),
                getString(R.string.port_label_range), getString(R.string.port_hint),
                String.valueOf(prefs.port()), true, new GlassDialog.OnText() {
                    public void onText(String text) {
                        String s = text == null ? "" : text.trim();
                        int p;
                        try {
                            p = Integer.parseInt(s);
                        } catch (Throwable t) {
                            toast(getString(R.string.port_invalid));
                            return;
                        }
                        if (p < 1024 || p > 65535) {
                            toast(getString(R.string.port_range));
                            return;
                        }
                        prefs.setPort(p);
                        tvPortValue.setText(String.valueOf(p));
                        if (ServerHub.isRunning()) {
                            toast(getString(R.string.restart_port));
                            restartServer();
                        }
                    }
                });
    }

    private void applyIpv6Visibility() {
        rowIpv6.setVisibility(prefs.showIpv6() ? View.VISIBLE : View.GONE);
    }


    private void setupServiceTab() {
        btnPick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickFolder();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ServerHub.isRunning()) stopServer();
                else startServer();
            }
        });

        btnQr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                qrShown = !qrShown;
                if (qrShown) {
                    renderQr();
                    Anim.popIn(imgQr, 260L);
                    Anim.fadeInUp(tvQrHint, 6f, 260L);
                } else {
                    Anim.popOut(imgQr, 170L);
                    tvQrHint.setVisibility(View.GONE);
                }
                scheduleBlur();
            }
        });

        rowIpv4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copy("IPv4", curUrl4);
            }
        });
        rowIpv6.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copy("IPv6", curUrl6);
            }
        });
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_TREE);
        } catch (Throwable t) {
            toast(getString(R.string.no_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        prefs.setTreeUri(uri.toString());
        bindStore();
        refreshFileList();
        toast(getString(R.string.dir_selected));
    }

    private void bindStore() {
        String s = prefs.treeUri();
        if (TextUtils.isEmpty(s)) {
            store = null;
            tvDir.setText(R.string.no_dir);
            return;
        }
        try {
            store = new DocStore(getContentResolver(), Uri.parse(s));
            String name = store.loadRootName();
            tvDir.setText(getString(R.string.dir_current, name + "\n" + s));
        } catch (Throwable t) {
            store = null;
            tvDir.setText(R.string.dir_unavailable);
        }
    }

    private void refreshFileList() {
        if (store == null) {
            fileList.removeAllViews();
            tvFileCount.setText("");
            return;
        }
        tvFileCount.setText(R.string.reading);
        fileList.removeAllViews();

        new Thread(new Runnable() {
            public void run() {
                LoadResult r;
                try {
                    r = new LoadResult(store.children(store.getRootId()), null);
                } catch (Throwable t) {
                    r = new LoadResult(null, String.valueOf(t.getMessage()));
                }
                final LoadResult fr = r;
                ui.post(new Runnable() {
                    public void run() {
                        if (fr.error != null) {
                            tvFileCount.setText(getString(R.string.read_failed, fr.error));
                            scheduleBlur();
                            return;
                        }
                        tvFileCount.setText(getString(R.string.item_count, fr.list.size()));
                        for (int i = 0; i < fr.list.size(); i++) addFileRow(fr.list.get(i));
                        scheduleBlur();
                    }
                });
            }
        }, "fs-list").start();
    }

    private void addFileRow(final DocStore.Doc d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView icon = new TextView(this);
        icon.setText(d.dir ? "📁" : "📄");
        icon.setTextSize(15);
        row.addView(icon);

        TextView name = new TextView(this);
        name.setText(d.name);
        name.setTextSize(14);
        name.setTextColor(getResources().getColor(R.color.textMain));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(8);
        name.setLayoutParams(nlp);
        row.addView(name);

        TextView meta = new TextView(this);
        meta.setText(d.dir ? getString(R.string.folder_word) : human(d.size));
        meta.setTextSize(12);
        meta.setTextColor(getResources().getColor(R.color.textSub));
        row.addView(meta);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toast(d.dir ? getString(R.string.folder_named, d.name)
                        : getString(R.string.file_named, d.name, human(d.size)));
            }
        });
        fileList.addView(row);
    }


    private void startServer() {
        if (store == null) {
            toast(getString(R.string.need_folder));
            return;
        }
        final int port = prefs.port();
        btnStart.setEnabled(false);
        btnStart.setText(R.string.starting);

        new Thread(new Runnable() {
            public void run() {
                String error = null;
                try {
                    ServerHub.start(store, prefs, port);
                } catch (Throwable t) {
                    error = String.valueOf(t.getMessage());
                }
                final String ferror = error;
                ui.post(new Runnable() {
                    public void run() {
                        btnStart.setEnabled(true);
                        if (ferror != null) {
                            toast(getString(R.string.start_failed, ferror));
                            btnStart.setText(R.string.start);
                            Logs.add(getString(R.string.start_failed, ferror));
                            return;
                        }
                        onServerRunning(true, true);
                        startHttpService();
                    }
                });
            }
        }, "fs-start").start();
    }

    private void restartServer() {
        ServerHub.stop();
        ui.postDelayed(new Runnable() {
            public void run() {
                startServer();
            }
        }, 250);
    }

    private void stopServer() {
        ServerHub.stop();
        try {
            stopService(new Intent(this, HttpService.class));
        } catch (Throwable ignored) {
        }
        onServerRunning(false, true);
    }

    private void onServerRunning(boolean running, boolean animate) {
        if (running) {
            btnStart.setText(R.string.stop);
            btnStart.setBackgroundResource(R.drawable.liquid_btn_red);
            refreshAddresses();
            if (animate) {
                Anim.expand(cardAddr, Anim.DUR_EXPAND);
            } else {
                ViewGroup.LayoutParams lp = cardAddr.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                cardAddr.setLayoutParams(lp);
                cardAddr.setAlpha(1f);
                cardAddr.setScaleY(1f);
                cardAddr.setTranslationY(0f);
                cardAddr.setVisibility(View.VISIBLE);
            }
        } else {
            if (animate) {
                Anim.collapse(cardAddr, Anim.DUR_COLLAPSE);
            } else {
                cardAddr.setVisibility(View.GONE);
            }
            imgQr.setVisibility(View.GONE);
            tvQrHint.setVisibility(View.GONE);
            qrShown = false;
            btnStart.setText(R.string.start);
            btnStart.setBackgroundResource(R.drawable.liquid_btn_teal);
        }
        updateHttpsHint();
        scheduleBlur();
    }

    private String scheme() {
        return prefs.https() ? "https://" : "http://";
    }

    private void refreshAddresses() {
        int port = prefs.port();
        String v4 = NetUtil.pickIpv4();
        String v6 = NetUtil.pickIpv6();

        curUrl4 = v4 == null ? "" : scheme() + v4 + ":" + port;
        curUrl6 = v6 == null ? "" : scheme() + "[" + v6 + "]:" + port;

        tvIpv4.setText(v4 == null ? getString(R.string.no_ipv4) : curUrl4);
        tvIpv6.setText(v6 == null ? getString(R.string.no_ipv6) : curUrl6);
        applyIpv6Visibility();

        if (qrShown) renderQr();
        scheduleBlur();
    }

    private void renderQr() {
        String url = !TextUtils.isEmpty(curUrl4) ? curUrl4 : curUrl6;
        if (TextUtils.isEmpty(url)) {
            imgQr.setImageDrawable(null);
            return;
        }
        try {
            QrCode qr = QrCode.encodeText(url, QrCode.ECC_M);
            int quiet = 4;
            int base = qr.size + quiet * 2;
            float targetPx = 240f * getResources().getDisplayMetrics().density;
            int scale = Math.max(2, (int) (targetPx / base));
            int dim = qr.bitmapDimension(scale, quiet);
            int[] argb = qr.toArgb(scale, quiet, Color.BLACK, Color.WHITE);
            Bitmap bmp = Bitmap.createBitmap(argb, dim, dim, Bitmap.Config.ARGB_8888);
            imgQr.setImageBitmap(bmp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dim, dim);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(12);
            imgQr.setLayoutParams(lp);
            tvQrHint.setText(getString(R.string.qr_hint, url));
        } catch (Throwable t) {
            toast(getString(R.string.qr_failed));
        }
    }

    private void startHttpService() {
        try {
            Intent i = new Intent(this, HttpService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Throwable ignored) {
        }
    }


    private void refreshBanList() {
        List<String> list = prefs.banned();
        banList.removeAllViews();
        tvBanEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < list.size(); i++) {
            final String ip = list.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.cell_bg);
            row.setPadding(dp(12), dp(6), dp(4), dp(6));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(6);
            row.setLayoutParams(rlp);

            TextView tv = new TextView(this);
            tv.setText(ip);
            tv.setTextSize(14);
            tv.setTextColor(getResources().getColor(R.color.textMain));
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            ImageButton del = new ImageButton(this);
            del.setImageResource(android.R.drawable.ic_menu_delete);
            del.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            del.setBackgroundResource(R.drawable.glass_icon);
            del.setPadding(dp(8), dp(8), dp(8), dp(8));
            del.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    removeBan(ip);
                }
            });
            row.addView(del);

            banList.addView(row);
        }
        scheduleBlur();
    }

    private void removeBan(String ip) {
        List<String> list = prefs.banned();
        list.remove(ip);
        prefs.setBanned(list);
        refreshBanList();
        toast(getString(R.string.ban_removed, ip));
    }

    private void addBanDialog() {
        GlassDialog.input(this, getString(R.string.ban_dialog_title),
                getString(R.string.ban_dialog_msg),
                getString(R.string.ban_dialog_hint), "", false, new GlassDialog.OnText() {
                    public void onText(String text) {
                        final String v = text == null ? "" : text.trim();
                        if (v.length() == 0) {
                            toast(getString(R.string.ban_need));
                            return;
                        }
                        List<String> list = prefs.banned();
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).equals(v)) {
                                toast(getString(R.string.ban_exists));
                                return;
                            }
                        }
                        list.add(v);
                        prefs.setBanned(list);
                        refreshBanList();
                        toast(getString(R.string.banned, v));
                    }
                });
    }


    private void setupLogTab() {
        btnClearLog.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Logs.clear();
                logBuffer.setLength(0);
                tvLog.setText("");
            }
        });
        Logs.addListener(logListener);

        List<String> history = Logs.snapshot();
        for (int i = 0; i < history.size(); i++) appendLog(history.get(i));
    }

    private void appendLog(String line) {
        if (logBuffer.length() > 0) logBuffer.append('\n');
        logBuffer.append(line);
        if (logBuffer.length() > MAX_LOG_CHARS) {
            logBuffer.delete(0, logBuffer.length() - MAX_LOG_CHARS);
        }
        tvLog.setText(logBuffer.toString());
        scrollLogToEnd();
    }

    private void scrollLogToEnd() {
        logScroll.post(new Runnable() {
            public void run() {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }


    private void restoreState() {
        bindStore();
        if (store != null) refreshFileList();
        applyIpv6Visibility();
        if (ServerHub.isRunning()) onServerRunning(true, false);
    }

    private void requestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
            }
        }
    }

    private void copy(String label, String text) {
        if (TextUtils.isEmpty(text)) {
            toast(getString(R.string.nothing_to_copy));
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            toast(getString(R.string.copied, text));
        } catch (Throwable t) {
            toast(getString(R.string.copy_failed));
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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

    private static class LoadResult {
        final List<DocStore.Doc> list;
        final String error;

        LoadResult(List<DocStore.Doc> list, String error) {
            this.list = list;
            this.error = error;
        }
    }
}
