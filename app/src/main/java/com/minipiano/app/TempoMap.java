package com.minipiano.app;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

/** Immutable piecewise-constant tempo, with invertible tick/second conversion. */
public final class TempoMap {
    private final long[] ticks;
    private final double[] seconds, bpms;
    public TempoMap(double initial, List<Score.Tempo> changes) {
        TreeMap<Long, Double> map = new TreeMap<>(); map.put(0L, valid(initial));
        for (Score.Tempo tempo : changes) map.put(tempo.tick, valid(tempo.bpm));
        List<Map.Entry<Long, Double>> entries = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : map.entrySet()) if (entries.isEmpty() || Math.abs(entries.get(entries.size() - 1).getValue() - entry.getValue()) > .00001) entries.add(entry);
        ticks = new long[entries.size()]; seconds = new double[entries.size()]; bpms = new double[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            ticks[i] = entries.get(i).getKey(); bpms[i] = entries.get(i).getValue();
            if (i > 0) seconds[i] = seconds[i - 1] + (ticks[i] - ticks[i - 1]) * 60.0 / (bpms[i - 1] * Score.PPQ);
        }
    }
    private static double valid(double value) { if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid tempo"); return value; }
    public int size() { return ticks.length; }
    public double seconds(double tick) { int i = tickIndex(tick); return seconds[i] + (Math.max(0, tick) - ticks[i]) * 60 / (bpms[i] * Score.PPQ); }
    public double tick(double time) {
        int low = 0, high = seconds.length;
        while (low + 1 < high) { int mid = (low + high) >>> 1; if (seconds[mid] <= time) low = mid; else high = mid; }
        return ticks[low] + (Math.max(0, time) - seconds[low]) * bpms[low] * Score.PPQ / 60;
    }
    public double bpmAt(double tick) { return bpms[tickIndex(tick)]; }
    private int tickIndex(double tick) {
        int low = 0, high = ticks.length;
        while (low + 1 < high) { int mid = (low + high) >>> 1; if (ticks[mid] <= tick) low = mid; else high = mid; }
        return low;
    }
}
