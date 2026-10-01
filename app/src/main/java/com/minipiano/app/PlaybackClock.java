package com.minipiano.app;

import java.util.Arrays;

/** Tracks score time and actually-heard note age separately, including speed changes. */
final class PlaybackClock {
    private final PlaybackProgram program;
    private final int rate;
    private final double[] starts;
    private double sourceSeconds, elapsedSeconds;
    private double speed = 1;
    PlaybackClock(PlaybackProgram program, int rate) { this.program = program; this.rate = rate; starts = new double[program.score.notes.size()]; Arrays.fill(starts, Double.NaN); }
    void speed(double value) { if (!Double.isFinite(value) || value <= 0 || value > 4) throw new IllegalArgumentException("Invalid speed"); speed = value; }
    void advance(int frames) { double elapsed = frames / (double) rate; sourceSeconds = Math.min(program.duration, sourceSeconds + elapsed * speed); elapsedSeconds += elapsed; }
    boolean finished() { return sourceSeconds >= program.duration; }
    double seconds() { return sourceSeconds; }
    double tick() { return Math.min(program.length, program.timeline.tick(sourceSeconds)); }
    void seek(long tick) { sourceSeconds = program.timeline.seconds(Math.max(0, Math.min(program.length, tick))); elapsedSeconds = 0; Arrays.fill(starts, Double.NaN); }
    void onset(int id) { starts[id] = elapsedSeconds; }
    double age(int id) {
        if (Double.isNaN(starts[id])) {
            double age = Math.max(0, (sourceSeconds - program.timeline.seconds(program.score.notes.get(id).start)) / speed);
            starts[id] = elapsedSeconds - age;
        }
        return Math.max(0, elapsedSeconds - starts[id]);
    }
}
