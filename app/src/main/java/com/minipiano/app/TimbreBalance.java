package com.minipiano.app;

/** Offline-calibrated loudness contours. Lookup only during note-on; no audio-thread I/O. */
final class TimbreBalance {
    private static final int[] ANCHORS = {24, 48, 72, 96, 108};
    private static final double[][] DB = {
        {0, 0, 0, 0, 0},
        {6.775, 6.486, 12.523, 14.564, 6.949},
        {6.276, -2.631, -3.085, -12.167, -19.525}
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
    static double voiceLevel(int selectedSound, boolean sampled, int pitch, int velocity) {
        int actualSound = sampled ? selectedSound : PianoSound.SYNTHETIC;
        double strength = Math.max(0, Math.min(127, velocity)) / 127.0;
        return strength * (sampled ? .5 : .16) * gain(actualSound, pitch);
    }
    static float output(double piano, double volume, double click) {
        // The user's slider affects piano only; the click joins before the existing limiter.
        double mix = piano * 1.5 * volume + click;
        return (float) (mix / (1 + Math.abs(mix)));
    }
}
