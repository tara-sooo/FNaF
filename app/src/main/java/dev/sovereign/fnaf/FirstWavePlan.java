package dev.sovereign.fnaf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Source-aligned first-wave input program, with game coordinate system 1280x720.
 * t=0 is a manually-triggered controller epoch (not automatically gameplay start).
 * All positions are user-calibratable via MainActivity preferences.
 * No enemy vision, power estimates or camera screenshots are used.
 */
public final class FirstWavePlan {
    private FirstWavePlan() { }
    public enum Key { MONITOR, CAMERA4B, PAN_LEFT, PAN_RIGHT, LEFT_DOOR, RIGHT_DOOR }

    public static final class Step {
        public final long atMs;
        public final long durationMs;
        public final Key key;
        public final String description;
        public Step(long atMs, long durationMs, Key key, String description) {
            if (atMs < 0 || durationMs <= 0) throw new IllegalArgumentException();
            this.atMs = atMs;
            this.durationMs = durationMs;
            this.key = key;
            this.description = description;
        }
        public long endMs() { return atMs + durationMs; }
    }

    public static List<Step> build() {
        List<Step> x = new ArrayList<>();
        // Initialize selected camera. Camera4B is clicked after initial UP completes.
        // The display becomes active after ~23 60-FPS updates; >0.50 s allowed here.
        x.add(new Step(50, 34, Key.MONITOR, "CAM UP (initial selection)"));
        x.add(new Step(600, 34, Key.CAMERA4B, "Select CAM4B"));
        x.add(new Step(950, 34, Key.MONITOR, "CAM DOWN (initial selection)"));
        // Move fully left before the precisely scheduled Fox camera pulse.
        x.add(new Step(3450, 320, Key.PAN_LEFT, "Pan toward left door"));
        x.add(new Step(3883, 34, Key.MONITOR, "CAM UP: suppress Fox"));
        x.add(new Step(4417, 34, Key.MONITOR, "CAM DOWN"));
        // Both doors start closing before corresponding checks (4.970/4.980).
        // 250ms pan gives ~15 frames at full touch pan speed before right click.
        x.add(new Step(4483, 34, Key.LEFT_DOOR, "LEFT door toggle → close"));
        x.add(new Step(4517, 233, Key.PAN_RIGHT, "Pan toward right door"));
        x.add(new Step(4767, 34, Key.RIGHT_DOOR, "RIGHT door toggle → close"));
        return Collections.unmodifiableList(x);
    }

    public static void validate(List<Step> steps) {
        long lastEnd = -1;
        long lastBegin = -1;
        for (Step s : steps) {
            if (s.atMs < lastBegin) throw new IllegalStateException("Unsorted: " + s.description);
            if (s.atMs < lastEnd) throw new IllegalStateException("Overlapping touches: " + s.description);
            lastBegin = s.atMs;
            lastEnd = s.endMs();
        }
        if (lastEnd > 6000) throw new IllegalStateException("POC must finish before 6s");
    }
}
