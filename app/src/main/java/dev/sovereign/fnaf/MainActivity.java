package dev.sovereign.fnaf;

import android.app.Activity;
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
    private final Map<String,EditText> fields=new LinkedHashMap<>();
    private SharedPreferences prefs;
    private TextView status;
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
        addText(content,"③ ゲーム画面へ移動して、開始時点で音量＋を押す。音量−で中断。\n"
                +"※ 音量＋を押した瞬間が時刻0です。ゲーム内部の開始時刻と一致させる機能はまだありません。",13);
        status=addText(content,"",13);
        Button refresh=button(content,"動作状態を更新");
        refresh.setOnClickListener(v->refreshStatus());
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
    private void refreshStatus() {
        status.setText("STATUS: "+prefs.getString("STATUS","サービス未接続または未実行"));
    }
}
