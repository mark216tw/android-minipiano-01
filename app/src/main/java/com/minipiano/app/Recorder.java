package com.minipiano.app;

import java.util.HashMap;
import java.util.Map;

/** Called only by the UI thread. Keeps the unquantized performance. */
final class Recorder {
    private final Map<Integer, Held> held = new HashMap<>();
    private Score score;
    private long origin;
    private boolean pedal;
    private static final class Held {
        final int pitch; final long tick;
        Held(int pitch, long tick) { this.pitch = pitch; this.tick = tick; }
    }
    boolean active() { return score != null; }
    void start(int bpm, int beats, int beatType) {
        score = new Score(); score.bpm = bpm; score.beats = beats; score.beatType = beatType;
        origin = System.nanoTime(); held.clear(); pedal = false;
    }
    private long now() { return score.tick((System.nanoTime() - origin) / 1e9); }
    void on(int id, int pitch) {
        if (!active()) return;
        off(id); held.put(id, new Held(pitch, now()));
    }
    void off(int id) {
        if (!active()) return;
        Held h = held.remove(id);
        if (h != null) score.notes.add(new Score.Note(h.pitch, h.tick, now() - h.tick, 90));
    }
    void pedal(boolean down) {
        if (active() && pedal != down) { score.pedals.add(new Score.Pedal(now(), down)); pedal = down; }
    }
    Score stop() {
        for (Integer id : held.keySet().toArray(new Integer[0])) off(id);
        if (pedal) pedal(false);
        Score result = score; score = null;
        if (result != null) result.sort();
        return result;
    }
}
