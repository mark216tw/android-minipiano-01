package com.minipiano.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/** Time is expressed in quarter-note ticks, independently of playback speed. */
public final class Score {
    public static final int PPQ = 480;
    public String title = "我的演奏";
    public int bpm = 120, beats = 4, beatType = 4;
    public final List<Note> notes = new ArrayList<>();
    public final List<Pedal> pedals = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<Tempo> tempos = new ArrayList<>();
    public final List<Measure> measures = new ArrayList<>();
    public final Map<String, String> partNames = new LinkedHashMap<>();
    public final Set<String> mutedVoices = new LinkedHashSet<>();
    public final Set<String> features = new LinkedHashSet<>();
    public String originalXml;
    public long endTick;
    public int originalMeasureCount;
    private TempoMap timeline;

    public static final class Note {
        public final int pitch, velocity;
        public final String part, voice;
        public final int staff, staffMask;
        public final long start, duration;
        public Note(int pitch, long start, long duration, int velocity) {
            this(pitch, start, duration, velocity, "P1", "1", pitch < 60 ? 2 : 1);
        }
        public Note(int pitch, long start, long duration, int velocity, String part, String voice, int staff) {
            this(pitch, start, duration, velocity, part, voice, staff, 1 << (Math.max(1, Math.min(32, staff)) - 1));
        }
        public Note(int pitch, long start, long duration, int velocity, String part, String voice, int staff, int staffMask) {
            this.pitch = pitch; this.start = Math.max(0, start);
            this.duration = Math.max(1, duration); this.velocity = Math.max(1, Math.min(127, velocity));
            this.part = part; this.voice = voice; this.staff = staff; this.staffMask = staffMask;
        }
        public long end() { return start + duration; }
        public String channelKey() { return part + "\u001f" + voice; }
    }
    public static final class Pedal {
        public final long tick;
        public final boolean down;
        public final String part;
        public Pedal(long tick, boolean down) { this(tick, down, "P1"); }
        public Pedal(long tick, boolean down, String part) { this.tick = Math.max(0, tick); this.down = down; this.part = part; }
    }
    public static final class Tempo {
        public final long tick; public final double bpm;
        public Tempo(long tick, double bpm) { this.tick = Math.max(0, tick); this.bpm = bpm; }
    }
    public static final class Measure {
        public final long tick, duration; public final String number; public final int visit;
        public Measure(long tick, long duration, String number, int visit) { this.tick = tick; this.duration = duration; this.number = number; this.visit = visit; }
    }
    public static final class Channel {
        public final String key, part, voice, label;
        Channel(String key, String part, String voice, String label) { this.key = key; this.part = part; this.voice = voice; this.label = label; }
    }
    public List<Channel> channels() {
        Map<String, Note> first = new LinkedHashMap<>(); Map<String, Integer> masks = new LinkedHashMap<>();
        for (Note n : notes) { first.putIfAbsent(n.channelKey(), n); masks.put(n.channelKey(), masks.getOrDefault(n.channelKey(), 0) | n.staffMask); }
        List<Channel> result = new ArrayList<>();
        for (Map.Entry<String, Note> entry : first.entrySet()) {
            Note n = entry.getValue(); StringBuilder staves = new StringBuilder(); int mask = masks.get(entry.getKey());
            for (int staff = 1; staff <= 32; staff++) if ((mask & (1 << (staff - 1))) != 0) { if (staves.length() > 0) staves.append("、"); staves.append(staff); }
            result.add(new Channel(entry.getKey(), n.part, n.voice, partNames.getOrDefault(n.part, n.part) + " · 聲部 " + n.voice + "（譜表 " + staves + "）"));
        }
        return result;
    }
    public void sort() {
        notes.sort(Comparator.comparingLong((Note n) -> n.start).thenComparingInt(n -> n.pitch).thenComparing(n -> n.part).thenComparing(n -> n.voice));
        pedals.sort(Comparator.comparingLong(p -> p.tick));
        tempos.sort(Comparator.comparingLong(t -> t.tick));
        java.util.TreeMap<Long, Double> events = new java.util.TreeMap<>(); for (Tempo tempo : tempos) events.put(tempo.tick, tempo.bpm);
        tempos.clear(); double previous = Double.NaN;
        for (Map.Entry<Long, Double> entry : events.entrySet()) if (Double.isNaN(previous) || Math.abs(previous - entry.getValue()) > .00001) { tempos.add(new Tempo(entry.getKey(), entry.getValue())); previous = entry.getValue(); }
        timeline = null;
    }
    public long length() {
        long length = endTick;
        for (Note n : notes) length = Math.max(length, n.end());
        for (Pedal p : pedals) length = Math.max(length, p.tick);
        return length;
    }
    public TempoMap timeline() { if (timeline == null) timeline = new TempoMap(bpm, tempos); return timeline; }
    public Measure measureAt(long tick) {
        if (measures.isEmpty()) return null;
        int low = 0, high = measures.size();
        while (low + 1 < high) { int mid = (low + high) >>> 1; if (measures.get(mid).tick <= tick) low = mid; else high = mid; }
        return measures.get(low);
    }
    public double seconds(long tick) { return tempos.isEmpty() ? tick * 60.0 / (bpm * PPQ) : timeline().seconds(tick); }
    public long tick(double seconds) { return Math.round(tempos.isEmpty() ? seconds * bpm * PPQ / 60.0 : timeline().tick(seconds)); }
    public Score copy() {
        Score result = new Score(); result.title = title; result.bpm = bpm; result.beats = beats; result.beatType = beatType;
        result.notes.addAll(notes); result.pedals.addAll(pedals); result.tempos.addAll(tempos); result.measures.addAll(measures);
        result.partNames.putAll(partNames); result.mutedVoices.addAll(mutedVoices); result.features.addAll(features); result.warnings.addAll(warnings);
        result.originalXml = originalXml; result.endTick = endTick; result.originalMeasureCount = originalMeasureCount; result.sort(); return result;
    }
    public String importSummary() {
        return title + "\n" + notes.size() + " 個音符 · " + channels().size() + " 個聲部\n"
            + (tempos.isEmpty() ? 1 : timeline().size()) + " 個速度段 · " + measures.size() + " 個演奏小節（原譜 " + originalMeasureCount + "）\n"
            + (features.isEmpty() ? "基本音符、和弦與休止符" : "已處理：" + String.join("、", features))
            + (warnings.isEmpty() ? "" : "\n\n提醒：\n" + String.join("\n", warnings));
    }
    public Score quantized(int step) {
        if (step <= 0) throw new IllegalArgumentException("量化精度必須大於零");
        Score result = new Score();
        result.title = title; result.bpm = bpm; result.beats = beats; result.beatType = beatType;
        for (Note n : notes) {
            long start = Math.round((double) n.start / step) * step;
            long end = Math.round((double) n.end() / step) * step;
            result.notes.add(new Note(n.pitch, start, Math.max(step, end - start), n.velocity, n.part, n.voice, n.staff, n.staffMask));
        }
        for (Pedal p : pedals) result.pedals.add(new Pedal(Math.round((double) p.tick / step) * step, p.down, p.part));
        result.tempos.addAll(tempos); result.partNames.putAll(partNames);
        result.sort(); return result;
    }
    public static Score demo() {
        Score score = new Score(); score.title = "示範 · 小星星"; score.bpm = 100;
        int[] melody = {60,60,67,67,69,69,67,65,65,64,64,62,62,60};
        long cursor = 0;
        for (int i = 0; i < melody.length; i++) {
            long duration = (i == 6 || i == 13) ? 960 : 480;
            score.notes.add(new Note(melody[i], cursor, duration, 90)); cursor += duration;
        }
        for (int i = 0; i < 4; i++) score.notes.add(new Note(i == 2 ? 53 : 48, i * 1920L, 1800, 65, "P1", "2", 2));
        score.partNames.put("P1", "Piano");
        score.sort(); return score;
    }
}
