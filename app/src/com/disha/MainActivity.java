package com.disha;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    // ---------- palette (matches the Raider's teal cluster) ----------
    private static final int BG = 0xFF071012, SURFACE = 0xFF0F1B1E, SURFACE2 = 0xFF162629,
            LINE = 0x1AFFFFFF, ACCENT = 0xFF2DD4C4, ACCENT_DARK = 0xFF0B3B3A, TEXT = 0xFFEAF5F6,
            MUTED = 0xFF8AA2A6, OK = 0xFF3BE39E, WARN = 0xFFFFB547, BAD = 0xFFFF6B6B;

    private final Handler h = new Handler(Looper.getMainLooper());

    // live views
    private TextView statusTitle, statusSub, pillText, distView, instrView, roadView,
            timeLeftView, distLeftView, etaView, logView;
    private View pillDot, heroCard;
    private ArrowView arrow;
    private TextView primaryBtn;
    private LinearLayout checklist;
    private CheckRow chkPerms, chkMaps, chkBattery;
    private EditText macEdit;
    private View logSection;
    private boolean blink;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        BikeService.initLog(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences p = BikeService.prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(32));

        buildHeader(root);
        buildHero(root);
        buildChecklist(root);
        buildActions(root);
        buildRides(root);
        buildSettings(root, p);
        buildLog(root);
        buildFooter(root);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
        askPermissions();
    }

    @Override protected void onResume() {
        super.onResume();
        h.post(refresh);
        h.post(rideUiTick);
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

    private boolean autoScanned;

    static final String REPO_URL = "https://github.com/sameeramperera/Disha";

    /** Developer credit at the bottom; the name opens the app's GitHub repo. */
    private void buildFooter(LinearLayout root) {
        TextView by = label("Developed by ", 12, MUTED, "sans-serif");
        TextView name = label("Sameera Madushan", 12, ACCENT, "sans-serif-medium");
        name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        name.setPadding(0, dp(8), 0, dp(8));   // bigger tap target
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(24), 0, 0);
        root.addView(row, lp);
    }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresh); h.removeCallbacks(rideUiTick); }

    // =====================================================================================
    // Layout
    // =====================================================================================

    private void buildHeader(LinearLayout root) {
        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("DISHA", 13, ACCENT, "sans-serif-medium");
        brand.setLetterSpacing(0.25f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout pill = hbox();
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(5), dp(12), dp(5));
        pill.setBackground(round(SURFACE2, 99, 0));
        pillDot = new View(this);
        pillDot.setBackground(round(MUTED, 99, 0));
        pill.addView(pillDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        pillText = label("", 12, TEXT, "sans-serif-medium");
        pillText.setPadding(dp(7), 0, 0, 0);
        pill.addView(pillText);
        top.addView(pill);
        root.addView(top);

        statusTitle = label("", 28, TEXT, "sans-serif-medium");
        statusTitle.setPadding(0, dp(14), 0, 0);
        root.addView(statusTitle);
        statusSub = label("", 14, MUTED, "sans-serif");
        statusSub.setPadding(0, dp(2), 0, dp(4));
        root.addView(statusSub);
    }

    private void buildHero(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(16));
        heroCard = card;

        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout arrowBox = new FrameLayout(this);
        arrowBox.setBackground(round(0x26FFFFFF, 26, 0));
        arrow = new ArrowView(this);
        arrow.setColors(TEXT, 0x55FFFFFF);
        int pad = dp(14);
        arrowBox.setPadding(pad, pad, pad, pad);
        arrowBox.addView(arrow, new FrameLayout.LayoutParams(-1, -1));
        row.addView(arrowBox, new LinearLayout.LayoutParams(dp(112), dp(112)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), 0, 0, 0);
        distView = label("", 44, TEXT, "sans-serif-condensed");
        distView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        distView.setIncludeFontPadding(false);
        col.addView(distView);
        instrView = label("", 18, TEXT, "sans-serif-medium");
        instrView.setPadding(0, dp(2), 0, 0);
        col.addView(instrView);
        roadView = label("", 14, 0xCCEAF5F6, "sans-serif");
        roadView.setMaxLines(2);
        col.addView(roadView);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);

        View div = new View(this);
        div.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(1));
        dl.setMargins(0, dp(18), 0, dp(14));
        card.addView(div, dl);

        LinearLayout stats = hbox();
        timeLeftView = stat(stats, "TIME LEFT");
        distLeftView = stat(stats, "DISTANCE");
        etaView = stat(stats, "ETA");
        card.addView(stats);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(16), 0, 0);
        root.addView(card, lp);
    }

    private TextView stat(LinearLayout row, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 11, 0x99EAF5F6, "sans-serif-medium");
        t.setLetterSpacing(0.12f);
        c.addView(t);
        TextView v = label("--", 17, TEXT, "sans-serif-medium");
        v.setPadding(0, dp(3), 0, 0);
        c.addView(v);
        row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        return v;
    }

    private TextView rideLine;
    private boolean wasRiding;

    private final Runnable rideUiTick = new Runnable() {
        @Override public void run() {
            RideTracker.Ride r = BikeService.currentRide;
            // "Riding now" only while the bike is actually sending. After the ignition goes off the
            // ride stays open for 2 minutes (in case it was just a stall) but is not shown as riding.
            boolean live = r != null && BikeService.instance != null
                    && System.currentTimeMillis() - BikeService.telemetryAt < 10_000;
            rideLine.setText(live ? "Riding now  \u00b7  " + RideTracker.summary(r) : "");
            rideLine.setVisibility(live ? View.VISIBLE : View.GONE);
            if (wasRiding && r == null) refreshRides(); // a ride just ended
            wasRiding = r != null;
            h.postDelayed(this, 1000);
        }
    };

    // ---------- rides ----------
    private LinearLayout ridesBody;

    private void buildRides(LinearLayout root) {
        ridesBody = section(root, "Rides", "Trips recorded from the bike's odometer");
        // The ride in progress, shown under the header so it is visible with the card folded.
        rideLine = label("", 13, ACCENT, "sans-serif-medium");
        rideLine.setPadding(0, dp(8), 0, 0);
        rideLine.setVisibility(View.GONE);
        ((LinearLayout) ridesBody.getParent()).addView(rideLine, 1);
    }

    /** Rebuilds the ride list: totals for the last 7 days, then each ride, newest first. */
    private void refreshRides() {
        if (ridesBody == null) return;
        ridesBody.removeAllViews();
        List<RideTracker.Ride> rides = RideStore.all(this);
        if (rides.isEmpty()) {
            TextView t = label("No rides yet. Rides are recorded while the app is connected to the bike.",
                    13, MUTED, "sans-serif");
            t.setPadding(0, dp(10), 0, 0);
            ridesBody.addView(t);
            return;
        }
        long weekAgo = System.currentTimeMillis() - 7L * 24 * 3600_000;
        int n = 0; double km = 0;
        for (RideTracker.Ride r : rides) if (r.start >= weekAgo) { n++; km += r.km(); }
        TextView total = label(String.format(Locale.ROOT, "Last 7 days: %d ride%s  \u00b7  %.1f km", n, n == 1 ? "" : "s", km),
                14, TEXT, "sans-serif-medium");
        total.setPadding(0, dp(10), 0, dp(4));
        ridesBody.addView(total);

        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("EEE d MMM  \u00b7  HH:mm", Locale.ENGLISH);
        for (int i = 0; i < rides.size() && i < 30; i++) {
            RideTracker.Ride r = rides.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(round(SURFACE2, 14, 0));
            row.addView(label(when.format(new java.util.Date(r.start)), 13, TEXT, "sans-serif-medium"));
            TextView sum = label(RideTracker.summary(r), 12, MUTED, "sans-serif");
            sum.setPadding(0, dp(2), 0, 0);
            row.addView(sum);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(8), 0, 0);
            ridesBody.addView(row, lp);
        }
        TextView clear = chip("Clear rides");
        clear.setOnClickListener(v -> confirmDelete("Delete all rides?",
                "The ride history is removed from this phone.", "Delete",
                () -> { RideStore.clear(this); refreshRides(); }));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(42));
        cl.setMargins(0, dp(12), 0, 0);
        ridesBody.addView(clear, cl);
    }

    private void buildChecklist(LinearLayout root) {
        checklist = card(root);
        TextView t = label("Setup", 15, TEXT, "sans-serif-medium");
        t.setPadding(0, 0, 0, dp(6));
        checklist.addView(t);
        chkPerms = new CheckRow(checklist, "Bluetooth & notifications", "Lets the app talk to the bike", this::askPermissions);
        chkMaps = new CheckRow(checklist, "Read Google Maps", "Needed to see the turn arrows",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        chkBattery = new CheckRow(checklist, "Run with screen off", "Stops the phone pausing navigation", this::keepRunning);
    }

    private void buildActions(LinearLayout root) {
        LinearLayout row = hbox();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(16), 0, 0);
        root.addView(row, lp);

        primaryBtn = pillButton("Connect", true);
        primaryBtn.setOnClickListener(v -> {
            BikeService s = BikeService.instance;
            if (s != null && s.isActive()) disconnect();
            else connect();
        });
        row.addView(primaryBtn, new LinearLayout.LayoutParams(0, dp(52), 1.4f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        TextView find = pillButton("Find bike", false);
        find.setOnClickListener(v -> findBike());
        row.addView(find, new LinearLayout.LayoutParams(0, dp(52), 1f));
    }

    private void buildSettings(LinearLayout root, SharedPreferences p) {
        LinearLayout body = section(root, "Settings", "Bike address");

        TextView l = label("BIKE BLUETOOTH ADDRESS", 11, MUTED, "sans-serif-medium");
        l.setLetterSpacing(0.12f);
        l.setPadding(0, dp(10), 0, dp(6));
        body.addView(l);
        LinearLayout macRow = hbox();
        macRow.setGravity(Gravity.CENTER_VERTICAL);
        macEdit = new EditText(this);
        macEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        macEdit.setText(p.getString("mac", ""));
        macEdit.setHint("Use Find bike");
        macEdit.setHintTextColor(MUTED);
        macEdit.setTextColor(TEXT);
        macEdit.setTextSize(15);
        macEdit.setTypeface(Typeface.MONOSPACE);
        macEdit.setBackground(round(SURFACE2, 14, 0));
        macEdit.setPadding(dp(14), dp(12), dp(14), dp(12));
        macRow.addView(macEdit, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = chip("Save");
        save.setOnClickListener(v -> {
            p.edit().putString("mac", macEdit.getText().toString().trim().toUpperCase(Locale.ROOT)).apply();
            toast("Saved");
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(76), dp(46));
        sl.setMargins(dp(8), 0, 0, 0);
        macRow.addView(save, sl);
        body.addView(macRow);


    }

    private void buildLog(LinearLayout root) {
        LinearLayout body = section(root, "Log", "Share this when something looks wrong");
        logSection = body;
        LinearLayout row = hbox();
        TextView share = chip("Share log");
        share.setOnClickListener(v -> shareLog());
        TextView copy = chip("Clear log");
        copy.setOnClickListener(v -> confirmDelete("Clear the log?",
                "Deletes all log lines and the saved log files. Share it first if you still need it.", "Clear",
                this::clearLog));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(42), 1);
        a.setMargins(0, dp(8), dp(8), 0);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(42), 1);
        c.setMargins(0, dp(8), 0, 0);
        row.addView(share, a);
        row.addView(copy, c);
        body.addView(row);

        logView = label("", 10, MUTED, "monospace");
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));

        // Fixed-height box with its own scroll bar, so the page does not grow with the log.
        logScroll = new ScrollView(this);
        logScroll.setBackground(round(BG, 12, 0));
        logScroll.setVerticalScrollBarEnabled(true);
        logScroll.setScrollbarFadingEnabled(false);          // always visible
        logScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable thumb = round(ACCENT, 4, 0);
            thumb.setSize(dp(4), dp(40));
            logScroll.setVerticalScrollbarThumbDrawable(thumb);
            GradientDrawable track = round(SURFACE2, 4, 0);
            track.setSize(dp(4), dp(40));
            logScroll.setVerticalScrollbarTrackDrawable(track);
        }
        // Scrolling inside the box must not scroll the whole page.
        logScroll.setOnTouchListener((v, e) -> {
            int act = e.getActionMasked();
            v.getParent().requestDisallowInterceptTouchEvent(
                    act != android.view.MotionEvent.ACTION_UP && act != android.view.MotionEvent.ACTION_CANCEL);
            return false;
        });
        logScroll.addView(logView, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(360));
        lp.setMargins(0, dp(12), 0, 0);
        body.addView(logScroll, lp);
    }

    private ScrollView logScroll;
    private String lastLogText = "";

    /**
     * Newest lines are at the top. While the user has scrolled down to read older lines, hold the
     * text still (new lines would push it around); updates resume once they are back at the top.
     */
    private void updateLog(boolean force) {
        if (!force && logScroll.getScrollY() > dp(8)) return;
        String t = BikeService.logText(400);
        if (!force && t.equals(lastLogText)) return;
        lastLogText = t;
        logView.setText(t);
        if (force) logScroll.scrollTo(0, 0);
    }

    // =====================================================================================
    // Live refresh
    // =====================================================================================

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateStatus();
            updateHero();
            updateChecklist();
            if (logSection.getVisibility() == View.VISIBLE) updateLog(false);
            h.postDelayed(this, 500);
        }
    };

    private void updateStatus() {
        String st = BikeService.status;
        BikeService s = BikeService.instance;
        boolean ready = s != null && s.isReady();
        boolean standby = !ready && s != null && st.startsWith("Waiting for the bike");
        boolean connecting = !ready && !standby && s != null && (st.startsWith("Connect") || st.contains("retrying"));
        int dot;
        String pill, title;
        if (ready) { dot = OK; pill = "Connected"; title = MapsListener.current != null ? "Navigating" : "Ready to ride"; }
        else if (standby) { dot = MUTED; pill = "Standby"; title = "Bike is off"; }
        else if (connecting) { dot = WARN; pill = "Connecting"; title = "Looking for your bike"; }
        else if (st.contains("Bluetooth is off")) { dot = BAD; pill = "Bluetooth off"; title = "Turn Bluetooth on"; }
        else { dot = MUTED; pill = "Offline"; title = "Not connected"; }
        blink = !blink;
        pillDot.setBackground(round(dot, 99, 0));
        pillDot.setAlpha(connecting && blink ? 0.3f : 1f);
        pillText.setText(pill);
        statusTitle.setText(title);

        SharedPreferences p = BikeService.prefs(this);
        String name = p.getString("bikeName", "TVS Raider");
        String mac = p.getString("mac", "");
        if (mac.isEmpty()) statusSub.setText("No bike chosen yet  \u00b7  tap Find bike");
        else statusSub.setText(ready ? name + "  ·  " + mac
                : standby ? "Connects by itself when you switch the ignition on"
                : connecting ? "Ignition on and TVS Connect closed?  ·  " + mac
                : "Tap Connect, or Find bike to scan  ·  " + mac);
        boolean linked = ready || connecting || standby;   // the service is holding or seeking the link
        primaryBtn.setText(standby ? "Stop waiting" : linked ? "Disconnect" : "Connect");
        primaryBtn.setBackground(linked ? ripple(round(SURFACE2, 99, LINE)) : ripple(round(ACCENT, 99, 0)));
        primaryBtn.setTextColor(linked ? TEXT : 0xFF04201F);
    }

    private void updateHero() {
        TvsProtocol.Nav n = MapsListener.current;
        if (n == null) {
            heroCard.setBackground(round(SURFACE, 28, LINE));
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
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                close ? new int[]{0xFF1C6B5F, 0xFF0B3B3A} : new int[]{0xFF0F5551, 0xFF0A2629});
        g.setCornerRadius(dp(28));
        heroCard.setBackground(g);
        arrow.setPicto(n.picto);
        distView.setText(distance(n.distNextM, n.picto));
        distView.setTextColor(close ? WARN : TEXT);
        instrView.setText(n.text);
        roadView.setText(MapsListener.road);
        timeLeftView.setText(minutes(n.minutesLeft));
        distLeftView.setText(km(n.distLeftM));
        etaView.setText(MapsListener.eta.isEmpty() ? "--" : MapsListener.eta);
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
        checklist.setVisibility(perms && maps && battery ? View.GONE : View.VISIBLE);
    }

    /** "150 m" / "1.8 km" with the unit smaller. */
    private CharSequence distance(int m, int picto) {
        if (picto == TvsProtocol.P_ARRIVE) return "Here";
        if (picto == TvsProtocol.P_REROUTING) return "...";
        String num, unit;
        if (m >= 1000) { num = String.format(Locale.ROOT, m >= 10000 ? "%.0f" : "%.1f", m / 1000.0); unit = " km"; }
        else { num = String.valueOf(m); unit = " m"; }
        SpannableString s = new SpannableString(num + unit);
        s.setSpan(new AbsoluteSizeSpan(20, true), num.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xCCEAF5F6), num.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
    // Actions
    // =====================================================================================

    private void connect() {
        SharedPreferences p = BikeService.prefs(this);
        String typed = macEdit.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (!typed.isEmpty()) p.edit().putString("mac", typed).apply();
        if (p.getString("mac", "").isEmpty()) { findBike(); return; }
        startForegroundService(new Intent(this, BikeService.class).setAction(BikeService.ACTION_CONNECT));
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
        if (ad == null || !ad.isEnabled()) { toast("Turn Bluetooth on first"); return; }
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
        macEdit.setText(mac);
        SharedPreferences.Editor e = BikeService.prefs(this).edit().putString("mac", mac);
        if (name != null) e.putString("bikeName", name);
        e.apply();
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
            w.write(header());
            w.write(BikeService.fullLog());
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

    private TextView label(String s, int sp, int color, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null);
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(round(SURFACE, 22, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(14), 0, 0);
        root.addView(c, lp);
        return c;
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
            box.setBackground(round(SURFACE, 24, LINE));

            LinearLayout head = hbox();
            head.setGravity(Gravity.CENTER_VERTICAL);
            title = label(titleText, 18, TEXT, "sans-serif-medium");
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            spinner = new android.widget.ProgressBar(MainActivity.this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
            spinner.setVisibility(View.GONE);
            head.addView(spinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
            box.addView(head);

            message = label(messageText == null ? "" : messageText, 14, MUTED, "sans-serif");
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
            TextView b = label(text, 14, kind == 0 ? TEXT : 0xFF04201F, "sans-serif-medium");
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(20), 0, dp(20), 0);
            int bg = kind == 1 ? ACCENT : kind == 2 ? BAD : SURFACE2;
            b.setBackground(ripple(round(bg, 99, kind == 0 ? LINE : 0)));
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

    private TextView pillButton(String s, boolean primary) {
        TextView b = label(s, 16, primary ? 0xFF04201F : TEXT, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(primary ? round(ACCENT, 99, 0) : round(SURFACE2, 99, LINE)));
        b.setClickable(true);
        return b;
    }

    private TextView chip(String s) {
        TextView b = label(s, 13, TEXT, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(round(SURFACE2, 14, LINE)));
        b.setClickable(true);
        return b;
    }

    /** A card with a tappable header that folds its body open and closed. */
    private LinearLayout section(LinearLayout root, String title, String sub) {
        LinearLayout c = card(root);
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, 15, TEXT, "sans-serif-medium"));
        TextView st = label(sub, 12, MUTED, "sans-serif");
        st.setPadding(0, dp(2), 0, 0);
        texts.addView(st);
        head.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        final TextView chev = label("›", 26, MUTED, "sans-serif-light");
        head.addView(chev);
        c.addView(head);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        body.setPadding(0, dp(6), 0, 0);
        c.addView(body);
        head.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.animate().rotation(open ? 90 : 0).setDuration(150).start();
            if (open && body == logSection) updateLog(true);
        });
        return body;
    }

    interface OnToggle { void changed(boolean on); }

    private void toggle(LinearLayout body, String title, String sub, final String key, boolean def, final OnToggle cb) {
        final SharedPreferences p = BikeService.prefs(this);
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, 14, TEXT, "sans-serif"));
        texts.addView(label(sub, 12, MUTED, "sans-serif"));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(p.getBoolean(key, def));
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xFFB8C7C9}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x882DD4C4, 0x44FFFFFF}));
        sw.setOnCheckedChangeListener((v, on) -> {
            p.edit().putBoolean(key, on).apply();
            if (cb != null) cb.changed(on);
        });
        row.addView(sw);
        body.addView(row);
    }

    /** One line of the setup checklist: status badge, text, and a Fix button when needed. */
    private final class CheckRow {
        final TextView badge, fix;

        CheckRow(LinearLayout parent, String title, String sub, Runnable action) {
            LinearLayout row = hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);
            badge = label("", 13, 0xFF04201F, "sans-serif-medium");
            badge.setGravity(Gravity.CENTER);
            row.addView(badge, new LinearLayout.LayoutParams(dp(26), dp(26)));
            LinearLayout texts = new LinearLayout(MainActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(8), 0);
            texts.addView(label(title, 14, TEXT, "sans-serif-medium"));
            texts.addView(label(sub, 12, MUTED, "sans-serif"));
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            fix = chip("Fix");
            fix.setTextColor(ACCENT);
            fix.setOnClickListener(v -> action.run());
            row.addView(fix, new LinearLayout.LayoutParams(dp(64), dp(36)));
            parent.addView(row);
        }

        void set(boolean ok) {
            badge.setText(ok ? "✓" : "!");
            badge.setBackground(round(ok ? OK : WARN, 99, 0));
            fix.setVisibility(ok ? View.INVISIBLE : View.VISIBLE);
        }
    }
}
