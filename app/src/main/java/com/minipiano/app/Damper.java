package com.minipiano.app;

/** Pitch-dependent damper time; the top octave has no dampers. */
final class Damper {
    private static final double BASS = .225, TREBLE = .045, UNDAMPED = 1.8;
    private Damper() {}
    static double releaseSeconds(int pitch) {
        if (pitch >= 89) return UNDAMPED;
        double position = Math.max(0, Math.min(1, (89 - pitch) / 68.0));
        return TREBLE + position * (BASS - TREBLE);
    }
}
