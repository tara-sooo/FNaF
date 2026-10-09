package dev.sovereign.fnaf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

/**
 * Receives an owner-APK-instrumented frame-start signal; never infers
 * game t0 from the arrival timestamp.
 */
public final class GameFrameBridgeReceiver extends BroadcastReceiver {
    public static final String ACTION="dev.sovereign.fnaf.INTERNAL_MAIN_ROOM_START";

    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null || !ACTION.equals(intent.getAction()))return;
        if(intent.getIntExtra("frame_index",-1)!=5)return;
        long epoch=intent.getLongExtra("epoch_elapsed_ns",0L);
        long engineMs=intent.getLongExtra("engine_wall_ms",-1L);
        long now=SystemClock.elapsedRealtimeNanos();
        long age=(now-epoch)/1000000L;
        if(epoch<=0 || age<0 || age>750) {
            log(context,"Main Room信号を破棄 (通知遅延 "+age+"ms)");
            return;
        }
        SharedPreferences prefs=context.getSharedPreferences(SovereignService.PREFS,Context.MODE_PRIVATE);
        if(!prefs.getBoolean("ARMED",false)) {
            log(context,"Main Room開始を受信、実行許可OFF (通知遅延 "+age+"ms)");
            return;
        }
        log(context,"Main Room実行ループ開始を直接受信。通知遅延="+age+
                "ms engine.rhTimerOld="+engineMs+"ms。端末時計の受信時刻を0秒にはしません");
        SovereignService service=SovereignService.connected();
        if(service!=null)service.runSynchronizedWave(epoch);
        else log(context,"アクセシビリティ未接続のため自動操作は実行していません");
    }

    private static void log(Context c,String message) {
        Log.i("SovereignFrameBridge",message);
        SharedPreferences prefs=c.getSharedPreferences(SovereignService.PREFS,Context.MODE_PRIVATE);
        String history="[ENGINE] "+message+"\n"+prefs.getString("HISTORY","");
        if(history.length()>3500)history=history.substring(0,3500);
        prefs.edit().putString("STATUS",message).putString("HISTORY",history)
                .putLong("STATUS_AT",System.currentTimeMillis()).apply();
    }
}
