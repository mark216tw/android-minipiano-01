package com.minipiano.app;

/** Maps the strike position on a key to MIDI velocity; lower on the key is louder. */
final class Velocity {
    static final int MIN = 28, MAX = 127;
    private Velocity() {}
    static int fromPosition(float top, float bottom, float y) {
        float span = Math.max(1, bottom - top);
        float position = Math.max(0, Math.min(1, (y - top) / span));
        return Math.round(MIN + position * (MAX - MIN));
    }
}
