package dev.sovereign.fnaf;

import android.app.Activity;
import android.content.Context;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQ_SCREEN_CAPTURE=2035;
    private final Map<String,EditText> fields=new LinkedHashMap<>();
    private SharedPreferences prefs;
    private TextView status;
    private TextView history;
    private CheckBox armed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences(SovereignService.PREFS,MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28,26,28,26);
        scroll.addView(content);
        setContentView(scroll);
        TextView title=new TextView(this);
        title.setText("SOVEREIGN · FIRST WAVE");
        title.setTextSize(23);
        title.setTypeface(null,Typeface.BOLD);
        content.addView(title);
        addText(content,"FNaF 1 Android · 4/20 · first 5 seconds\n"
                +"CAM4Bを最初に選択→フォクシー抑制→左・右ドア閉鎖。敵画像認識なし。\n"
                +"試作品はドアを再開放しません。5秒の入力確認用です。",15);
        Button enable=button(content,"① Accessibility設定を開く");
        enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        armed=new CheckBox(this);
        armed.setText("② FNaF表示中の音量＋で実行を許可");
        armed.setChecked(prefs.getBoolean("ARMED",false));
        armed.setOnCheckedChangeListener((b,checked)->prefs.edit().putBoolean("ARMED",checked).apply());
        content.addView(armed);
        addText(content,"【音量キーが反応しなくても試せる起動方法】アプリでボタンを押し、5秒以内にゲーム画面へ切り替える。",14);
        Button testTap=button(content,"③ 5秒後にモニターを1回タップ（最初にこちら）");
        testTap.setOnClickListener(v->startDelayed(true));
        Button testWave=button(content,"④ 5秒後に最初の9操作を開始");
        testWave.setOnClickListener(v->startDelayed(false));
        addText(content,"別の方法：ゲーム画面へ移動して音量＋で直接開始。音量−で中断。\n"
                +"※ 音量＋を押した瞬間が時刻0です。ゲーム内部の開始時刻と一致させる機能はまだありません。",13);
        status=addText(content,"",13);
        Button refresh=button(content,"動作状態を更新");
        refresh.setOnClickListener(v->refreshStatus());
        history=addText(content,"",12);
        addText(content,"【ゲーム内部時間への画面同期】",17);
        addText(content,"Custom Nightの開始画面で『12 AMを検出して同期開始』を押し、Androidの画面共有を許可してください。そのあとFNaFに戻り、Night 7を開始します。右上の12 AM時計が現れたフレームから時刻表を実行します。",13);
        Button sync=button(content,"⑤ 12 AMの出現を検出して自動開始（画面共有）");
        sync.setOnClickListener(v->requestClockSync());
        Button stopSync=button(content,"画面時計の監視を停止");
        stopSync.setOnClickListener(v->{
            ClockSyncCaptureService.stop(this);
            status.setText("画面時計監視の停止を要求しました");
        });
        addText(content,"※ 初回は時計出現の画像検出を検証する版です。時計の出現がゲーム内部0秒と完全一致するかは、この検証で確認します。時刻変化時のずれはログに残ります。",12);
        addText(content,"【操作位置】1280×720のゲーム内座標（変更可能）",17);
        for (int i=0;i<TouchConfig.NAMES.length;i++) {
            String n=TouchConfig.NAMES[i];
            addIntegerField(content,n+"_X",TouchConfig.DEFAULTS[i][0]);
            addIntegerField(content,n+"_Y",TouchConfig.DEFAULTS[i][1]);
        }
        addText(content,"【画面上のゲーム表示範囲】0幅/高さ指定の場合は画面全体",16);
        addIntegerField(content,"RECT_X",0);
        addIntegerField(content,"RECT_Y",0);
        addIntegerField(content,"RECT_WIDTH",0);
        addIntegerField(content,"RECT_HEIGHT",0);
        Button save=button(content,"座標設定を保存");
        save.setOnClickListener(v->saveConfig());
        addText(content,"時刻表：0.050 CAM UP、0.600 CAM4B、0.950 DOWN、3.450 左パン、"
                +"3.883 CAM UP、4.417 DOWN、4.483 左閉鎖、4.517 右パン、4.767 右閉鎖。",12);
        refreshStatus();
    }
    private TextView addText(LinearLayout c,String value,int size) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);
        v.setPadding(0,12,0,10);c.addView(v);return v;
    }
    private Button button(LinearLayout c,String label) {
        Button b=new Button(this);b.setText(label);c.addView(b);return b;
    }
    private void addIntegerField(LinearLayout c,String key,int fallback) {
        EditText e=new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(String.valueOf(prefs.getInt(key,fallback)));
        e.setHint(key);
        e.setTextSize(13);
        c.addView(e);fields.put(key,e);
    }
    private void saveConfig() {
        SharedPreferences.Editor editor=prefs.edit();
        try {
            for(Map.Entry<String,EditText> e:fields.entrySet()) {
                String v=e.getValue().getText().toString().trim();
                editor.putInt(e.getKey(),Integer.parseInt(v));
            }
        } catch(NumberFormatException ex) {
            status.setText("エラー：すべての座標欄に整数を入力してください");
            return;
        }
        editor.apply();status.setText("設定を保存しました");
    }
    private void requestClockSync() {
        if(SovereignService.connected()==null) {
            status.setText("時刻同期不可：まずアクセシビリティサービスを有効にしてください");
            return;
        }
        if(!prefs.getBoolean("ARMED",false)) {
            status.setText("時刻同期不可：『実行を許可』にチェックしてください");
            return;
        }
        MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(),REQ_SCREEN_CAPTURE);
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_SCREEN_CAPTURE)return;
        if(resultCode!=RESULT_OK || data==null) {
            status.setText("画面共有を許可しなかったため、時刻同期は開始していません");
            return;
        }
        Intent service=new Intent(this,ClockSyncCaptureService.class);
        service.putExtra(ClockSyncCaptureService.EXTRA_RESULT_CODE,resultCode);
        service.putExtra(ClockSyncCaptureService.EXTRA_RESULT_DATA,data);
        try {
            if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
            else startService(service);
            status.setText("画面時計監視を準備中。FNaFの開始画面へ戻り、夜を開始してください");
        } catch(Exception ex) {
            status.setText("画面時計監視開始失敗: "+ex.getClass().getSimpleName()+" "+ex.getMessage());
        }
    }

    private void startDelayed(boolean singleTap) {
        SovereignService svc=SovereignService.connected();
        if(svc==null) {
            status.setText("サービス未接続：Android設定でSovereign First Waveのアクセシビリティを有効にしてください。");
            return;
        }
        if(!prefs.getBoolean("ARMED",false)) {
            status.setText("実行許可がOFF：上のチェックをONにしてください。");
            return;
        }
        svc.scheduleDelayed(singleTap,5000);
        refreshStatus();
    }
    @Override protected void onResume() {
        super.onResume();
        if(status!=null) refreshStatus();
    }
    private void refreshStatus() {
        boolean connected=SovereignService.connected()!=null;
        status.setText("サービス: "+(connected?"接続済み":"未接続（設定で有効化してください）")+
                "\\n実行許可: "+(prefs.getBoolean("ARMED",false)?"ON":"OFF")+
                "\\n直近: "+prefs.getString("STATUS","履歴なし"));
        if(history!=null)history.setText("【動作ログ・最新順】\\n"+
                prefs.getString("HISTORY","ログなし"));
    }
}
