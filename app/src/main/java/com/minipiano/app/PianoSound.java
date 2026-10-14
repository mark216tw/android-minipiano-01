package com.minipiano.app;

import android.content.Context;

final class PianoSound {
    static final int SALAMANDER = 0, SYNTHETIC = 2;
    /** User-selectable timbres, in display order. */
    static final int[] SELECTABLE = {SALAMANDER, SYNTHETIC};
    static String name(int sound) { return sound == SYNTHETIC ? "合成鋼琴" : "Salamander 大鋼琴"; }
    static String directory(int sound) { return sound == SYNTHETIC ? "" : "piano"; }
    static int selected(Context context) {
        // 2 selects the synthetic timbre; anything else (including the retired VSCO id 1) is Salamander.
        return UiTheme.preferences(context).getInt("pianoSound", SALAMANDER) == SYNTHETIC ? SYNTHETIC : SALAMANDER;
    }
    static void select(Context context, int sound) {
        if (sound != SALAMANDER && sound != SYNTHETIC) throw new IllegalArgumentException("Invalid piano sound");
        UiTheme.preferences(context).edit().putInt("pianoSound", sound).apply();
    }
}
