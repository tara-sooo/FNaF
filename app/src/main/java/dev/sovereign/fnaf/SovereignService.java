package dev.sovereign.fnaf;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * First-wave test controller. Volume keys are optional: a 5-second delayed
 * start from the app UI provides a second, more diagnosable path.
 */
public final class SovereignService extends AccessibilityService {
    public static final String PREFS = "sovereign_config";
    private static final String TAG = "SovereignFirstWave";
    private static volatile SovereignService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private boolean running;
    private long startedElapsedNs;

    public static SovereignService connected() { return instance; }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() {
        cancelPending();
        logStatus("アクセシビリティが中断されました");
        running = false;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        logStatus("サービス接続OK。アプリの『5秒後にタップ』で動作を確認できます");
    }

    @Override public void onDestroy() {
        cancelPending();
        if (instance == this) instance = null;
        running = false;
        super.onDestroy();
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("ARMED", false);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean armed = prefs.getBoolean("ARMED", false);
        logStatus("音量" + (key == KeyEvent.KEYCODE_VOLUME_UP ? "＋" : "−") + "を受信。実行許可=" + armed);
        if (!armed) return false;
        if (key == KeyEvent.KEYCODE_VOLUME_UP) runFirstWave(prefs);
        else cancelWave();
        return true;
    }

    public void scheduleDelayed(boolean singleTap, long delayMs) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("ARMED", false)) {
            logStatus("開始不可：アプリの『実行を許可』をオンにしてください");
            return;
        }
        if (running) {
            logStatus("すでに操作中。完了または中断後に実行してください");
            return;
        }
        cancelPending();
        pending = () -> {
            pending = null;
            if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("ARMED", false)) {
                logStatus("開始直前に実行許可がオフになりました");
                return;
            }
            if (singleTap) runSingleTap();
            else runFirstWave(getSharedPreferences(PREFS, MODE_PRIVATE));
        };
        handler.postDelayed(pending, delayMs);
        logStatus(delayMs + "ms後に" + (singleTap ? "モニター単発タップ" : "9操作") + "を開始予定。ゲーム画面へ切替えてください");
    }

    private void cancelPending() {
        if (pending != null) {
            handler.removeCallbacks(pending);
            pending = null;
        }
    }

    /**
     * Called by the frame-stream detector when 12 AM first becomes visible.
     * The supplied monotonic timestamp becomes the absolute schedule epoch.
     * No human volume-key timing is involved.
     */
    public void runSynchronizedWave(long epochNs) {
        handler.post(() -> {
            SharedPreferences prefs=getSharedPreferences(PREFS, MODE_PRIVATE);
            if(!prefs.getBoolean("ARMED",false)) {
                logStatus("12 AM検出済み。ただし実行許可がOFF");
                return;
            }
            long nowNs=SystemClock.elapsedRealtimeNanos();
            long passedMs=Math.max(0,(nowNs-epochNs)/1000000L);
            if(passedMs>=900) {
                logStatus("12 AM同期が遅すぎます: "+passedMs+"ms。誤ったタイミングで実行しないため中止");
                return;
            }
            try {
                List<FirstWavePlan.Step> steps=FirstWavePlan.buildSynchronized();
                GestureDescription.Builder builder=new GestureDescription.Builder();
                for(FirstWavePlan.Step step:steps) {
                    long when=step.atMs-passedMs;
                    if(when<0)throw new IllegalStateException("操作期限を超過: "+step.description);
                    Point pt=TouchConfig.devicePoint(this,prefs,step.key);
                    Path p=new Path();p.moveTo(pt.x,pt.y);
                    builder.addStroke(new GestureDescription.StrokeDescription(
                            p,when,step.durationMs,false));
                }
                logStatus("12 AM同期から"+passedMs+"msで9操作を登録。ゲーム開始を基準に実行");
                dispatchChecked(builder.build(),"ゲーム時計同期・最初の9操作");
            } catch(Exception err) {
                logStatus("同期操作に失敗: "+err.getClass().getSimpleName()+": "+err.getMessage());
            }
        });
    }

    public void recordHourAnchor(int hour,long observedAt,long driftMs) {
        handler.post(() -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt("CLOCK_HOUR",hour)
                    .putLong("CLOCK_HOUR_AT_NS",observedAt)
                    .putLong("CLOCK_DRIFT_MS",driftMs).apply();
            logStatus(hour+" AM 時計変化を観測: 最初の12 AM基準とのずれ "+driftMs+"ms");
        });
    }

    private void runSingleTap() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            Point pt = TouchConfig.devicePoint(this, prefs, FirstWavePlan.Key.MONITOR);
            Path path = new Path();
            path.moveTo(pt.x, pt.y);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(
                    new GestureDescription.StrokeDescription(path, 0, 70, false)).build();
            dispatchChecked(gesture, "単発タップ " + pt.x + "," + pt.y);
        } catch (Exception ex) {
            logStatus("単発タップ例外: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void runFirstWave(SharedPreferences prefs) {
        if (running) { logStatus("すでに9操作を実行中"); return; }
        try {
            List<FirstWavePlan.Step> steps = FirstWavePlan.build();
            FirstWavePlan.validate(steps);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            for (FirstWavePlan.Step step : steps) {
                Point pt = TouchConfig.devicePoint(this, prefs, step.key);
                Path path = new Path();
                path.moveTo(pt.x, pt.y);
                builder.addStroke(new GestureDescription.StrokeDescription(
                        path, step.atMs, step.durationMs, false));
            }
            dispatchChecked(builder.build(), "最初の9操作");
        } catch (Exception ex) {
            running = false;
            logStatus("9操作例外: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void dispatchChecked(GestureDescription gesture, String name) {
        startedElapsedNs = SystemClock.elapsedRealtimeNanos();
        running = true;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                running = false;
                long elapsed = (SystemClock.elapsedRealtimeNanos() - startedElapsedNs) / 1000000L;
                logStatus(name + " 完了通知 (" + elapsed + "ms)。ゲーム側での反応は別途確認");
            }
            @Override public void onCancelled(GestureDescription g) {
                running = false;
                logStatus(name + " キャンセルされました");
            }
        }, handler);
        if (!accepted) {
            running = false;
            logStatus(name + " Android側がdispatchGestureを拒否しました");
        } else {
            logStatus(name + " dispatchGesture受理 (" + gesture.getStrokeCount() + "ストローク)");
        }
    }

    private void cancelWave() {
        cancelPending();
        if (!running) {
            logStatus("待機を解除。実行中の操作なし");
            return;
        }
        Path p = new Path();
        p.moveTo(1, 1);
        try {
            GestureDescription g = new GestureDescription.Builder().addStroke(
                    new GestureDescription.StrokeDescription(p, 0, 1, false)).build();
            boolean accepted = dispatchGesture(g, null, handler);
            running = false;
            logStatus(accepted ? "キャンセルジェスチャー送信" : "キャンセル送信失敗");
        } catch (Exception ex) {
            running = false;
            logStatus("キャンセル例外: " + ex.getMessage());
        }
    }

    private void logStatus(String message) {
        Log.i(TAG, message);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String at = new SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(new Date());
        String old = prefs.getString("HISTORY", "");
        String next = "[" + at + "] " + message + "\n" + old;
        if (next.length() > 3500) next = next.substring(0, 3500);
        prefs.edit().putString("STATUS", message)
                .putLong("STATUS_AT", System.currentTimeMillis())
                .putString("HISTORY", next).apply();
    }
}
