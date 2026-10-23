package com.minipiano.app;

/** Offline-calibrated loudness contours. Lookup only during note-on; no audio-thread I/O. */
final class TimbreBalance {
    private static final int[] ANCHORS = {24, 48, 72, 96, 108};
    private static final double[][] DB = {
        {0, 0, 0, 0, 0},
        {6.786, 6.499, 12.536, 14.591, 7.068},
        {6.325, -2.633, -3.091, -12.093, -19.449}
    };
    private static final double[][] GAINS = createGains();
    private static double[][] createGains() {
        double[][] gains = new double[3][128];
        for (int sound = 0; sound < gains.length; sound++) {
            for (int pitch = 0; pitch < 128; pitch++) {
                int region = 0;
                while (region < ANCHORS.length - 2 && pitch > ANCHORS[region + 1]) region++;
                double blend = Math.max(0, Math.min(1, (pitch - ANCHORS[region]) / (double) (ANCHORS[region + 1] - ANCHORS[region])));
                double db = DB[sound][region] + blend * (DB[sound][region + 1] - DB[sound][region]);
                gains[sound][pitch] = Math.pow(10, db / 20);
            }
        }
        return gains;
    }
    static void prepare() { /* Initializes the lookup on the creating/UI thread. */ }
    static double gain(int sound, int pitch) { return GAINS[sound][Math.max(0, Math.min(127, pitch))]; }
    static double strength(int velocity) { return Math.pow(Math.max(0, Math.min(127, velocity)) / 127.0, .85); }
    static double voiceLevel(int selectedSound, boolean sampled, int pitch, int velocity) {
        int actualSound = sampled ? selectedSound : PianoSound.SYNTHETIC;
        return strength(velocity) * (sampled ? .5 : .16) * gain(actualSound, pitch);
    }
}
