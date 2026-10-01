package com.minipiano.app;

import android.content.Context;

final class PianoSound {
    static final int SALAMANDER = 0, UPRIGHT = 1, SYNTHETIC = 2;
    static final String[] NAMES = {"Salamander 大鋼琴", "VSCO 直立鋼琴", "合成鋼琴"};
    static final String[] DIRECTORIES = {"piano", "upright", ""};
    static int selected(Context context) {
        var preferences = UiTheme.preferences(context);
        if (!preferences.contains("pianoSound")) {
            int sound = preferences.getBoolean("sampledPiano", true) ? SALAMANDER : SYNTHETIC;
            preferences.edit().putInt("pianoSound", sound).apply(); return sound;
        }
        int sound = preferences.getInt("pianoSound", SALAMANDER);
        return sound >= SALAMANDER && sound <= SYNTHETIC ? sound : SALAMANDER;
    }
    static void select(Context context, int sound) {
        if (sound < SALAMANDER || sound > SYNTHETIC) throw new IllegalArgumentException("Invalid piano sound");
        UiTheme.preferences(context).edit().putInt("pianoSound", sound).putBoolean("sampledPiano", sound != SYNTHETIC).apply();
    }
}
