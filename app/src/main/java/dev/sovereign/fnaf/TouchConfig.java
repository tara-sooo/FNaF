package dev.sovereign.fnaf;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;
import android.content.Context;

/** Game-local (1280x720) points, scaled to the actual Android display. */
public final class TouchConfig {
    private TouchConfig() { }
    public static final String[] NAMES = {
            "MONITOR", "CAMERA4B", "PAN_LEFT", "PAN_RIGHT", "LEFT_DOOR", "RIGHT_DOOR"
    };
    // APK coordinate provenance: CAM4B instance 1089,644; door trigger Y343/353;
    // left/right x54/1228 with room pan clamped to center640/960;
    // monitorFlipTrigger y=640 (g835), actual clickable width may vary by overlay.
    // Pan touches at y=110 avoid button collisions.
    public static final int[][] DEFAULTS = {
            {640, 650}, {1089, 644}, {40, 110}, {1240, 110}, {54, 343}, {1228, 353}
    };

    public static Point gamePoint(SharedPreferences prefs, FirstWavePlan.Key key) {
        int n=key.ordinal();
        return new Point(prefs.getInt(NAMES[n]+"_X",DEFAULTS[n][0]),
                prefs.getInt(NAMES[n]+"_Y",DEFAULTS[n][1]));
    }

    public static Point devicePoint(Context context,SharedPreferences prefs,FirstWavePlan.Key key) {
        Point gp=gamePoint(prefs,key);
        WindowManager wm=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display d=wm.getDefaultDisplay();
        Point display=new Point();
        d.getRealSize(display);
        // User can override the content rectangle for letterboxing/cutouts.
        int x=prefs.getInt("RECT_X",0), y=prefs.getInt("RECT_Y",0);
        int width=prefs.getInt("RECT_WIDTH",0);
        int height=prefs.getInt("RECT_HEIGHT",0);
        if(width<=0)width=display.x;
        if(height<=0)height=display.y;
        int dx=x+Math.round(gp.x*width/1280f);
        int dy=y+Math.round(gp.y*height/720f);
        return new Point(Math.min(Math.max(1,dx),display.x-2),
                Math.min(Math.max(1,dy),display.y-2));
    }
}
