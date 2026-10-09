package dev.sovereign.fnaf;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * FNaF1 gameplay-clock phase detector. Watches only the clock HUD ROI,
 * no enemy-state imagery, and never saves screenshots to disk.
 *
 * First rising HUD edge: anchors controller time; following changed HUD digit
 * masks: report hour transitions against the 90-second/game-hour source timer.
 * This is a DISPLAY-clock alignment, not access to the game's private clock.
 */
public final class ClockSyncCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL = "clock_sync";
    private static final int NOTIFICATION_ID = 1103;
    private static final long MAX_WAIT_NS = 40000000000L;
    private static final int W=1280,H=720;
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay virtual;
    private HandlerThread processingThread;
    private Handler worker;
    private boolean detected;
    private boolean sawAbsent;
    private int absentFrames;
    private int strongFrames;
    private int lastWhite;
    private int lowCount=Integer.MAX_VALUE;
    private int baselineSum,baselineN;
    private long armedNs;
    private BitSet oldMask, candidateMask;
    private int candidateCount;
    private long epochNs;
    private int hour;
    private long lastHourNs;
    private int frames;
    private boolean captureActive;

    public static void stop(Context context) {
        context.stopService(new Intent(context, ClockSyncCaptureService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mgr.createNotificationChannel(new NotificationChannel(CHANNEL,"Sovereign時刻同期",NotificationManager.IMPORTANCE_LOW));
        Notification notification=new Notification.Builder(this,CHANNEL)
                .setContentTitle("Sovereign · ゲーム時計と同期")
                .setContentText("12 AMの出現と、時刻の切り替わりを検出")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29) {
            startForeground(NOTIFICATION_ID,notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID,notification);
        }
    }

    @Override public int onStartCommand(Intent request,int flags,int startId) {
        if(request==null || !request.hasExtra(EXTRA_RESULT_DATA) || captureActive)
            return START_NOT_STICKY;
        int result=request.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED);
        Intent data=request.getParcelableExtra(EXTRA_RESULT_DATA);
        try {
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(result,data);
            if(projection==null) {
                report("画面共有権限を取得できませんでした");
                stopSelf();return START_NOT_STICKY;
            }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    report("画面共有が停止しました");
                    stopSelf();
                }
            },new Handler(Looper.getMainLooper()));
            processingThread=new HandlerThread("clock-scan", android.os.Process.THREAD_PRIORITY_DISPLAY);
            processingThread.start();
            worker=new Handler(processingThread.getLooper());
            reader=ImageReader.newInstance(W,H,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::onImage,worker);
            virtual=projection.createVirtualDisplay("SovereignClockHUD",W,H,
                    getResources().getDisplayMetrics().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(),null,worker);
            captureActive=true;
            armedNs=SystemClock.elapsedRealtimeNanos();
            report("同期監視開始。Custom Nightの開始画面からゲームを開始してください");
        } catch(Exception ex) {
            report("画面共有の開始失敗: "+ex.getClass().getSimpleName()+" "+ex.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void onImage(ImageReader source) {
        Image frame=null;
        try {
            frame=source.acquireLatestImage();
            if(frame==null) return;
            long observedAt=SystemClock.elapsedRealtimeNanos();
            long imageAt=frame.getTimestamp();
            // Most MediaProjection buffers use a monotonic nanosecond timestamp.
            // If the timebase differs, use callback time instead.
            if(imageAt>0 && observedAt-imageAt>=0 && observedAt-imageAt<150000000L)
                observedAt=imageAt;

            Image.Plane plane=frame.getPlanes()[0];
            ByteBuffer buf=plane.getBuffer();
            int rs=plane.getRowStride(), ps=plane.getPixelStride();
            if(ps<3 || rs<W*ps) return;
            BitSet mask=new BitSet(2048);
            int white=0,idx=0;
            // From APK main-frame instances: time-of-day at (1185,59);
            // hour suffix AM at (1198,31). HUD remains stable under room panning.
            for(int y=12;y<=102;y+=3) {
                for(int x=1095;x<=1269;x+=3) {
                    int pos=y*rs+x*ps;
                    if(pos+2>=buf.limit()) {idx++;continue;}
                    int r=buf.get(pos)&255,g=buf.get(pos+1)&255,b=buf.get(pos+2)&255;
                    if(r>=210 && g>=210 && b>=210) {white++;mask.set(idx);}
                    idx++;
                }
            }
            frames++;
            if(!detected) findFirstClock(mask,white,observedAt);
            else trackClockChange(mask,white,observedAt);
            if(!detected && observedAt-armedNs>MAX_WAIT_NS) {
                report("40秒経過しても12 AMを検出できません。監視を停止");
                stopSelf();
            }
        } catch(Exception ex) {
            if(frames<3) report("画面解析失敗: "+ex.getClass().getSimpleName()+" "+ex.getMessage());
        } finally {
            if(frame!=null)frame.close();
        }
    }

    private void findFirstClock(BitSet mask,int white,long at) {
        lowCount=Math.min(lowCount,white);
        if(baselineN<10) {
            baselineSum+=white;
            baselineN++;
            if(baselineN==10) report("時計検出の初期値="+(baselineSum/10)+"白点");
            return;
        }
        int base=Math.min(lowCount,baselineSum/Math.max(1,baselineN));
        if(white<=base+8) {
            sawAbsent=true;
            absentFrames++;
            strongFrames=0;
            return;
        }
        if(!sawAbsent || absentFrames<4) return;
        if(white>=base+18 && white>=18) {
            strongFrames++;
            if(strongFrames<2)return;
            detected=true;
            epochNs=at-16666667L;
            oldMask=(BitSet)mask.clone();
            lastWhite=white;
            lastHourNs=epochNs;
            report("12 AM画面検出: 白点="+white+" 基準="+base+" → 時刻を同期。ゲーム開始からの視覚遅延は要検証");
            SovereignService svc=SovereignService.connected();
            if(svc!=null)svc.runSynchronizedWave(epochNs);
            else report("アクセシビリティサービス未接続。同期したが自動操作は実行不可");
        } else strongFrames=0;
    }

    private void trackClockChange(BitSet mask,int white,long at) {
        if(oldMask==null || at-lastHourNs<30000000000L)return;
        // Detect digit change as a persistent difference of white-pixel geometry.
        BitSet diff=(BitSet)oldMask.clone();diff.xor(mask);
        int score=diff.cardinality();
        if(score<9) {
            candidateCount=0; candidateMask=null; return;
        }
        if(candidateMask==null) {
            candidateMask=(BitSet)mask.clone();
            candidateCount=1;
            return;
        }
        BitSet d=(BitSet)candidateMask.clone();d.xor(mask);
        if(d.cardinality()<=6)candidateCount++;
        else {candidateMask=(BitSet)mask.clone(); candidateCount=1;}
        if(candidateCount>=3) {
            hour++;
            lastHourNs=at;
            oldMask=(BitSet)mask.clone();
            candidateMask=null;candidateCount=0;
            long expected=epochNs+hour*90000000000L;
            long deltaMs=(at-expected)/1000000L;
            report(hour+" AM検出: 初期同期との差 "+(deltaMs>=0?"+":"")+deltaMs+
                    "ms。表示時刻から新基準を算出");
            SovereignService svc=SovereignService.connected();
            if(svc!=null)svc.recordHourAnchor(hour,at,deltaMs);
        }
    }

    private void report(String message) {
        Log.i("SovereignClockSync",message);
        SharedPreferences prefs=getSharedPreferences(SovereignService.PREFS,MODE_PRIVATE);
        String old=prefs.getString("HISTORY","");
        String next="[CLOCK] "+message+"\n"+old;
        if(next.length()>3500) next=next.substring(0,3500);
        prefs.edit().putString("STATUS",message).putString("HISTORY",next)
                .putLong("STATUS_AT",System.currentTimeMillis()).apply();
    }

    @Override public void onDestroy() {
        captureActive=false;
        try {if(reader!=null)reader.close();}catch(Exception ignored){}
        try {if(virtual!=null)virtual.release();}catch(Exception ignored){}
        try {if(projection!=null)projection.stop();}catch(Exception ignored){}
        if(processingThread!=null)processingThread.quitSafely();
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent) {return null;}
}
