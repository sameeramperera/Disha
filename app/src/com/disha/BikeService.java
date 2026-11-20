package com.disha;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Keeps the Bluetooth LE link to the TVS cluster and writes navigation frames. */
@SuppressLint("MissingPermission")
public class BikeService extends Service {
    // TVS cluster BLE service ("TVSMVGSASBENTORQ" in ASCII).
    static final UUID SERVICE = UUID.fromString("5456534d-5647-5341-5342-454e544f5251");
    static final UUID WRITE_CHAR = UUID.fromString("00005352-0000-1000-8000-00805f9b34fb");
    static final UUID NOTIFY_CHAR = UUID.fromString("00005354-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    static final String ACTION_CONNECT = "connect", ACTION_DISCONNECT = "disconnect";

    public static volatile BikeService instance;
    public static volatile String status = "Not connected";
    private static final List<String> LOG = new ArrayList<>();

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Runnable> ops = new ArrayDeque<>();
    private boolean busy;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar, notifyChar;
    private boolean wantConnected, ready;

    // The cluster leaves navigation mode after ~8 s without data, and it hides the navigation bar
    // ~1.5 s after the last update. So the last update is repeated every 0.7 s to keep the bar on screen.
    private static final long KEEPALIVE_MS = 700;
    /** Text last written to the bike, so it can be sent only when it changes. null = must send. */
    private String lastTextSent;
    private TvsProtocol.Nav lastNav;
    private long lastSendAt;
    private final java.util.Map<String, String> lastFromBike = new java.util.HashMap<>();

    // ---------- logging shared with the screen, and saved to a file as it goes ----------
    // The file survives the app being closed or the phone restarting. Two files of ~1.5 MB rotate:
    // disha.log (current) and disha-prev.log. Lines only carry the time, so a date line is
    // written at start and whenever the day changes.
    private static final int MEM_LINES = 5000;
    private static final long ROTATE_BYTES = 1_500_000;
    private static final java.util.concurrent.ExecutorService LOG_IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static java.io.File logFile, prevFile;
    private static java.io.Writer logOut;
    private static boolean toFile;
    private static String lastDay = "";

    public static void log(String s) {
        Date now = new Date();
        String day = new SimpleDateFormat("yyyy-MM-dd EEE", Locale.ROOT).format(now);
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(now) + " " + s;
        Log.i("Disha", line);
        synchronized (LOG) {
            if (!day.equals(lastDay)) {
                lastDay = day;
                addLine("---- " + day + " ----");
            }
            addLine(line);
        }
    }

    /** Caller holds LOG. */
    private static void addLine(final String line) {
        LOG.add(line);
        while (LOG.size() > MEM_LINES) LOG.remove(0);
        if (toFile) LOG_IO.execute(() -> writeLine(line));
    }

    /** Opens the log file (once per process) and loads its newest lines for the screen. */
    static void initLog(Context c) {
        final List<String> early;
        synchronized (LOG) {
            if (logFile != null) return;
            java.io.File dir = c.getApplicationContext().getFilesDir();
            logFile = new java.io.File(dir, "disha.log");
            prevFile = new java.io.File(dir, "disha-prev.log");
            early = new ArrayList<>(LOG);   // logged before the file was open
            toFile = true;
        }
        LOG_IO.execute(() -> {
            List<String> old = readLines(MEM_LINES);
            synchronized (LOG) {
                LOG.addAll(0, old);
                while (LOG.size() > MEM_LINES) LOG.remove(0);
            }
            openWriter();
            writeLine("==== Disha started " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " ====");
            for (String l : early) writeLine(l);
        });
    }

    private static void openWriter() {
        try {
            logOut = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(logFile, true), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            Log.w("Disha", "log file: " + e);
            logOut = null;
        }
    }

    /** Log thread only. Flushed every line, so nothing is lost if the app is killed. */
    private static void writeLine(String line) {
        if (logOut == null) return;
        try {
            logOut.write(line);
            logOut.write('\n');
            logOut.flush();
            if (logFile.length() > ROTATE_BYTES) {
                logOut.close();
                //noinspection ResultOfMethodCallIgnored
                prevFile.delete();
                //noinspection ResultOfMethodCallIgnored
                logFile.renameTo(prevFile);
                openWriter();
            }
        } catch (java.io.IOException e) {
            Log.w("Disha", "log write: " + e);
        }
    }

    /** Log thread only: the newest max lines of both files, oldest first. */
    private static List<String> readLines(int max) {
        ArrayDeque<String> q = new ArrayDeque<>();
        for (java.io.File f : new java.io.File[]{prevFile, logFile}) {
            if (f == null || !f.isFile()) continue;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    q.add(l);
                    if (max > 0 && q.size() > max) q.poll();
                }
            } catch (java.io.IOException e) {
                Log.w("Disha", "log read: " + e);
            }
        }
        return new ArrayList<>(q);
    }

    /** Newest first, at most maxLines lines (for the screen). */
    public static String logText(int maxLines) {
        synchronized (LOG) {
            StringBuilder b = new StringBuilder();
            int stop = Math.max(0, LOG.size() - maxLines);
            for (int i = LOG.size() - 1; i >= stop; i--) b.append(LOG.get(i)).append('\n');
            return b.toString();
        }
    }

    /** Empties the log and deletes the log files (the Clear button). */
    public static void clearLog() {
        synchronized (LOG) {
            LOG.clear();
            lastDay = "";
            if (toFile) LOG_IO.execute(() -> {
                try { if (logOut != null) logOut.close(); } catch (java.io.IOException ignored) { }
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
                //noinspection ResultOfMethodCallIgnored
                prevFile.delete();
                openWriter();
            });
        }
        log("Log cleared");
    }

    /** Whole log, oldest first (for sharing): both log files, or memory if they are not open. */
    public static String fullLog() {
        if (toFile) {
            try {
                List<String> lines = LOG_IO.submit(() -> readLines(0)).get(10, java.util.concurrent.TimeUnit.SECONDS);
                StringBuilder b = new StringBuilder();
                for (String l : lines) b.append(l).append('\n');
                return b.toString();
            } catch (Exception e) {
                Log.w("Disha", "log share: " + e);
            }
        }
        synchronized (LOG) {
            StringBuilder b = new StringBuilder();
            for (String s : LOG) b.append(s).append('\n');
            return b.toString();
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("disha", MODE_PRIVATE);
    }

    // ---------- service lifecycle ----------
    @Override public IBinder onBind(Intent i) { return null; }

    // ---------- rides ----------
    /** Ride in progress, for the screen (null when not riding). */
    public static volatile RideTracker.Ride currentRide;
    private RideTracker rides;
    private long rideSavedAt;

    private final Runnable rideTick = new Runnable() {
        @Override public void run() {
            rides.tick(System.currentTimeMillis());
            h.postDelayed(this, 10_000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initLog(this);
        rides = new RideTracker(new RideTracker.Listener() {
            @Override public void finished(RideTracker.Ride r) {
                currentRide = null;
                RideStore.add(BikeService.this, r);
                log("RIDE finished: " + RideTracker.summary(r));
                notifyRide(r);
            }
            @Override public void changed(RideTracker.Ride r) {
                currentRide = r;
                long now = System.currentTimeMillis();
                if (r == null) { RideStore.saveActive(BikeService.this, null); rideSavedAt = 0; }
                else if (now - rideSavedAt > 30_000) { RideStore.saveActive(BikeService.this, r); rideSavedAt = now; }
            }
        });
        // A ride that was going when the app was closed: carry on, or end it if it is long over.
        RideTracker.Ride saved = RideStore.active(this);
        if (saved != null) {
            rides.resume(saved, saved.end);
            currentRide = saved;
            rides.tick(System.currentTimeMillis());
        }
        h.postDelayed(rideTick, 10_000);
    }

    // ---------- alerts (Settings > Alerts) ----------
    private boolean speedArmed = true;

    /**
     * Speed alert: vibrate once when the bike reaches the set speed; again only after dropping 5
     * km/h below it. Low fuel: one notification when the gauge falls to the set bars; again only
     * after it has been above that (a fill-up), even across app restarts.
     */
    private void checkAlerts() {
        SharedPreferences p = prefs(this);
        long now = System.currentTimeMillis();
        if (p.getBoolean("speedAlert", false) && now - telemetryAt < 3000) {
            int limit = p.getInt("speedLimit", 80), kmh = speedKmh;
            if (kmh < limit - 5) speedArmed = true;
            else if (speedArmed && kmh >= limit) {
                speedArmed = false;
                log("ALERT speed " + kmh + " km/h (limit " + limit + ")");
                vibrate();
            }
        }
        int bars = BikeFrames.fuelBars;
        if (p.getBoolean("fuelAlert", true) && bars >= 0) {
            int level = p.getInt("fuelLevel", 1);
            boolean warned = p.getBoolean("fuelWarned", false);
            if (bars > level && warned) p.edit().putBoolean("fuelWarned", false).apply();
            else if (bars <= level && !warned) {
                p.edit().putBoolean("fuelWarned", true).apply();
                log("ALERT low fuel: " + bars + " of 5 bars (warn at " + level + ")");
                notifyLowFuel(bars);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate() {
        long[] pattern = {0, 450, 150, 450, 150, 450};
        android.os.Vibrator vib = Build.VERSION.SDK_INT >= 31
                ? getSystemService(android.os.VibratorManager.class).getDefaultVibrator()
                : (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        android.os.VibrationEffect fx = android.os.VibrationEffect.createWaveform(pattern, -1);
        if (Build.VERSION.SDK_INT >= 33) {
            // Alarm usage: still vibrates when the phone is on silent.
            vib.vibrate(fx, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM));
        } else {
            vib.vibrate(fx);
        }
    }

    private void notifyLowFuel(int bars) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("alerts", "Low fuel",
                NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        nm.notify(4242, new Notification.Builder(this, "alerts")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Low fuel")
                .setContentText(bars == 0 ? "The fuel gauge is empty. Fill up soon."
                        : "Fuel is down to " + bars + " of 5 bars. Time to fill up.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }

    private void notifyRide(RideTracker.Ride r) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("rides", "Ride summary",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent pi = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        nm.notify((int) (r.start / 1000), new Notification.Builder(this, "rides")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("Ride finished")
                .setContentText(RideTracker.summary(r))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        String a = intent == null ? ACTION_CONNECT : intent.getAction();
        if (ACTION_DISCONNECT.equals(a)) {
            prefs(this).edit().putBoolean("autoConnect", false).apply(); // user said stop: no auto-start after restart
            disconnect();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else {
            prefs(this).edit().putBoolean("autoConnect", true).apply();
            wantConnected = true;
            // Asked to connect (button, Maps started): try fast again, not the slow standby wait.
            failCount = 0;
            if (gatt != null && bgConnect && !ready) closeGatt();
            connect();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacks(rideTick);
        // Keep the ride in progress: the next start carries on with it, or ends it if it is over.
        RideStore.saveActive(this, rides.current());
        currentRide = null;   // nothing is being recorded while the service is stopped
        wantConnected = false;
        closeGatt();
        instance = null;
        setStatus("Not connected");
        super.onDestroy();
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bike", "Bike connection",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "bike")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Disha")
                .setContentText("Sending Google Maps directions to the bike")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(1, n);
        }
    }

    private void setStatus(String s) {
        status = s;
        log("STATUS " + s);
    }

    // ---------- auto-connect ----------
    private static long lastAutoStart;

    /**
     * Starts the bike link without the user opening the app: when Google Maps starts a trip
     * ("maps-start"), when a trip is running but the link died by itself ("maps"), or after a phone
     * restart / app update ("restart"). Needs a remembered bike.
     */
    static void autoStart(Context c, String why) {
        SharedPreferences p = prefs(c);
        if (p.getString("mac", "").isEmpty()) return;
        // "maps-start" (a new Google Maps trip) always connects; the others only if the user did not
        // press Disconnect last time.
        if (!"maps-start".equals(why) && !p.getBoolean("autoConnect", false)) return;
        BikeService s = instance;
        if (s != null && s.wantConnected) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoStart < 10_000) return;
        lastAutoStart = now;
        log("Auto-connect (" + why + ")");
        try {
            c.startForegroundService(new Intent(c, BikeService.class).setAction(ACTION_CONNECT));
        } catch (Exception e) {
            // Android 12+ can refuse to start from the background unless battery optimisation is off.
            log("Auto-connect blocked by Android: " + e);
        }
    }

    // ---------- connection ----------
    // After 3 failed fast attempts (bike off or out of range) switch to Android's "connect when it
    // appears" mode (autoConnect=true): it waits in the background at low power instead of retrying
    // every few seconds, and connects within seconds of the ignition going on.
    private static final int FAST_TRIES = 3;
    private int failCount;
    private boolean bgConnect;

    private void connect() {
        if (gatt != null) return;
        BluetoothAdapter ad = getSystemService(BluetoothManager.class).getAdapter();
        if (ad == null || !ad.isEnabled()) {
            setStatus("Bluetooth is off");
            retryLater();
            return;
        }
        String mac = prefs(this).getString("mac", "");
        if (mac.isEmpty()) {
            wantConnected = false;
            setStatus("No bike chosen. Tap Find bike");
            return;
        }
        BluetoothDevice dev;
        try {
            dev = ad.getRemoteDevice(mac);
        } catch (IllegalArgumentException e) {
            setStatus("Bad bike address: " + mac);
            return;
        }
        bgConnect = failCount >= FAST_TRIES;
        setStatus(bgConnect ? "Waiting for the bike (ignition on?) " + mac : "Connecting to " + mac + " ...");
        gatt = dev.connectGatt(this, bgConnect, cb, BluetoothDevice.TRANSPORT_LE);
    }

    private void retryLater() {
        if (wantConnected) h.postDelayed(this::connect, 4000);
    }

    private void disconnect() {
        wantConnected = false;
        h.removeCallbacksAndMessages(null);
        closeGatt();
        setStatus("Disconnected");
    }

    private void closeGatt() {
        ready = false;
        lastTextSent = null;
        lastStatusAt = 0;
        h.removeCallbacks(keepAlive);
        lastFromBike.clear();
        ops.clear();
        busy = false;
        writeChar = notifyChar = null;
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) { }
            gatt = null;
        }
    }

    public boolean isReady() { return ready; }

    /** Connected, or trying / waiting to connect (so the screen offers Disconnect). */
    public boolean isActive() { return ready || wantConnected; }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            h.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    setStatus("Connected, setting up ...");
                    g.requestMtu(137);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // A working link that drops almost always means the ignition went off: go straight
                    // to the low-power wait (it reconnects within seconds once the bike is back).
                    if (ready) failCount = FAST_TRIES; else failCount++;
                    setStatus("Disconnected (code " + st + ")" + (wantConnected ? ", retrying" : ""));
                    closeGatt();
                    retryLater();
                }
            });
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int st) {
            h.post(() -> {
                log("MTU " + mtu + " (status " + st + ")");
                g.discoverServices();
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int st) {
            h.post(() -> setupCharacteristics(g));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            h.post(() -> {
                if (st != BluetoothGatt.GATT_SUCCESS) log("Write failed, status " + st);
                opDone();
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            h.post(() -> {
                log("Notifications on (status " + st + ")");
                opDone();
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] v) {
            fromBike(v);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (Build.VERSION.SDK_INT < 33) fromBike(c.getValue());
        }
    };

    // ---------- live data from the bike ----------
    /** Bike speed in km/h, odometer in 0.1 km, and when they arrived (0 = never). */
    public static volatile int speedKmh = 0, odoTenths = 0;
    public static volatile long telemetryAt = 0;

    /**
     * Frame 5A 10 (about 3 a second): byte 2 = speed km/h, bytes 3-5 = odometer in 100 m.
     * Byte 18 = 255 - (sum of bytes 0..17) % 256; frames with a bad checksum are dropped.
     */
    static boolean decodeTelemetry(byte[] v) {
        if (v.length < 20 || (v[0] & 0xFF) != 0x5A || (v[1] & 0xFF) != 0x10) return false;
        int sum = 0;
        for (int i = 0; i < 18; i++) sum += v[i] & 0xFF;
        if ((255 - sum % 256) != (v[18] & 0xFF)) return false;
        speedKmh = v[2] & 0xFF;
        odoTenths = (v[3] & 0xFF) << 16 | (v[4] & 0xFF) << 8 | (v[5] & 0xFF);
        telemetryAt = System.currentTimeMillis();
        return true;
    }

    /** Logs a bike frame only when it differs from the last one of the same type (the bike repeats a lot). */
    private void fromBike(byte[] v) {
        if (v == null) return;
        if (decodeTelemetry(v)) {
            final int kmh = speedKmh, odo = odoTenths;
            final long t = telemetryAt;
            h.post(() -> rides.sample(kmh, odo, t));
        }
        final String hex = TvsProtocol.hex(v);
        BikeFrames.update(v);
        h.post(this::checkAlerts);
        final String meaning = BikeFrames.describe(v);
        h.post(() -> {
            String type = hex.length() >= 5 ? hex.substring(0, 5) : hex;
            boolean navRestart = type.equals("5A 9D");
            if (navRestart) lastTextSent = null;
            if (!navRestart && hex.equals(lastFromBike.get(type))) return;
            lastFromBike.put(type, hex);
            log("FROM BIKE " + hex + (navRestart ? "   <- bike (re)started navigation mode" : ""));
            if (meaning != null) log(meaning);
        });
    }

    private void setupCharacteristics(BluetoothGatt g) {
        for (BluetoothGattService s : g.getServices()) {
            log("Service " + s.getUuid());
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                log("  char " + c.getUuid() + " props=" + props(c.getProperties()));
            }
        }
        BluetoothGattService s = g.getService(SERVICE);
        if (s == null) {
            // Fallback: first non-standard service that has a writable characteristic.
            for (BluetoothGattService cand : g.getServices()) {
                if (cand.getUuid().toString().endsWith("-0000-1000-8000-00805f9b34fb")
                        && cand.getUuid().toString().startsWith("000018")) continue;
                for (BluetoothGattCharacteristic c : cand.getCharacteristics()) {
                    if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) { s = cand; break; }
                }
                if (s != null) break;
            }
        }
        if (s == null) {
            setStatus("TVS service not found on this device");
            return;
        }
        log("Using service " + s.getUuid());
        writeChar = s.getCharacteristic(WRITE_CHAR);
        notifyChar = s.getCharacteristic(NOTIFY_CHAR);
        for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
            int p = c.getProperties();
            boolean canWrite = (p & (BluetoothGattCharacteristic.PROPERTY_WRITE
                    | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
            boolean canNotify = (p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY
                    | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0;
            if (canWrite && writeChar == null) writeChar = c;
            if (canNotify && notifyChar == null) notifyChar = c;
        }
        if (writeChar == null) {
            setStatus("No writable characteristic in TVS service");
            return;
        }
        log("Using write char " + writeChar.getUuid());
        if (notifyChar != null) {
            log("Using notify char " + notifyChar.getUuid());
            final BluetoothGattCharacteristic nc = notifyChar;
            enqueue(() -> {
                g.setCharacteristicNotification(nc, true);
                BluetoothGattDescriptor d = nc.getDescriptor(CCCD);
                if (d == null) { opDone(); return; }
                byte[] val = (nc.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                writeDescriptor(g, d, val);
            });
        }
        ready = true;
        failCount = 0;
        setStatus("READY - connected to bike");
        h.removeCallbacks(keepAlive);
        h.postDelayed(keepAlive, KEEPALIVE_MS);
    }

    @SuppressWarnings("deprecation")
    private void writeDescriptor(BluetoothGatt g, BluetoothGattDescriptor d, byte[] val) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, val);
        } else {
            d.setValue(val);
            g.writeDescriptor(d);
        }
    }

    static String props(int p) {
        StringBuilder b = new StringBuilder();
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) b.append("READ ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) b.append("WRITE ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) b.append("WRITE_NO_RESP ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) b.append("NOTIFY ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) b.append("INDICATE ");
        return b.toString().trim();
    }

    // ---------- one-at-a-time GATT operation queue ----------
    private void enqueue(Runnable op) {
        ops.add(op);
        if (!busy) next();
    }

    private void next() {
        Runnable op = ops.poll();
        if (op == null) { busy = false; return; }
        busy = true;
        h.postDelayed(watchdog, 1500);
        try {
            op.run();
        } catch (Exception e) {
            log("GATT op error: " + e);
            opDone();
        }
    }

    private final Runnable watchdog = () -> {
        log("GATT op timed out, continuing");
        opDone();
    };

    private void opDone() {
        h.removeCallbacks(watchdog);
        busy = false;
        next();
    }

    private void enqueueDelay(long ms) {
        enqueue(() -> h.postDelayed(this::opDone, ms));
    }

    @SuppressWarnings("deprecation")
    private void enqueueWrite(byte[] data, boolean logHex) {
        enqueue(() -> {
            BluetoothGattCharacteristic c = writeChar;
            if (gatt == null || c == null) { opDone(); return; }
            int type = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            if (logHex) log("TO BIKE " + TvsProtocol.hex(data));
            boolean ok;
            if (Build.VERSION.SDK_INT >= 33) {
                ok = gatt.writeCharacteristic(c, data, type) == 0;
            } else {
                c.setWriteType(type);
                c.setValue(data);
                ok = gatt.writeCharacteristic(c);
            }
            if (!ok) { log("Write rejected by Android"); opDone(); }
        });
    }

    // ---------- public API ----------
    /** Sends one navigation update and keeps re-sending it every 0.7 s until the next one. */
    public void sendNav(TvsProtocol.Nav nav) {
        h.post(() -> {
            lastNav = nav;
            if (!ready) {
                log("Not sent (bike not ready): " + nav);
                return;
            }
            log("SEND " + nav);
            writeNav(nav, true);
        });
    }

    /** Navigation ended in Google Maps: stop re-sending. */
    public void stopNav() {
        h.post(() -> { lastNav = null; lastSendAt = 0; });
    }

    private static final long STATUS_MS = 2000;
    private long lastStatusAt;

    /** Phone battery as 0..5 bars for the cluster's phone icon. */
    private int phoneBatteryBars() {
        try {
            int pct = getSystemService(android.os.BatteryManager.class)
                    .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return pct < 0 ? 5 : Math.max(0, Math.min(5, Math.round(pct / 20f)));
        } catch (Exception e) {
            return 5;
        }
    }

    private final Runnable keepAlive = new Runnable() {
        @Override public void run() {
            if (!ready) return;
            long now = System.currentTimeMillis();
            // Only when idle, so a slow link never builds up a queue of stale updates.
            if (lastNav != null && !busy && ops.isEmpty() && now - lastSendAt >= KEEPALIVE_MS) {
                writeNav(lastNav, false);
            }
            // Phone status every 2 s, as TVS Connect does. The bike sends no data (fuel, odometer,
            // ...) until it gets the first one, and it keeps the cluster clock right.
            if (now - lastStatusAt >= STATUS_MS && ops.size() < 4) {
                boolean first = lastStatusAt == 0;
                lastStatusAt = now;
                enqueueWrite(TvsProtocol.phoneStatusFrame(phoneBatteryBars(), java.util.Calendar.getInstance()), first);
            }
            h.postDelayed(this, 100);
        }
    };

    /** 2 frames, each optionally in plain + protected form. */
    private void writeNav(TvsProtocol.Nav nav, boolean logHex) {
        long now = System.currentTimeMillis();
        // The bike gives up on navigation after ~8 s without data. A long gap here means Android
        // paused the app (battery saver / screen off), which shows as the panel closing on the bike.
        if (lastSendAt > 0 && now - lastSendAt > 6000) {
            log("GAP " + (now - lastSendAt) / 1000 + " s without sending (phone paused the app?)");
        }
        lastSendAt = now;
        // Plain frames only (the protected copy garbles numbers), instruction text only when it
        // changes (keeps the nav bar from blinking).
        final boolean plain = true, prot = false;
        String text = nav.text == null ? "" : nav.text;
        boolean sendText = !text.equals(lastTextSent);
        byte[] control = TvsProtocol.controlFrame(nav), textFrame = TvsProtocol.textFrame(nav);
        if (plain) enqueueWrite(control, logHex);
        if (prot) enqueueWrite(TvsProtocol.protectedCopy(control), logHex);
        if (sendText) {
            enqueueDelay(100); // TVS waits 100 ms before the text frame
            if (plain) enqueueWrite(textFrame, logHex);
            if (prot) enqueueWrite(TvsProtocol.protectedCopy(textFrame), logHex);
            lastTextSent = text;
        }
    }
}
