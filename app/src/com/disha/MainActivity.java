package com.disha;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    // ---------- palette: the Raider's teal cluster, at night (dark) or by day (light) ----------
    private int BG, BAR, BAR_LINE, HERO, HERO_LINE, TILE, TILE_LINE, PILL, PILL_LINE, SELECTED, LOG_BG,
            ACCENT, ON_ACCENT, TEXT, TEXT2, SUB, MUTED, FAINT, EMPTY, OK, WARN, WARN_BG, ON_WARN, BAD,
            RED_TEXT, RED_BG, RED_LINE, PURPLE, BLUE, SCALE_HI, ARROW_DIM, ON_ACCENT_DIM, RIPPLE,
            TOGGLE_OFF, KNOB_OFF;
    /** Kept for the scan pop-up's rows. */
    private int SURFACE, SURFACE2, LINE, ACCENT_DARK;
    private boolean darkNow;

    private void applyPalette(boolean dark) {
        darkNow = dark;
        if (dark) {
            BG = 0xFF061012; BAR = 0xFF0A1617; BAR_LINE = 0xFF142627; HERO = 0xFF0D2224; HERO_LINE = 0xFF1C3533;
            TILE = 0xFF0C1A1C; TILE_LINE = 0xFF16292A; PILL = 0xFF10201F; PILL_LINE = 0xFF1F3A38;
            SELECTED = 0xFF0E3B39; LOG_BG = 0xFF040B0C; ACCENT = 0xFF2DD4C4; ON_ACCENT = 0xFF04201F;
            TEXT = 0xFFEAF5F6; TEXT2 = 0xFFCFE3E4; SUB = 0xFFA9C2C4; MUTED = 0xFF8FA9AC; FAINT = 0xFF5F7A7D;
            EMPTY = 0xFF1A2E30; OK = 0xFF3BE39E; WARN = 0xFFFFB547; WARN_BG = 0xFF2A2110; ON_WARN = 0xFF2A1A00;
            BAD = 0xFFFF6B6B; RED_TEXT = 0xFFFF9B9B; RED_BG = 0xFF1A1213; RED_LINE = 0xFF3A2A2A;
            PURPLE = 0xFFA78BFA; BLUE = 0xFF7CC4FF; SCALE_HI = 0xFFB98A3A; ARROW_DIM = 0x33FFFFFF;
            ON_ACCENT_DIM = 0x5504201F; RIPPLE = 0x33FFFFFF; TOGGLE_OFF = 0xFF2A3B3D; KNOB_OFF = 0xFFB8C7C9;
        } else {
            BG = 0xFFF3F7F7; BAR = 0xFFFFFFFF; BAR_LINE = 0xFFDCE6E7; HERO = 0xFFFFFFFF; HERO_LINE = 0xFFD5E2E3;
            TILE = 0xFFFFFFFF; TILE_LINE = 0xFFE1EAEB; PILL = 0xFFEAF1F1; PILL_LINE = 0xFFD3E0E1;
            SELECTED = 0xFFD3EFEC; LOG_BG = 0xFFFFFFFF; ACCENT = 0xFF0A7D73; ON_ACCENT = 0xFFFFFFFF;
            TEXT = 0xFF0B1F22; TEXT2 = 0xFF24393C; SUB = 0xFF44595C; MUTED = 0xFF5B7275; FAINT = 0xFF6F8588;
            EMPTY = 0xFFDCE7E8; OK = 0xFF0E8F57; WARN = 0xFFA15C00; WARN_BG = 0xFFFFF0D9; ON_WARN = 0xFFFFFFFF;
            BAD = 0xFFC93636; RED_TEXT = 0xFFB3261E; RED_BG = 0xFFFDECEC; RED_LINE = 0xFFF1C7C7;
            PURPLE = 0xFF6B4FC8; BLUE = 0xFF1F6FB2; SCALE_HI = 0xFFA15C00; ARROW_DIM = 0x22000000;
            ON_ACCENT_DIM = 0x66FFFFFF; RIPPLE = 0x22000000; TOGGLE_OFF = 0xFFC9D6D8; KNOB_OFF = 0xFFFFFFFF;
        }
        SURFACE = TILE; SURFACE2 = PILL; LINE = HERO_LINE; ACCENT_DARK = SELECTED;
    }

    // ---------- theme: Settings > Appearance ----------
    static final String[] THEMES = {"dark", "light", "system", "auto"};

    /** Dark or light right now, from the setting: always, the phone's dark mode, or the clock. */
    private boolean wantDark() {
        String t = BikeService.prefs(this).getString("theme", "dark");
        if ("light".equals(t)) return false;
        if ("system".equals(t)) {
            return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        if ("auto".equals(t)) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            return hour < AUTO_LIGHT_FROM || hour >= AUTO_DARK_FROM;
        }
        return true;
    }

    /** Auto: light 6 AM to 6 PM. Near the equator (Sri Lanka, south India) sunrise and sunset stay close to these all year. */
    static final int AUTO_LIGHT_FROM = 6, AUTO_DARK_FROM = 18;

    private final Handler h = new Handler(Looper.getMainLooper());

    // ---------- fonts (assets/fonts, OFL): Space Grotesk for numbers and titles, Inter for text ----------
    private final Map<String, Typeface> fonts = new HashMap<>();

    private Typeface font(String file, int weight) {
        String key = file + weight;
        Typeface t = fonts.get(key);
        if (t == null) {
            try {
                t = new Typeface.Builder(getAssets(), "fonts/" + file)
                        .setFontVariationSettings("'wght' " + weight).build();
            } catch (Exception e) {
                t = null;
            }
            if (t == null) t = Typeface.create(Typeface.SANS_SERIF, weight, false);
            fonts.put(key, t);
        }
        return t;
    }

    private Typeface display(int w) { return font("SpaceGrotesk.ttf", w); }
    private Typeface body(int w) { return font("Inter.ttf", w); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        BikeService.initLog(this);
        applyPalette(wantDark());
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BAR);
        getWindow().getDecorView().setBackgroundColor(BG);
        if (!darkNow) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        SharedPreferences p = BikeService.prefs(this);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        FrameLayout content = new FrameLayout(this);
        outer.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        buildHome(tabPage(content, true));
        buildOverview(tabPage(content, true));
        buildLog(tabPage(content, false));
        buildSettings(tabPage(content, true), p);

        buildNavBar(outer);
        setContentView(outer);
        showTab(b != null ? b.getInt("tab", TAB_HOME) : TAB_HOME);
        askPermissions();
    }

    @Override protected void onResume() {
        super.onResume();
        h.post(refresh);
        h.post(rideUiTick);
        h.post(speedTick);
        if (BikeService.instance == null) RideStore.finishStale(this);
        refreshRides();
        // First run (no bike remembered): open the device list by itself, once per app start.
        if (!autoScanned && BikeService.prefs(this).getString("mac", "").isEmpty()
                && (Build.VERSION.SDK_INT < 31
                    || checkSelfPermission("android.permission.BLUETOOTH_SCAN") == PackageManager.PERMISSION_GRANTED)) {
            autoScanned = true;
            h.postDelayed(this::findBike, 600);
        }
    }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresh); h.removeCallbacks(rideUiTick); h.removeCallbacks(speedTick); }

    private boolean autoScanned;

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);   // keep the tab when the theme switches
    }

    static final String REPO_URL = "https://github.com/sameeramperera/Disha";
    static final String BIKE_MODEL = "TVS Raider 125";

    // =====================================================================================
    // Tabs and the bottom bar
    // =====================================================================================

    static final int TAB_HOME = 0, TAB_OVERVIEW = 1, TAB_LOG = 2, TAB_SETTINGS = 3;
    private final List<View> pages = new ArrayList<>();
    private final List<NavItem> navItems = new ArrayList<>();
    private int tab = TAB_HOME;

    /** One tab's page: a scrolling column, or (for the log) a plain column that fills the screen. */
    private LinearLayout tabPage(FrameLayout content, boolean scroll) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(22), dp(20), dp(16));
        View page = col;
        if (scroll) {
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(col);
            page = sv;
        }
        page.setVisibility(View.GONE);
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        pages.add(page);
        return col;
    }

    private void showTab(int t) {
        tab = t;
        for (int i = 0; i < pages.size(); i++) pages.get(i).setVisibility(i == t ? View.VISIBLE : View.GONE);
        for (int i = 0; i < navItems.size(); i++) navItems.get(i).set(i == t);
        if (t == TAB_LOG) updateLog(true);
        if (t == TAB_OVERVIEW) { refreshRides(); updateBike(); }
        if (t == TAB_SETTINGS) updateSettings();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tab != TAB_HOME) showTab(TAB_HOME);
        else super.onBackPressed();
    }

    private void buildNavBar(LinearLayout outer) {
        View line = new View(this);
        line.setBackgroundColor(BAR_LINE);
        outer.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout bar = hbox();
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(8), dp(8), dp(8), dp(12));
        String[] names = {"Home", "Overview", "Logs", "Settings"};
        int[] icons = {LineIcon.HOME, LineIcon.OVERVIEW, LineIcon.LOG, LineIcon.SETTINGS};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            NavItem item = new NavItem(bar, names[i], icons[i]);
            item.root.setOnClickListener(v -> showTab(t));
            navItems.add(item);
        }
        outer.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    /** A bar entry: icon in a pill (filled when selected) with its name underneath. */
    private final class NavItem {
        final LinearLayout root;
        final FrameLayout pill;
        final LineIcon icon;
        final TextView name;

        NavItem(LinearLayout bar, String text, int kind) {
            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setClickable(true);
            root.setContentDescription(text);
            pill = new FrameLayout(MainActivity.this);
            icon = new LineIcon(MainActivity.this, kind, MUTED);
            pill.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(60), dp(32));
            pl.setMargins(0, dp(4), 0, 0);
            root.addView(pill, pl);
            name = text(text, 12, MUTED, body(500));
            name.setPadding(0, dp(4), 0, 0);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setSingleLine(true);
            root.addView(name, new LinearLayout.LayoutParams(-1, -2));
            bar.addView(root, new LinearLayout.LayoutParams(0, -2, 1));
        }

        void set(boolean on) {
            pill.setBackground(on ? round(SELECTED, 99, 0) : null);
            icon.setColor(on ? ACCENT : MUTED);
            name.setTextColor(on ? TEXT : MUTED);
            name.setTypeface(body(on ? 600 : 500));
        }
    }

    // =====================================================================================
    // Home
    // =====================================================================================

    private TextView statusTitle, statusSub, pillText, distView, instrView, roadView,
            timeLeftView, distLeftView, etaView, primaryBtn, homeFuelText, homeMileage, homeBattery,
            setupNote;
    private View pillDot, setupCard;
    private FrameLayout arrowTile;
    private ArrowView arrow;
    private final View[] homeFuelSegs = new View[5];
    private boolean blink;

    private void buildHome(LinearLayout root) {
        // DISHA + connection pill
        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("DISHA", 15, ACCENT, display(700));
        brand.setLetterSpacing(0.32f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout pill = hbox();
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(7), dp(12), dp(7));
        pill.setBackground(round(PILL, 99, HERO_LINE));
        pillDot = new View(this);
        pill.addView(pillDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        pillText = text("", 12, TEXT2, body(500));
        pillText.setPadding(dp(8), 0, 0, 0);
        pill.addView(pillText);
        top.addView(pill);
        root.addView(top);

        statusSub = text("", 13, MUTED, body(400));
        statusSub.setPadding(0, dp(20), 0, dp(2));
        root.addView(statusSub);
        statusTitle = text("", 28, TEXT, display(600));
        root.addView(statusTitle);

        // Shown only while something in Settings > Setup still needs fixing.
        setupCard = hbox();
        ((LinearLayout) setupCard).setGravity(Gravity.CENTER_VERTICAL);
        setupCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        setupCard.setBackground(ripple(round(WARN_BG, 18, 0)));
        setupCard.setClickable(true);
        setupCard.setOnClickListener(v -> showTab(TAB_SETTINGS));
        TextView bang = text("!", 14, ON_WARN, body(700));
        bang.setGravity(Gravity.CENTER);
        bang.setBackground(round(WARN, 99, 0));
        ((LinearLayout) setupCard).addView(bang, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.VERTICAL);
        st.setPadding(dp(12), 0, dp(8), 0);
        st.addView(text("Finish setup", 14, TEXT, body(600)));
        setupNote = text("", 12, MUTED, body(400));
        st.addView(setupNote);
        ((LinearLayout) setupCard).addView(st, new LinearLayout.LayoutParams(0, -2, 1));
        ((LinearLayout) setupCard).addView(new LineIcon(this, LineIcon.CHEVRON, WARN), new LinearLayout.LayoutParams(dp(18), dp(18)));
        root.addView(setupCard, marginTop(-1, -2, 16));

        // Navigation card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(ripple(round(HERO, 28, HERO_LINE)));
        card.setClickable(true);
        card.setContentDescription("Open Google Maps");
        card.setOnClickListener(v -> openMaps());
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        arrowTile = new FrameLayout(this);
        arrow = new ArrowView(this);
        int pad = dp(16);
        arrowTile.setPadding(pad, pad, pad, pad);
        arrowTile.addView(arrow, new FrameLayout.LayoutParams(-1, -1));
        row.addView(arrowTile, new LinearLayout.LayoutParams(dp(104), dp(104)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), 0, 0, 0);
        distView = text("", 52, TEXT, display(700));
        distView.setIncludeFontPadding(false);
        distView.setLetterSpacing(-0.02f);
        col.addView(distView);
        instrView = text("", 18, TEXT, body(600));
        instrView.setPadding(0, dp(6), 0, 0);
        col.addView(instrView);
        roadView = text("", 14, SUB, body(400));
        roadView.setMaxLines(2);
        col.addView(roadView);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        View div = new View(this);
        div.setBackgroundColor(HERO_LINE);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(1));
        dl.setMargins(0, dp(18), 0, dp(16));
        card.addView(div, dl);
        LinearLayout stats = hbox();
        timeLeftView = stat(stats, "TIME LEFT", 19);
        distLeftView = stat(stats, "DISTANCE", 19);
        etaView = stat(stats, "ARRIVE", 19);
        card.addView(stats);
        root.addView(card, marginTop(-1, -2, 16));

        // Bike at a glance; tap for the Overview tab
        LinearLayout tiles = hbox();
        LinearLayout fuel = tile(tiles, "FUEL", 0);
        LinearLayout segs = hbox();
        for (int i = 0; i < 5; i++) {
            homeFuelSegs[i] = new View(this);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(8), 1);
            if (i < 4) sl.setMargins(0, 0, dp(3), 0);
            segs.addView(homeFuelSegs[i], sl);
        }
        fuel.addView(segs, marginTop(-1, -2, 10));
        homeFuelText = text("--", 15, TEXT, display(600));
        homeFuelText.setPadding(0, dp(10), 0, 0);
        fuel.addView(homeFuelText);
        homeMileage = tileValue(tile(tiles, "MILEAGE", dp(10)), "km/L avg");
        homeBattery = tileValue(tile(tiles, "BATTERY", dp(10)), "volts");
        for (int i = 0; i < tiles.getChildCount(); i++) tiles.getChildAt(i).setOnClickListener(v -> showTab(TAB_OVERVIEW));
        root.addView(tiles, marginTop(-1, -2, 16));

        buildSpeed(root);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout actions = hbox();
        primaryBtn = pillButton("Connect", true);
        primaryBtn.setOnClickListener(v -> {
            BikeService s = BikeService.instance;
            if (!bluetoothOn()) requestBluetooth(this::connect);
            else if (s != null && s.isActive()) disconnect();
            else connect();
        });
        actions.addView(primaryBtn, new LinearLayout.LayoutParams(0, dp(54), 1));
        FrameLayout find = new FrameLayout(this);
        find.setBackground(ripple(round(PILL, 99, PILL_LINE)));
        find.setClickable(true);
        find.setContentDescription("Find bike");
        find.addView(new LineIcon(this, LineIcon.SEARCH, TEXT), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        find.setOnClickListener(v -> findBike());
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(dp(54), dp(54));
        fl.setMargins(dp(10), 0, 0, 0);
        actions.addView(find, fl);
        root.addView(actions, marginTop(-1, -2, 24));
    }

    // ---------- live speed ----------
    private static final int SPEED_SEGS = 24, SPEED_SCALE = 120;
    private TextView speedView, speedMax;
    private final View[] speedSegs = new View[SPEED_SEGS];
    private int maxTodaySaved;     // fastest in today's finished rides (from refreshRides)

    /** Big number and a 24-segment bar rising left to right, like the cluster's rev bar. */
    private void buildSpeed(LinearLayout root) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(14));
        c.setBackground(round(HERO, 28, HERO_LINE));
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new LineIcon(this, LineIcon.OVERVIEW, MUTED), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView l = caps("SPEED");
        l.setPadding(dp(10), 0, 0, 0);
        head.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        speedMax = text("", 12, MUTED, body(400));
        head.addView(speedMax);
        c.addView(head);

        speedView = text("--", 38, TEXT, display(700));
        speedView.setIncludeFontPadding(false);
        speedView.setLetterSpacing(-0.02f);
        c.addView(speedView, marginTop(-2, -2, 12));

        LinearLayout bar = hbox();
        bar.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < SPEED_SEGS; i++) {
            speedSegs[i] = new View(this);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(14 + Math.round(i * 16f / (SPEED_SEGS - 1))), 1);
            if (i < SPEED_SEGS - 1) sl.setMargins(0, 0, dp(3), 0);
            bar.addView(speedSegs[i], sl);
        }
        c.addView(bar, marginTop(-1, dp(30), 12));

        LinearLayout scale = hbox();
        String[] marks = {"0", "30", "60", "90", "120"};
        for (int i = 0; i < marks.length; i++) {
            TextView m = text(marks[i], 11, i == marks.length - 1 ? SCALE_HI : FAINT, display(500));
            m.setGravity(i == 0 ? Gravity.START : i == marks.length - 1 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
            scale.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        }
        c.addView(scale, marginTop(-1, -2, 6));
        root.addView(c, marginTop(-1, -2, 16));
        updateSpeed();
    }

    /** Refreshed about 3 times a second on Home, as fast as the bike sends its speed. */
    private void updateSpeed() {
        if (speedView == null) return;
        boolean live = BikeService.instance != null && BikeService.instance.isReady()
                && System.currentTimeMillis() - BikeService.telemetryAt < 3000;
        int kmh = live ? BikeService.speedKmh : -1;
        SharedPreferences p = BikeService.prefs(this);
        // Amber zone: from the speed alert when it is on, else 100+ km/h.
        int warnAt = p.getBoolean("speedAlert", false) ? p.getInt("speedLimit", 80) : 100;
        int lit = kmh <= 0 ? 0 : Math.min(SPEED_SEGS, Math.round(kmh * (float) SPEED_SEGS / SPEED_SCALE));
        for (int i = 0; i < SPEED_SEGS; i++) {
            boolean fast = (i + 1) * SPEED_SCALE / (float) SPEED_SEGS > warnAt;
            int color = i < lit ? (fast ? WARN : ACCENT) : (fast ? WARN_BG : EMPTY);
            speedSegs[i].setBackground(round(color, 3, 0));
        }
        if (kmh < 0) {
            speedView.setText("--");
            speedView.setTextColor(MUTED);
        } else {
            String n = String.valueOf(kmh);
            SpannableString sp = new SpannableString(n + " km/h");
            sp.setSpan(new AbsoluteSizeSpan(18, true), n.length(), sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new ForegroundColorSpan(MUTED), n.length(), sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            speedView.setText(sp);
            speedView.setTextColor(kmh >= warnAt ? WARN : TEXT);
        }
        RideTracker.Ride r = BikeService.currentRide;
        int max = Math.max(maxTodaySaved, r != null ? r.maxKmh : 0);
        if (max <= 0) speedMax.setText("");
        else {
            String v = String.valueOf(max);
            SpannableString sp = new SpannableString("Max today " + v + " km/h");
            sp.setSpan(new ForegroundColorSpan(TEXT), 10, 10 + v.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            speedMax.setText(sp);
        }
    }

    private final Runnable speedTick = new Runnable() {
        @Override public void run() {
            if (tab == TAB_HOME) updateSpeed();
            h.postDelayed(this, 300);
        }
    };

    /** A rounded tile in a row of equal tiles, with its small caps label. */
    private LinearLayout tile(LinearLayout row, String label, int leftGap) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackground(ripple(round(TILE, 20, TILE_LINE)));
        t.setClickable(true);
        t.addView(caps(label));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(leftGap, 0, 0, 0);
        row.addView(t, lp);
        return t;
    }

    private TextView tileValue(LinearLayout t, String unit) {
        TextView v = text("--", 22, TEXT, display(600));
        v.setIncludeFontPadding(false);
        v.setPadding(0, dp(12), 0, 0);
        t.addView(v);
        TextView u = text(unit, 12, MUTED, body(400));
        u.setPadding(0, dp(8), 0, 0);
        t.addView(u);
        return v;
    }

    private TextView stat(LinearLayout row, String title, int sp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(caps(title));
        TextView v = text("--", sp, TEXT, display(600));
        v.setPadding(0, dp(4), 0, 0);
        c.addView(v);
        row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        return v;
    }

    private void updateStatus() {
        String st = BikeService.status;
        BikeService s = BikeService.instance;
        boolean ready = s != null && s.isReady();
        boolean standby = !ready && s != null && st.startsWith("Waiting for the bike");
        boolean connecting = !ready && !standby && s != null && (st.startsWith("Connect") || st.contains("retrying"));
        boolean navigating = MapsListener.current != null;
        boolean mirror = MapsListener.mirrorOn(this);
        String mac = BikeService.prefs(this).getString("mac", "");
        boolean btOff = !bluetoothOn();
        int dot;
        String pill, title, sub;
        if (btOff) {
            dot = BAD; pill = "Bluetooth off"; title = "Turn Bluetooth on";
            sub = "Disha needs Bluetooth to reach the bike";
        } else if (ready) {
            dot = OK; pill = "Raider connected";
            title = navigating ? "On the way" : "Ready to ride";
            sub = navigating ? (mirror ? "Sending to the cluster" : "Mirror navigation is off")
                    : "Start directions in Google Maps";
        } else if (standby) {
            dot = MUTED; pill = "Standby"; title = "Bike is off";
            sub = "Connects by itself when you switch the ignition on";
        } else if (connecting) {
            dot = WARN; pill = "Connecting"; title = "Looking for your bike";
            sub = "Ignition on, and TVS Connect closed?";
        } else {
            dot = MUTED; pill = "Offline"; title = "Not connected";
            sub = mac.isEmpty() ? "No bike chosen yet: tap the search button" : "Tap Connect to reach your bike";
        }
        blink = !blink;
        pillDot.setBackground(round(dot, 99, 0));
        pillDot.setAlpha(connecting && blink ? 0.3f : 1f);
        pillText.setText(pill);
        statusTitle.setText(title);
        statusSub.setText(sub);
        boolean linked = ready || connecting || standby;   // the service is holding or seeking the link
        primaryBtn.setText(btOff ? "Turn on Bluetooth" : standby ? "Stop waiting" : linked ? "Disconnect" : "Connect");
    }

    private void updateHero() {
        TvsProtocol.Nav n = MapsListener.current;
        if (n == null) {
            arrowTile.setBackground(round(PILL, 26, PILL_LINE));
            arrow.setColors(MUTED, ARROW_DIM);
            arrow.setPicto(-1);
            distView.setText("--");
            distView.setTextColor(MUTED);
            instrView.setText("No navigation");
            roadView.setText("Start directions in Google Maps. Turns appear here and on your bike.");
            timeLeftView.setText("--");
            distLeftView.setText("--");
            etaView.setText("--");
            return;
        }
        boolean close = n.distNextM <= 100 && n.picto != TvsProtocol.P_ARRIVE && n.picto != TvsProtocol.P_STRAIGHT;
        arrowTile.setBackground(round(close ? WARN : ACCENT, 26, 0));
        arrow.setColors(ON_ACCENT, ON_ACCENT_DIM);
        arrow.setPicto(n.picto);
        distView.setText(distance(n.distNextM, n.picto));
        distView.setTextColor(TEXT);
        instrView.setText(n.text);
        roadView.setText(MapsListener.road);
        timeLeftView.setText(minutes(n.minutesLeft));
        distLeftView.setText(km(n.distLeftM));
        etaView.setText(MapsListener.eta.isEmpty() ? "--" : MapsListener.eta);
    }

    private void updateHomeTiles() {
        int bars = BikeFrames.fuelBars;
        paintFuel(homeFuelSegs, bars);
        homeFuelText.setText(bars < 0 ? "--" : bars + " / 5");
        homeMileage.setText(BikeFrames.avgKmL > 0 ? String.valueOf(BikeFrames.avgKmL) : "--");
        homeBattery.setText(BikeFrames.batteryTenths > 0 ? BikeFrames.tenthsText(BikeFrames.batteryTenths) : "--");
    }

    private void paintFuel(View[] segs, int bars) {
        for (int i = 0; i < segs.length; i++)
            segs[i].setBackground(round(i < bars ? (bars <= 1 ? WARN : ACCENT) : EMPTY, segs == homeFuelSegs ? 3 : 6, 0));
    }

    /** "150 m" / "1.8 km" with the unit smaller and grey. */
    private CharSequence distance(int m, int picto) {
        if (picto == TvsProtocol.P_ARRIVE) return "Here";
        if (picto == TvsProtocol.P_REROUTING) return "...";
        String num, unit;
        if (m >= 1000) { num = String.format(Locale.ROOT, m >= 10000 ? "%.0f" : "%.1f", m / 1000.0); unit = " km"; }
        else { num = String.valueOf(m); unit = " m"; }
        SpannableString s = new SpannableString(num + unit);
        s.setSpan(new AbsoluteSizeSpan(22, true), num.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(MUTED), num.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static String minutes(int min) {
        if (min <= 0) return "--";
        return min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
    }

    private static String km(int m) {
        if (m <= 0) return "--";
        return m >= 10000 ? (m / 1000) + " km" : String.format(Locale.ROOT, "%.1f km", m / 1000.0);
    }

    // =====================================================================================
    // Overview: live bike data and rides
    // =====================================================================================

    private TextView liveText, fuelState, fuelBig, odoText, tripBText, mileageText, batteryText, weekText, rideLine;
    private View liveDot;
    private final View[] fuelSegs = new View[5];
    private LinearLayout ridesBody;
    private boolean wasRiding;

    private void buildOverview(LinearLayout root) {
        LinearLayout head = hbox();
        head.setGravity(Gravity.BOTTOM);
        head.addView(pageTitle(BIKE_MODEL, "Overview"), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout live = hbox();
        live.setGravity(Gravity.CENTER_VERTICAL);
        live.setPadding(0, 0, 0, dp(8));
        liveDot = new View(this);
        live.addView(liveDot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        liveText = text("", 12, SUB, body(400));
        liveText.setPadding(dp(6), 0, 0, 0);
        live.addView(liveText);
        head.addView(live);
        root.addView(head);

        // Fuel: rising bars, like the cluster's gauge
        LinearLayout fc = new LinearLayout(this);
        fc.setOrientation(LinearLayout.VERTICAL);
        fc.setPadding(dp(20), dp(20), dp(20), dp(20));
        fc.setBackground(round(HERO, 28, HERO_LINE));
        LinearLayout fh = hbox();
        fh.setGravity(Gravity.CENTER_VERTICAL);
        fh.addView(new LineIcon(this, LineIcon.FUEL, MUTED), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView fl = caps("FUEL");
        fl.setPadding(dp(10), 0, 0, 0);
        fh.addView(fl, new LinearLayout.LayoutParams(0, -2, 1));
        fuelState = text("", 13, ACCENT, body(600));
        fh.addView(fuelState);
        fc.addView(fh);
        LinearLayout bars = hbox();
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 5; i++) {
            fuelSegs[i] = new View(this);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(28 + 9 * i), 1);
            if (i < 4) bl.setMargins(0, 0, dp(6), 0);
            bars.addView(fuelSegs[i], bl);
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(64));
        bp.setMargins(0, dp(16), 0, dp(16));
        fc.addView(bars, bp);
        LinearLayout fb = hbox();
        fb.setGravity(Gravity.BOTTOM);
        fuelBig = text("--", 34, TEXT, display(700));
        fuelBig.setIncludeFontPadding(false);
        fb.addView(fuelBig, new LinearLayout.LayoutParams(0, -2, 1));
        fc.addView(fb);
        root.addView(fc, marginTop(-1, -2, 16));

        LinearLayout r1 = hbox();
        odoText = bigTile(r1, "ODO", "km", 0);
        tripBText = bigTile(r1, "TRIP B", "km", dp(10));
        root.addView(r1, marginTop(-1, -2, 10));
        LinearLayout r2 = hbox();
        mileageText = bigTile(r2, "AVG MILEAGE", "km/L", 0);
        batteryText = bigTile(r2, "BATTERY", "volts", dp(10));
        root.addView(r2, marginTop(-1, -2, 10));

        LinearLayout rh = hbox();
        rh.setGravity(Gravity.BOTTOM);
        rh.addView(text("Rides", 18, TEXT, display(600)), new LinearLayout.LayoutParams(0, -2, 1));
        weekText = text("", 12, MUTED, body(400));
        weekText.setPadding(0, 0, 0, dp(2));
        rh.addView(weekText);
        root.addView(rh, marginTop(-1, -2, 22));
        rideLine = text("", 13, ACCENT, body(600));
        rideLine.setVisibility(View.GONE);
        root.addView(rideLine, marginTop(-1, -2, 6));
        ridesBody = new LinearLayout(this);
        ridesBody.setOrientation(LinearLayout.VERTICAL);
        root.addView(ridesBody);
        updateBike();
    }

    private TextView bigTile(LinearLayout row, String label, String unit, int leftGap) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setBackground(round(TILE, 22, TILE_LINE));
        t.addView(caps(label));
        TextView v = text("--", 24, TEXT, display(600));
        v.setIncludeFontPadding(false);
        v.setPadding(0, dp(12), 0, 0);
        t.addView(v);
        TextView u = text(unit, 12, MUTED, body(400));
        u.setPadding(0, dp(8), 0, 0);
        t.addView(u);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(leftGap, 0, 0, 0);
        row.addView(t, lp);
        return v;
    }

    private void updateBike() {
        if (fuelBig == null) return;
        boolean live = BikeService.instance != null && BikeService.instance.isReady()
                && System.currentTimeMillis() - BikeFrames.updatedAt < 10_000;
        liveDot.setBackground(round(live ? OK : FAINT, 99, 0));
        liveText.setText(live ? "Live" : BikeFrames.updatedAt == 0 ? "Not connected"
                : "Seen " + new java.text.SimpleDateFormat("HH:mm", Locale.ENGLISH).format(new java.util.Date(BikeFrames.updatedAt)));
        int bars = BikeFrames.fuelBars;
        paintFuel(fuelSegs, bars);
        fuelState.setText(bars < 0 ? "" : bars == 5 ? "Full" : bars <= 1 ? "Low" : "");
        fuelState.setTextColor(bars <= 1 ? WARN : ACCENT);
        if (bars < 0) fuelBig.setText("--");
        else {
            String n = String.valueOf(bars);
            SpannableString s = new SpannableString(n + " / 5 bars");
            s.setSpan(new AbsoluteSizeSpan(18, true), n.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(MUTED), n.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fuelBig.setText(s);
        }
        odoText.setText(BikeService.odoTenths > 0 ? String.format(Locale.ROOT, "%,.1f", BikeService.odoTenths / 10.0) : "--");
        tripBText.setText(BikeFrames.tripBTenths >= 0 ? BikeFrames.tenthsText(BikeFrames.tripBTenths) : "--");
        mileageText.setText(BikeFrames.avgKmL > 0 ? String.valueOf(BikeFrames.avgKmL) : "--");
        batteryText.setText(BikeFrames.batteryTenths > 0 ? BikeFrames.tenthsText(BikeFrames.batteryTenths) : "--");
    }

    private final Runnable rideUiTick = new Runnable() {
        @Override public void run() {
            RideTracker.Ride r = BikeService.currentRide;
            // "Riding now" only while the bike is actually sending. After the ignition goes off the
            // ride stays open for 2 minutes (in case it was just a stall) but is not shown as riding.
            boolean live = r != null && BikeService.instance != null
                    && System.currentTimeMillis() - BikeService.telemetryAt < 10_000;
            rideLine.setText(live ? "Riding now  ·  " + RideTracker.summary(r) : "");
            rideLine.setVisibility(live ? View.VISIBLE : View.GONE);
            if (wasRiding && r == null) refreshRides(); // a ride just ended
            wasRiding = r != null;
            h.postDelayed(this, 1000);
        }
    };

    /** Rebuilds the ride list: totals for the last 7 days, then each ride, newest first. */
    private void refreshRides() {
        if (ridesBody == null) return;
        ridesBody.removeAllViews();
        List<RideTracker.Ride> rides = RideStore.all(this);
        java.util.Calendar midnight = java.util.Calendar.getInstance();
        midnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
        midnight.set(java.util.Calendar.MINUTE, 0);
        midnight.set(java.util.Calendar.SECOND, 0);
        maxTodaySaved = 0;
        for (RideTracker.Ride r : rides) if (r.end >= midnight.getTimeInMillis()) maxTodaySaved = Math.max(maxTodaySaved, r.maxKmh);
        long weekAgo = System.currentTimeMillis() - 7L * 24 * 3600_000;
        int n = 0; double km = 0;
        for (RideTracker.Ride r : rides) if (r.start >= weekAgo) { n++; km += r.km(); }
        weekText.setText(String.format(Locale.ROOT, "Last 7 days · %d ride%s · %.1f km", n, n == 1 ? "" : "s", km));
        if (rides.isEmpty()) {
            TextView t = text("No rides yet. Rides are recorded while the app is connected to the bike.", 13, MUTED, body(400));
            ridesBody.addView(t, marginTop(-1, -2, 10));
            return;
        }
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("EEE d MMM · HH:mm", Locale.ENGLISH);
        for (int i = 0; i < rides.size() && i < 30; i++) {
            RideTracker.Ride r = rides.get(i);
            LinearLayout row = hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(round(TILE, 18, TILE_LINE));
            row.addView(iconTile(LineIcon.BIKE, 40, 12, SELECTED, ACCENT));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            texts.addView(text(when.format(new java.util.Date(r.start)), 14, TEXT, body(600)));
            TextView sum = text(RideTracker.summary(r), 12, MUTED, body(400));
            sum.setPadding(0, dp(2), 0, 0);
            texts.addView(sum);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            ridesBody.addView(row, marginTop(-1, -2, 8));
        }
    }

    // =====================================================================================
    // Logs
    // =====================================================================================

    private static final String[] FILTERS = {"All", "Bike data", "Maps", "Status"};
    private int logFilter = 0;
    private final List<TextView> filterChips = new ArrayList<>();
    private TextView logView;
    private ScrollView logScroll;
    private String lastLogKey = "";
    private int logIndent = -1;

    private void buildLog(LinearLayout root) {
        root.addView(pageTitle(null, "Logs"));

        LinearLayout row = hbox();
        LinearLayout share = hbox();
        share.setGravity(Gravity.CENTER);
        share.setBackground(ripple(round(ACCENT, 99, 0)));
        share.setClickable(true);
        share.addView(new LineIcon(this, LineIcon.SHARE, ON_ACCENT), new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView sl = text("Share log", 14, ON_ACCENT, body(600));
        sl.setPadding(dp(8), 0, 0, 0);
        share.addView(sl);
        share.setOnClickListener(v -> shareLog());
        row.addView(share, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView clear = text("Clear", 14, RED_TEXT, body(600));
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(20), 0, dp(20), 0);
        clear.setBackground(ripple(round(RED_BG, 99, RED_LINE)));
        clear.setClickable(true);
        clear.setOnClickListener(v -> confirmDelete("Clear the log?",
                "Deletes all log lines and the saved log files. Share it first if you still need it.", "Clear",
                this::clearLog));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, dp(46));
        cl.setMargins(dp(10), 0, 0, 0);
        row.addView(clear, cl);
        root.addView(row, marginTop(-1, -2, 16));

        LinearLayout chips = hbox();
        for (int i = 0; i < FILTERS.length; i++) {
            final int f = i;
            TextView c = text(FILTERS[i], 13, SUB, body(500));
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(14), 0, dp(14), 0);
            c.setClickable(true);
            c.setOnClickListener(v -> { logFilter = f; paintChips(); updateLog(true); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
            if (i > 0) lp.setMargins(dp(8), 0, 0, 0);
            chips.addView(c, lp);
            filterChips.add(c);
        }
        paintChips();
        root.addView(chips, marginTop(-1, -2, 14));

        logView = text("", 11, TEXT2, Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setLineSpacing(dp(2), 1f);
        logScroll = new ScrollView(this);
        logScroll.setPadding(dp(14), dp(12), dp(14), dp(12));
        logScroll.setClipToPadding(false);
        logScroll.setBackground(round(LOG_BG, 22, BAR_LINE));
        logScroll.setVerticalScrollBarEnabled(false);
        logScroll.addView(logView, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.setMargins(0, dp(14), 0, 0);
        root.addView(logScroll, lp);
    }

    private void paintChips() {
        for (int i = 0; i < filterChips.size(); i++) {
            boolean on = i == logFilter;
            TextView c = filterChips.get(i);
            c.setBackground(ripple(on ? round(SELECTED, 99, 0) : round(0, 99, HERO_LINE)));
            c.setTextColor(on ? ACCENT : SUB);
            c.setTypeface(body(on ? 600 : 500));
        }
    }

    /**
     * Newest lines at the top, coloured by kind, decoded bike values under their raw frame. While
     * the user has scrolled down to read older lines, hold the text still (new lines would push it
     * around); updates resume once they are back at the top.
     */
    private void updateLog(boolean force) {
        if (logView == null) return;
        if (!force && logScroll.getScrollY() > dp(8)) return;
        List<String> lines = BikeService.logLines(400);
        String key = logFilter + ":" + lines.size() + ":" + (lines.isEmpty() ? "" : lines.get(lines.size() - 1));
        if (!force && key.equals(lastLogKey)) return;
        lastLogKey = key;
        if (logIndent < 0) {
            Paint pm = new Paint();
            pm.setTypeface(Typeface.MONOSPACE);
            pm.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
            logIndent = (int) pm.measureText("00:00:00  ");
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String l = lines.get(i);
            String decoded = null;
            // A decoded "   = ..." line follows its raw frame: show the frame first, the meaning under it.
            if (l.length() > 9 && l.startsWith("   = ", 9) && i > 0 && lines.get(i - 1).startsWith("FROM BIKE ", 9)) {
                decoded = l.substring(14);
                l = lines.get(--i);
            }
            appendLogLine(out, l, decoded);
        }
        if (out.length() == 0) out.append(logFilter == 0 ? "The log is empty." : "No " + FILTERS[logFilter].toLowerCase(Locale.ROOT) + " lines yet.");
        logView.setText(out);
        if (force) logScroll.scrollTo(0, 0);
    }

    private void appendLogLine(SpannableStringBuilder out, String l, String decoded) {
        if (l.startsWith("---- ")) {
            if (logFilter != 0) return;
            int s = out.length();
            out.append(l.replace("-", "").trim()).append('\n');
            out.setSpan(new ForegroundColorSpan(FAINT), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
        if (l.length() < 10 || l.charAt(2) != ':' || l.charAt(8) != ' ') {
            if (logFilter == 0) out.append(l).append('\n');
            return;
        }
        String time = l.substring(0, 8), msg = l.substring(9);
        String tag; int tagColor; String rest; int restColor = TEXT2; int kind;   // kind: 1 bike, 2 maps, 3 status, 0 other
        if (msg.startsWith("FROM BIKE ")) {
            rest = msg.substring(10);
            boolean nav = rest.contains("<- bike (re)started navigation mode");
            tag = "BIKE"; tagColor = nav ? PURPLE : ACCENT; kind = 1;
            if (nav) rest = "Navigation mode started on the cluster";
            else restColor = MUTED;
        } else if (msg.startsWith("   = ")) {
            tag = "BIKE"; tagColor = ACCENT; rest = msg.substring(5); kind = 1;
        } else if (msg.startsWith("TO BIKE ")) {
            tag = "SENT"; tagColor = PURPLE; rest = msg.substring(8); restColor = MUTED; kind = 1;
        } else if (msg.startsWith("SEND ")) {
            tag = "SENT"; tagColor = PURPLE; rest = msg.substring(5); kind = 2;
        } else if (msg.startsWith("MAPS ") || msg.startsWith("ICON ") || msg.contains("Google Maps") || msg.startsWith("Parsed ")) {
            tag = "MAPS"; tagColor = BLUE; rest = msg.startsWith("MAPS ") ? msg.substring(5) : msg; kind = 2;
        } else if (msg.startsWith("STATUS ")) {
            rest = msg.substring(7);
            tag = "STATUS"; tagColor = rest.startsWith("READY") ? OK : WARN; kind = 3;
        } else if (msg.startsWith("RIDE ")) {
            tag = "RIDE"; tagColor = OK; rest = msg.substring(5); kind = 0;
        } else if (msg.startsWith("GAP ") || msg.startsWith("GATT") || msg.startsWith("MTU ") || msg.startsWith("Auto-connect")
                || msg.startsWith("Service ") || msg.startsWith("  char ") || msg.startsWith("Using ") || msg.startsWith("Notifications")
                || msg.startsWith("Scan")) {
            tag = "LINK"; tagColor = MUTED; rest = msg; kind = 3;
        } else {
            tag = "APP"; tagColor = MUTED; rest = msg; kind = 0;
        }
        if (logFilter != 0 && logFilter != kind) return;

        int start = out.length();
        out.append(time).append("  ");
        out.setSpan(new ForegroundColorSpan(FAINT), start, start + time.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int t = out.length();
        out.append(tag).append(' ');
        out.setSpan(new ForegroundColorSpan(tagColor), t, t + tag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int r = out.length();
        out.append(rest);
        out.setSpan(new ForegroundColorSpan(restColor), r, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append('\n');
        out.setSpan(new LeadingMarginSpan.Standard(0, logIndent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (decoded != null) {
            int d = out.length();
            out.append(decoded).append('\n');
            out.setSpan(new ForegroundColorSpan(TEXT2), d, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new LeadingMarginSpan.Standard(logIndent), d, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int gap = out.length();
        out.append('\n');
        out.setSpan(new AbsoluteSizeSpan(5, true), gap, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // =====================================================================================
    // Settings
    // =====================================================================================

    private TextView bikeTitle, bikeAddr, bikeBtn, setupSummary;
    private LineIcon setupChevron;
    private LinearLayout setupGroup;
    private boolean setupOpenedByUser;

    private void showSetup(boolean open) {
        setupGroup.setVisibility(open ? View.VISIBLE : View.GONE);
        setupChevron.setRotation(open ? 90 : 0);
    }
    private CheckRow chkPerms, chkMaps, chkBattery;

    private void buildSettings(LinearLayout root, SharedPreferences p) {
        root.addView(pageTitle("Disha", "Settings"));

        // The bike: Change scans again; a long press lets you type the address
        LinearLayout bc = hbox();
        bc.setGravity(Gravity.CENTER_VERTICAL);
        bc.setPadding(dp(16), dp(16), dp(16), dp(16));
        bc.setBackground(round(HERO, 24, HERO_LINE));
        bc.addView(iconTile(LineIcon.BIKE, 52, 16, ACCENT, ON_ACCENT));
        LinearLayout bt = new LinearLayout(this);
        bt.setOrientation(LinearLayout.VERTICAL);
        bt.setPadding(dp(14), 0, dp(8), 0);
        bikeTitle = text("", 16, TEXT, body(600));
        bt.addView(bikeTitle);
        bikeAddr = text("", 12, MUTED, Typeface.MONOSPACE);
        bikeAddr.setPadding(0, dp(3), 0, 0);
        bt.addView(bikeAddr);
        bc.addView(bt, new LinearLayout.LayoutParams(0, -2, 1));
        bikeBtn = text("Change", 13, TEXT, body(600));
        bikeBtn.setGravity(Gravity.CENTER);
        bikeBtn.setPadding(dp(14), 0, dp(14), 0);
        bikeBtn.setBackground(ripple(round(PILL, 99, PILL_LINE)));
        bikeBtn.setClickable(true);
        bikeBtn.setOnClickListener(v -> findBike());
        bc.addView(bikeBtn, new LinearLayout.LayoutParams(-2, dp(38)));
        bc.setOnLongClickListener(v -> { editAddress(); return true; });
        root.addView(bc, marginTop(-1, -2, 16));

        root.addView(sectionLabel("RIDING"));
        LinearLayout riding = group(root);
        switchRow(riding, LineIcon.SEND, "Mirror navigation", "Send Google Maps turns to the cluster", "mirrorNav", on -> {
            BikeService s = BikeService.instance;
            if (!on && s != null) s.stopNav();
            BikeService.log("Mirror navigation " + (on ? "on" : "off"));
        });
        divider(riding);
        switchRow(riding, LineIcon.BIKE, "Ride mode", "Keep connecting, even after a phone restart", "rideMode",
                on -> BikeService.log("Ride mode " + (on ? "on" : "off")));

        root.addView(sectionLabel("ALERTS"));
        LinearLayout alerts = group(root);
        switchRow(alerts, LineIcon.OVERVIEW, "Speed alert", "Vibrate the phone when you reach this speed", "speedAlert", false,
                on -> BikeService.log("Speed alert " + (on ? "on" : "off")));
        stepperRow(alerts, "Alert at", "speedLimit", 80, 30, 150, 5, " km/h");
        divider(alerts);
        switchRow(alerts, LineIcon.FUEL, "Low fuel warning", "Notify when the fuel gauge drops to this level", "fuelAlert", true,
                on -> BikeService.log("Low fuel warning " + (on ? "on" : "off")));
        stepperRow(alerts, "Warn at", "fuelLevel", 1, 1, 4, 1, null);

        root.addView(sectionLabel("APPEARANCE"));
        buildTheme(group(root));

        // Setup folds away: tap its heading. Closed when all is set, open when something needs fixing.
        LinearLayout sh = hbox();
        sh.setGravity(Gravity.CENTER_VERTICAL);
        sh.setPadding(dp(4), dp(22), dp(4), dp(10));
        sh.setClickable(true);
        sh.addView(caps("SETUP"), new LinearLayout.LayoutParams(0, -2, 1));
        setupSummary = text("", 12, OK, body(600));
        sh.addView(setupSummary);
        setupChevron = new LineIcon(this, LineIcon.CHEVRON, MUTED);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(16), dp(16));
        cl.setMargins(dp(6), 0, 0, 0);
        sh.addView(setupChevron, cl);
        root.addView(sh);
        LinearLayout setup = group(root);
        setupGroup = setup;
        sh.setOnClickListener(v -> { setupOpenedByUser = true; showSetup(setup.getVisibility() != View.VISIBLE); });
        chkPerms = new CheckRow(setup, "Bluetooth & notifications", "Lets the app talk to the bike", this::askPermissions);
        divider(setup);
        chkMaps = new CheckRow(setup, "Read Google Maps", "Needed to see the turn arrows",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        divider(setup);
        chkBattery = new CheckRow(setup, "Run with screen off", "Stops the phone pausing navigation", this::keepRunning);

        root.addView(sectionLabel("DATA"));
        LinearLayout data = group(root);
        LinearLayout clear = hbox();
        clear.setGravity(Gravity.CENTER_VERTICAL);
        clear.setPadding(dp(16), dp(15), dp(16), dp(15));
        clear.setBackground(ripple(round(0, 22, 0)));
        clear.setClickable(true);
        clear.addView(text("Clear ride history", 14, TEXT, body(500)), new LinearLayout.LayoutParams(0, -2, 1));
        clear.addView(new LineIcon(this, LineIcon.CHEVRON, FAINT), new LinearLayout.LayoutParams(dp(18), dp(18)));
        clear.setOnClickListener(v -> confirmDelete("Delete all rides?",
                "The ride history is removed from this phone.", "Delete",
                () -> { RideStore.clear(this); refreshRides(); toast("Rides deleted"); }));
        data.addView(clear);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        buildFooter(root);
        updateSettings();
    }

    /** Dark | Light | System | Auto, with a line saying what the choice does. */
    private void buildTheme(LinearLayout g) {
        final SharedPreferences p = BikeService.prefs(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout seg = hbox();
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(PILL, 99, 0));
        final TextView note = text("", 12, MUTED, body(400));
        final String[] names = {"Dark", "Light", "System", "Auto"};
        final String[] notes = {
                "Always dark, like the cluster at night",
                "Always light",
                "Follows the phone's dark mode setting",
                "Light from 6 AM to 6 PM, dark at night, like the cluster"};
        final List<TextView> opts = new ArrayList<>();
        final Runnable paint = () -> {
            String cur = p.getString("theme", "dark");
            for (int i = 0; i < opts.size(); i++) {
                boolean on = THEMES[i].equals(cur);
                opts.get(i).setBackground(on ? round(HERO, 99, HERO_LINE) : null);
                opts.get(i).setTextColor(on ? ACCENT : SUB);
                opts.get(i).setTypeface(body(on ? 600 : 500));
                if (on) note.setText(notes[i]);
            }
        };
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView o = text(names[i], 13, SUB, body(500));
            o.setGravity(Gravity.CENTER);
            o.setClickable(true);
            o.setContentDescription(names[i] + " theme");
            o.setOnClickListener(v -> {
                p.edit().putString("theme", THEMES[idx]).apply();
                BikeService.log("Theme set to " + THEMES[idx]);
                if (wantDark() != darkNow) recreate();
                else paint.run();
            });
            seg.addView(o, new LinearLayout.LayoutParams(0, dp(36), 1));
            opts.add(o);
        }
        paint.run();
        box.addView(seg);
        note.setPadding(dp(4), dp(10), 0, 0);
        box.addView(note);
        g.addView(box);
    }

    private void updateSettings() {
        if (bikeTitle == null) return;
        String mac = BikeService.prefs(this).getString("mac", "");
        bikeTitle.setText(mac.isEmpty() ? "No bike yet" : BIKE_MODEL);
        bikeAddr.setText(mac.isEmpty() ? "Find it with Bluetooth" : mac);
        bikeBtn.setText(mac.isEmpty() ? "Find" : "Change");
    }

    /** Type the bike's Bluetooth address by hand (long press on the bike card). */
    private void editAddress() {
        final SharedPreferences p = BikeService.prefs(this);
        Sheet sheet = new Sheet("Bike address", "Only if Find does not show your bike.");
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        e.setText(p.getString("mac", ""));
        e.setHint("AA:BB:CC:DD:EE:FF");
        e.setHintTextColor(FAINT);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setTypeface(Typeface.MONOSPACE);
        e.setBackground(round(PILL, 14, 0));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        sheet.content.addView(e, marginTop(-1, -2, 14));
        sheet.button("Cancel", 0, null).button("Save", 1, () -> {
            p.edit().putString("mac", e.getText().toString().trim().toUpperCase(Locale.ROOT)).apply();
            updateSettings();
            toast("Saved");
        }).show();
    }

    /** Developer credit at the bottom; the name opens the app's GitHub repo. Version under it. */
    private void buildFooter(LinearLayout root) {
        TextView by = text("Developed by ", 12, MUTED, body(400));
        TextView name = text("Sameera Madushan", 12, ACCENT, body(500));
        name.setPaintFlags(name.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        name.setPadding(0, dp(8), 0, dp(4));   // bigger tap target
        name.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(REPO_URL)));
            } catch (Exception e) {
                toast("No browser found");
            }
        });
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(by);
        row.addView(name);
        root.addView(row, marginTop(-1, -2, 24));
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "?";
        }
        TextView ver = text(version, 11, FAINT, body(400));
        ver.setGravity(Gravity.CENTER);
        root.addView(ver, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateChecklist() {
        boolean perms = true;
        if (Build.VERSION.SDK_INT >= 31) perms = checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED;
        String listener = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean maps = listener != null && listener.contains(getPackageName());
        boolean battery = getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        chkPerms.set(perms);
        chkMaps.set(maps);
        chkBattery.set(battery);
        int missing = (perms ? 0 : 1) + (maps ? 0 : 1) + (battery ? 0 : 1);
        setupSummary.setText(missing == 0 ? "All set" : missing + " to fix");
        setupSummary.setTextColor(missing == 0 ? OK : WARN);
        // Open by itself while something needs fixing, unless the user has folded it themselves.
        if (!setupOpenedByUser) showSetup(missing > 0);
        int todo = (perms ? 0 : 1) + (maps ? 0 : 1) + (battery ? 0 : 1);
        setupCard.setVisibility(todo == 0 ? View.GONE : View.VISIBLE);
        setupNote.setText(todo == 1 ? "1 thing to fix in Settings" : todo + " things to fix in Settings");
    }

    // =====================================================================================
    // Live refresh
    // =====================================================================================

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (wantDark() != darkNow) { recreate(); return; }
            updateStatus();
            updateHero();
            updateHomeTiles();
            updateChecklist();
            if (tab == TAB_LOG) updateLog(false);
            if (tab == TAB_OVERVIEW) updateBike();
            h.postDelayed(this, 500);
        }
    };

    // =====================================================================================
    // Actions
    // =====================================================================================

    private void connect() {
        if (!bluetoothOn()) { askBluetooth(this::connect); return; }
        SharedPreferences p = BikeService.prefs(this);
        if (p.getString("mac", "").isEmpty()) { findBike(); return; }
        startForegroundService(new Intent(this, BikeService.class).setAction(BikeService.ACTION_CONNECT));
    }

    // ---------- Google Maps ----------
    static final String MAPS_PACKAGE = "com.google.android.apps.maps";

    /** The navigation card: back to the trip in progress, or just open Google Maps. */
    private void openMaps() {
        android.app.PendingIntent trip = MapsListener.tripIntent;
        if (trip != null && MapsListener.current != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    trip.send(this, 0, null, null, null, null, android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());
                } else {
                    trip.send();
                }
                return;
            } catch (Exception e) {
                BikeService.log("Could not reopen the Maps trip: " + e);
            }
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(MAPS_PACKAGE);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="));
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Google Maps is not installed");
        }
    }

    // ---------- Bluetooth off ----------
    private static final int REQ_BLUETOOTH = 7;
    private Runnable afterBluetooth;
    private android.app.Dialog btSheet;

    private boolean bluetoothOn() {
        android.bluetooth.BluetoothAdapter ad = getSystemService(android.bluetooth.BluetoothManager.class).getAdapter();
        return ad != null && ad.isEnabled();
    }

    /** "Bluetooth is off" pop-up; Turn on asks Android to switch it on, then runs then. */
    private void askBluetooth(Runnable then) {
        if (btSheet != null && btSheet.isShowing()) return;
        Sheet sh = new Sheet("Bluetooth is off", "Disha needs Bluetooth to reach your bike. Turn it on to continue.");
        sh.button("Not now", 0, null).button("Turn on", 1, () -> requestBluetooth(then)).show();
        btSheet = sh.dialog;
    }

    /** Android's own "Allow Disha to turn on Bluetooth?" prompt; Bluetooth settings if that is missing. */
    private void requestBluetooth(Runnable then) {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                != PackageManager.PERMISSION_GRANTED) {
            askPermissions();
            toast("Allow 'Nearby devices' first");
            return;
        }
        afterBluetooth = then;
        try {
            startActivityForResult(new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_BLUETOOTH);
        } catch (Exception e) {
            afterBluetooth = null;
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_BLUETOOTH) return;
        Runnable then = afterBluetooth;
        afterBluetooth = null;
        // The adapter takes a moment to come up after the user says yes.
        if (result == RESULT_OK && then != null) h.postDelayed(then, 800);
    }

    private void disconnect() {
        startService(new Intent(this, BikeService.class).setAction(BikeService.ACTION_DISCONNECT));
    }

    // ---------- finding the bike ----------
    private boolean scanning;

    /**
     * Scans for Bluetooth LE devices and shows every one in a live list: likely bikes first (TVS
     * service or a TVS-like name, marked with a star), then named devices, then unnamed ones, each
     * by signal strength. Tapping one connects to it and remembers it as "the bike".
     */
    @android.annotation.SuppressLint("MissingPermission")
    private void findBike() {
        if (scanning) return;
        android.bluetooth.BluetoothAdapter ad =
                getSystemService(android.bluetooth.BluetoothManager.class).getAdapter();
        if (ad == null) { toast("This phone has no Bluetooth"); return; }
        if (!ad.isEnabled()) { askBluetooth(this::findBike); return; }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_SCAN")
                != PackageManager.PERMISSION_GRANTED) { askPermissions(); toast("Allow 'Nearby devices', then try again"); return; }
        final android.bluetooth.le.BluetoothLeScanner sc = ad.getBluetoothLeScanner();
        if (sc == null) { toast("Bluetooth scanner not available"); return; }

        final java.util.LinkedHashMap<String, android.bluetooth.le.ScanResult> found = new java.util.LinkedHashMap<>();
        final List<android.bluetooth.le.ScanResult> shown = new ArrayList<>();
        final Sheet sheet = new Sheet("Finding your bike", "Ignition ON, and TVS Connect closed.");
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.addView(list);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(1));
        sl.setMargins(0, dp(12), 0, 0);
        sheet.content.addView(listScroll, sl);
        final Runnable[] stopRef = new Runnable[1];

        final Runnable relist = () -> {
            shown.clear();
            shown.addAll(found.values());
            shown.sort((x, y) -> {
                int gx = rank(x), gy = rank(y);
                return gx != gy ? gx - gy : y.getRssi() - x.getRssi();
            });
            list.removeAllViews();
            for (final android.bluetooth.le.ScanResult r : shown) {
                View row = deviceRow(r);
                row.setOnClickListener(v -> {
                    sheet.dialog.dismiss();
                    if (stopRef[0] != null) stopRef[0].run();
                    useBike(r);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(8));
                list.addView(row, lp);
            }
            // Grow with the list, up to about five rows, then scroll.
            listScroll.getLayoutParams().height = shown.isEmpty() ? dp(1) : Math.min(shown.size() * dp(66), dp(330));
            listScroll.requestLayout();
        };
        final android.bluetooth.le.ScanCallback cb = new android.bluetooth.le.ScanCallback() {
            @Override public void onScanResult(int type, android.bluetooth.le.ScanResult r) {
                h.post(() -> {
                    boolean isNew = !found.containsKey(r.getDevice().getAddress());
                    found.put(r.getDevice().getAddress(), r);
                    if (isNew) relist.run();
                });
            }
        };
        final Runnable stop = () -> {
            if (!scanning) return;
            scanning = false;
            try { sc.stopScan(cb); } catch (Exception ignored) { }
            BikeService.log("Scan: " + found.size() + " devices");
            for (android.bluetooth.le.ScanResult r : found.values())
                if (looksLikeBike(r)) BikeService.log("  bike? " + describe(r));
            relist.run(); // refresh signal strengths
            sheet.spinner.setVisibility(View.GONE);
            sheet.title.setText(found.isEmpty() ? "Nothing found" : "Tap your bike");
            sheet.message.setText(found.isEmpty() ? "Is the ignition on and TVS Connect closed? Then try again."
                    : "\u2605 = looks like a TVS bike. Nearest first.");
        };
        stopRef[0] = stop;

        android.bluetooth.le.ScanSettings set = new android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        // The bike only advertises while nothing is connected to it: drop our own link first.
        disconnect();
        scanning = true;
        BikeService.log("Scanning for the bike ...");
        sc.startScan(null, set, cb);
        h.postDelayed(stop, 15000);

        sheet.spinner.setVisibility(View.VISIBLE);
        sheet.button("Cancel", 0, null);
        sheet.dialog.setOnDismissListener(d -> { h.removeCallbacks(stop); stop.run(); });
        sheet.show();
    }

    /** One device in the picker: name (with a star badge if it looks like a bike), address, signal. */
    private View deviceRow(android.bluetooth.le.ScanResult r) {
        boolean bike = looksLikeBike(r);
        String name = nameOf(r);
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(ripple(round(bike ? ACCENT_DARK : SURFACE2, 14, bike ? ACCENT : 0)));
        row.setClickable(true);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label((bike ? "\u2605  " : "") + (name == null ? "Unnamed device" : name), 15,
                name == null ? MUTED : TEXT, "sans-serif-medium"));
        TextView addr = label(r.getDevice().getAddress(), 12, MUTED, "monospace");
        addr.setTypeface(Typeface.MONOSPACE);
        texts.addView(addr);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        int rssi = r.getRssi();
        String sig = rssi >= -60 ? "Very close" : rssi >= -75 ? "Near" : "Far";
        TextView signal = label(sig, 12, rssi >= -60 ? ACCENT : MUTED, "sans-serif-medium");
        row.addView(signal);
        return row;
    }

    /** List order: likely bikes, then named devices, then unnamed ones. */
    private static int rank(android.bluetooth.le.ScanResult r) {
        return looksLikeBike(r) ? 0 : nameOf(r) != null ? 1 : 2;
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static String nameOf(android.bluetooth.le.ScanResult r) {
        String name = r.getDevice().getName();
        if (name == null && r.getScanRecord() != null) name = r.getScanRecord().getDeviceName();
        return name;
    }

    private static boolean looksLikeBike(android.bluetooth.le.ScanResult r) {
        android.bluetooth.le.ScanRecord rec = r.getScanRecord();
        if (rec != null && rec.getServiceUuids() != null) {
            for (android.os.ParcelUuid u : rec.getServiceUuids())
                if (u.getUuid().equals(BikeService.SERVICE)) return true;
        }
        String name = nameOf(r);
        if (name == null) return false;
        String n = name.toUpperCase(Locale.ROOT);
        return n.contains("TVS") || n.contains("RAIDER") || n.contains("SMARTX") || n.contains("NTORQ")
                || n.contains("APACHE") || n.contains("RONIN") || n.contains("JUPITER");
    }

    private static String describe(android.bluetooth.le.ScanResult r) {
        String name = nameOf(r);
        return (name == null ? "(no name)" : name) + "  " + r.getDevice().getAddress() + "  " + r.getRssi() + " dBm";
    }

    private void useBike(android.bluetooth.le.ScanResult r) {
        String mac = r.getDevice().getAddress(), name = nameOf(r);
        SharedPreferences.Editor e = BikeService.prefs(this).edit().putString("mac", mac);
        if (name != null) e.putString("bikeName", name);
        e.apply();
        updateSettings();
        BikeService.log("Bike address set to " + mac + (name == null ? "" : " (" + name + ")"));
        toast("Found " + (name == null ? mac : name) + ". Connecting...");
        startForegroundService(new Intent(this, BikeService.class).setAction(BikeService.ACTION_CONNECT));
    }

    /** Asks Android not to pause us with the screen off (Honor/Huawei pause background apps hard). */
    private void keepRunning() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
        toast("Also: Settings > Apps > Disha > Battery > Manage manually, turn all ON");
    }

    private String header() {
        SharedPreferences p = BikeService.prefs(this);
        return "Disha log  " + new java.util.Date() + "\nPhone: " + Build.MANUFACTURER + " " + Build.MODEL
                + " Android " + Build.VERSION.RELEASE + "\nBike: " + p.getString("bikeName", "?") + " "
                + p.getString("mac", "") + "  status: " + BikeService.status + "\nscreenOffAllowed="
                + getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())
                + "\nLast Maps: " + MapsListener.lastRaw + "\n\n";
    }

    /**
     * Writes the whole log to a .txt file and shares it as an attachment (a huge log is too big to
     * paste). Only the newest file is kept: older ones are deleted first.
     */
    private void shareLog() {
        java.io.File dir = LogFileProvider.dir(this);
        deleteLogFiles();
        if (!dir.isDirectory() && !dir.mkdirs()) { toast("Could not create the log file"); return; }
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new java.util.Date());
        java.io.File f = new java.io.File(dir, "Disha-log-" + stamp + ".txt");
        try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f),
                java.nio.charset.StandardCharsets.UTF_8)) {
            // Road and place names are hidden in the shared copy; the log on the phone keeps them.
            w.write(LogRedactor.redact(header() + BikeService.fullLog()));
        } catch (java.io.IOException e) {
            toast("Could not write the log file: " + e.getMessage());
            return;
        }
        android.net.Uri uri = LogFileProvider.uriFor(f);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Disha log " + stamp)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(android.content.ClipData.newRawUri(f.getName(), uri)); // carries the read grant
        startActivity(Intent.createChooser(send, "Share Disha log"));
    }

    private void deleteLogFiles() {
        java.io.File[] old = LogFileProvider.dir(this).listFiles();
        if (old != null) for (java.io.File o : old) //noinspection ResultOfMethodCallIgnored
            o.delete();
    }

    private void clearLog() {
        BikeService.clearLog();
        deleteLogFiles();
        updateLog(true);
        toast("Log cleared");
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            need.add("android.permission.BLUETOOTH_CONNECT");
            need.add("android.permission.BLUETOOTH_SCAN");
        } else {
            need.add("android.permission.ACCESS_FINE_LOCATION");
        }
        if (Build.VERSION.SDK_INT >= 33) need.add("android.permission.POST_NOTIFICATIONS");
        List<String> missing = new ArrayList<>();
        for (String s : need) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) missing.add(s);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 1);
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    // =====================================================================================
    // Small UI kit
    // =====================================================================================

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView text(String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    /** Older call sites name a system family; map those onto the app's fonts. */
    private TextView label(String s, int sp, int color, String family) {
        Typeface tf = "monospace".equals(family) ? Typeface.MONOSPACE
                : family.contains("medium") ? body(600) : body(400);
        return text(s, sp, color, tf);
    }

    /** Small grey capitals above a value: "TIME LEFT", "FUEL". */
    private TextView caps(String s) {
        TextView t = text(s, 11, MUTED, body(600));
        t.setLetterSpacing(0.12f);
        return t;
    }

    /** Grey line above a big title, as at the top of each tab. */
    private LinearLayout pageTitle(String over, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        if (over != null) c.addView(text(over, 13, MUTED, body(400)));
        TextView t = text(title, 28, TEXT, display(600));
        t.setPadding(0, dp(2), 0, 0);
        c.addView(t);
        return c;
    }

    private TextView sectionLabel(String s) {
        TextView t = caps(s);
        t.setPadding(dp(4), dp(22), dp(4), dp(10));
        return t;
    }

    /** A rounded box holding a list of rows split by thin lines. */
    private LinearLayout group(LinearLayout root) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(TILE, 22, TILE_LINE));
        root.addView(g, new LinearLayout.LayoutParams(-1, -2));
        return g;
    }

    private void divider(LinearLayout g) {
        View d = new View(this);
        d.setBackgroundColor(TILE_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(16), 0, dp(16), 0);
        g.addView(d, lp);
    }

    /** An icon centred in a rounded square. */
    private FrameLayout iconTile(int kind, int sizeDp, int radiusDp, int bg, int fg) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(round(bg, radiusDp, 0));
        int icon = Math.round(sizeDp * 0.52f);
        f.addView(new LineIcon(this, kind, fg), new FrameLayout.LayoutParams(dp(icon), dp(icon), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    private LinearLayout.LayoutParams marginTop(int w, int hgt, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, hgt);
        lp.setMargins(0, dp(topDp), 0, 0);
        return lp;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), content, null);
    }

    private TextView pillButton(String s, boolean primary) {
        TextView b = text(s, 16, primary ? ON_ACCENT : TEXT, body(600));
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(primary ? round(ACCENT, 99, 0) : round(PILL, 99, PILL_LINE)));
        b.setClickable(true);
        return b;
    }

    private TextView chip(String s) {
        TextView b = text(s, 13, TEXT, body(600));
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(round(PILL, 14, HERO_LINE)));
        b.setClickable(true);
        return b;
    }

    interface OnToggle { void changed(boolean on); }

    /** Icon, title and line of help, with a switch saved under key (default on). */
    private void switchRow(LinearLayout g, int icon, String title, String sub, final String key, final OnToggle cb) {
        switchRow(g, icon, title, sub, key, true, cb);
    }

    private void switchRow(LinearLayout g, int icon, String title, String sub, final String key, final boolean def, final OnToggle cb) {
        final SharedPreferences p = BikeService.prefs(this);
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.addView(iconTile(icon, 36, 12, SELECTED, ACCENT));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(10), 0);
        texts.addView(text(title, 14, TEXT, body(600)));
        TextView st = text(sub, 12, MUTED, body(400));
        st.setPadding(0, dp(2), 0, 0);
        texts.addView(st);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        final Toggle t = new Toggle(p.getBoolean(key, def));
        t.setContentDescription(title);
        View.OnClickListener flip = v -> {
            t.set(!t.on);
            p.edit().putBoolean(key, t.on).apply();
            if (cb != null) cb.changed(t.on);
        };
        t.setOnClickListener(flip);
        row.setOnClickListener(flip);
        row.addView(t, new LinearLayout.LayoutParams(dp(50), dp(30)));
        g.addView(row);
    }

    /**
     * "Alert at   [-]  80 km/h  [+]": a number saved under key, changed in steps. unit null means
     * fuel bars ("1 bar", "2 bars").
     */
    private void stepperRow(LinearLayout g, String title, final String key, int def, final int min, final int max,
                            final int step, final String unit) {
        final SharedPreferences p = BikeService.prefs(this);
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(64), 0, dp(16), dp(13));
        row.addView(text(title, 13, SUB, body(500)), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView value = text("", 15, TEXT, display(600));
        value.setGravity(Gravity.CENTER);
        final int[] v = {Math.max(min, Math.min(max, p.getInt(key, def)))};
        final Runnable show = () -> value.setText(unit != null ? v[0] + unit : v[0] + (v[0] == 1 ? " bar" : " bars"));
        show.run();
        TextView minus = stepButton("−"), plus = stepButton("+");
        minus.setContentDescription("Lower " + title.toLowerCase(Locale.ROOT));
        plus.setContentDescription("Raise " + title.toLowerCase(Locale.ROOT));
        View.OnClickListener change = btn -> {
            int nv = Math.max(min, Math.min(max, v[0] + (btn == plus ? step : -step)));
            if (nv == v[0]) return;
            v[0] = nv;
            p.edit().putInt(key, nv).apply();
            show.run();
            BikeService.log(title + " set to " + value.getText());
        };
        minus.setOnClickListener(change);
        plus.setOnClickListener(change);
        row.addView(minus, new LinearLayout.LayoutParams(dp(36), dp(36)));
        row.addView(value, new LinearLayout.LayoutParams(dp(78), -2));
        row.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));
        g.addView(row);
    }

    private TextView stepButton(String s) {
        TextView b = text(s, 18, TEXT, body(600));
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(round(PILL, 99, PILL_LINE)));
        b.setClickable(true);
        return b;
    }

    /** The design's switch: a pill track with a round knob, teal when on. */
    private final class Toggle extends View {
        boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        Toggle(boolean on) {
            super(MainActivity.this);
            this.on = on;
            setClickable(true);
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Switch");
                    info.setCheckable(true);
                    info.setChecked(Toggle.this.on);
                }
            });
        }

        void set(boolean v) { on = v; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), pad = dp(3), knob = hh - 2 * pad;
            paint.setColor(on ? ACCENT : TOGGLE_OFF);
            r.set(0, 0, w, hh);
            c.drawRoundRect(r, hh / 2, hh / 2, paint);
            paint.setColor(on ? ON_ACCENT : KNOB_OFF);
            float cx = on ? w - pad - knob / 2 : pad + knob / 2;
            c.drawCircle(cx, hh / 2, knob / 2, paint);
        }
    }

    /** One line of the setup list: green tick or amber "!", text, and a Fix button when needed. */
    private final class CheckRow {
        final FrameLayout badge;
        final LineIcon tick;
        final TextView bang, fix;

        CheckRow(LinearLayout parent, String title, String sub, Runnable action) {
            LinearLayout row = hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(13), dp(16), dp(13));
            badge = new FrameLayout(MainActivity.this);
            tick = new LineIcon(MainActivity.this, LineIcon.CHECK, ON_ACCENT);
            badge.addView(tick, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
            bang = text("!", 14, ON_WARN, body(700));
            bang.setGravity(Gravity.CENTER);
            badge.addView(bang, new FrameLayout.LayoutParams(-1, -1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(26), dp(26)));
            LinearLayout texts = new LinearLayout(MainActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(8), 0);
            texts.addView(text(title, 14, TEXT, body(600)));
            TextView st = text(sub, 12, MUTED, body(400));
            st.setPadding(0, dp(2), 0, 0);
            texts.addView(st);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            fix = text("Fix", 13, WARN, body(600));
            fix.setGravity(Gravity.CENTER);
            fix.setBackground(ripple(round(WARN_BG, 99, 0)));
            fix.setClickable(true);
            fix.setOnClickListener(v -> action.run());
            row.addView(fix, new LinearLayout.LayoutParams(dp(60), dp(34)));
            parent.addView(row);
        }

        void set(boolean ok) {
            badge.setBackground(round(ok ? OK : WARN, 99, 0));
            tick.setVisibility(ok ? View.VISIBLE : View.GONE);
            bang.setVisibility(ok ? View.GONE : View.VISIBLE);
            fix.setVisibility(ok ? View.GONE : View.VISIBLE);
        }
    }

    /** A pop-up in the app's own style (dark card, app fonts, pill buttons) instead of the grey system one. */
    private final class Sheet {
        final android.app.Dialog dialog;
        final TextView title, message;
        final LinearLayout content, buttons;
        final android.widget.ProgressBar spinner;

        Sheet(String titleText, String messageText) {
            dialog = new android.app.Dialog(MainActivity.this);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(22), dp(20), dp(22), dp(18));
            box.setBackground(round(HERO, 24, HERO_LINE));

            LinearLayout head = hbox();
            head.setGravity(Gravity.CENTER_VERTICAL);
            title = text(titleText, 20, TEXT, display(600));
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            spinner = new android.widget.ProgressBar(MainActivity.this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
            spinner.setVisibility(View.GONE);
            head.addView(spinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
            box.addView(head);

            message = text(messageText == null ? "" : messageText, 14, MUTED, body(400));
            message.setPadding(0, dp(8), 0, 0);
            message.setVisibility(messageText == null ? View.GONE : View.VISIBLE);
            box.addView(message);

            content = new LinearLayout(MainActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            box.addView(content);

            buttons = hbox();
            buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            buttons.setPadding(0, dp(18), 0, 0);
            box.addView(buttons);

            dialog.setContentView(box);
            android.view.Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
                w.setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setDimAmount(0.65f);
            }
        }

        /** kind: 0 = plain, 1 = main action (teal), 2 = deletes something (red). Closes the pop-up first. */
        Sheet button(String text, int kind, Runnable action) {
            TextView b = MainActivity.this.text(text, 14, kind == 0 ? TEXT : kind == 2 ? RED_TEXT : ON_ACCENT, body(600));
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(20), 0, dp(20), 0);
            b.setBackground(ripple(kind == 1 ? round(ACCENT, 99, 0) : kind == 2 ? round(RED_BG, 99, RED_LINE)
                    : round(PILL, 99, PILL_LINE)));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                if (action != null) action.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
            if (buttons.getChildCount() > 0) lp.setMargins(dp(10), 0, 0, 0);
            buttons.addView(b, lp);
            return this;
        }

        Sheet show() { dialog.show(); return this; }
    }

    /** "Are you sure?" with Cancel and a red delete button. */
    private void confirmDelete(String title, String message, String yes, Runnable action) {
        new Sheet(title, message).button("Cancel", 0, null).button(yes, 2, action).show();
    }
}
