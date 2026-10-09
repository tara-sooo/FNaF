package dev.sovereign.fnaf;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Point;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.util.List;

/**
 * Volume-UP triggers the *entire* 0..4.801 second trajectory in one OS gesture.
 * No polling screenshot, per-event Handler scheduling, ADB, JNI or root.
 * Volume-DOWN attempts cancellation by dispatching a replacement gesture.
 */
public final class SovereignService extends AccessibilityService {
    public static final String PREFS="sovereign_config";
    private static final String TAG="SovereignFirstWave";
    private boolean running=false;
    private long startedElapsedNs=0;

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() {
        logStatus("Accessibility interrupted");
        running=false;
    }
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info=getServiceInfo();
        if (info!=null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        logStatus("Service ready. Arm in app, go to FNaF, press VOL+.");
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        SharedPreferences p=getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("ARMED",false)) return false;
        if (e.getKeyCode()!=KeyEvent.KEYCODE_VOLUME_UP &&
                e.getKeyCode()!=KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0) {
            if (e.getKeyCode()==KeyEvent.KEYCODE_VOLUME_UP) runFirstWave(p);
            else cancelWave();
        }
        return true;
    }

    private void runFirstWave(SharedPreferences prefs) {
        if(running) {logStatus("Already running; VOL- to cancel.");return;}
        List<FirstWavePlan.Step> steps=FirstWavePlan.build();
        try { FirstWavePlan.validate(steps); }
        catch(RuntimeException err) { logStatus("Bad schedule: "+err.getMessage());return; }
        // This is the controller epoch; game-frame start must later be synchronized.
        GestureDescription.Builder builder=new GestureDescription.Builder();
        for (FirstWavePlan.Step step:steps) {
            Point pt=TouchConfig.devicePoint(this,prefs,step.key);
            Path path=new Path();
            path.moveTo(pt.x,pt.y);
            builder.addStroke(new GestureDescription.StrokeDescription(
                    path,step.atMs,step.durationMs,false));
        }
        GestureDescription gesture=builder.build();
        startedElapsedNs=SystemClock.elapsedRealtimeNanos();
        running=true;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){
                running=false;
                long elapsed=(SystemClock.elapsedRealtimeNanos()-startedElapsedNs)/1_000_000L;
                logStatus("Gesture completed after "+elapsed+"ms (individual game taps not verified)");
            }
            @Override public void onCancelled(GestureDescription g){
                running=false;
                logStatus("Gesture cancelled");
            }
        },null);
        if (!accepted) {running=false;logStatus("Android rejected dispatchGesture");}
        else logStatus("Started first wave: 9 scheduled strokes, epoch="+startedElapsedNs);
    }
    private void cancelWave() {
        if(!running){logStatus("Nothing running");return;}
        // Any new gesture cancels ongoing accessibility-dispatched strokes.
        Path p=new Path();p.moveTo(1,1);
        GestureDescription g=new GestureDescription.Builder().addStroke(
                new GestureDescription.StrokeDescription(p,0,1,false)).build();
        boolean queued=dispatchGesture(g,null,null);
        running=false;
        logStatus(queued?"Cancellation gesture dispatched":"Cancellation dispatch failed");
    }
    private void logStatus(String s) {
        Log.i(TAG,s);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString("STATUS",s).putLong("STATUS_AT",System.currentTimeMillis()).apply();
    }
}
