package com.minipiano.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;

/** Parse authored events first; apply expression, expand the route, then join ties. */
final class MusicXmlReader {
    private static class Point {
        final int measure; final long local;
        long tick;
        Point(int measure, long local) { this.measure = measure; this.local = local; }
    }
    private static final class RawNote extends Point {
        final int pitch, staff; final String voice; final long duration;
        final boolean tieStart, tieStop; final Integer explicit; final int accent;
        int velocity;
        RawNote(int m, long local, int pitch, long duration, String voice, int staff, boolean start, boolean stop, Integer explicit, int accent) {
            super(m, local); this.pitch = pitch; this.duration = duration; this.voice = voice; this.staff = staff;
            tieStart = start; tieStop = stop; this.explicit = explicit; this.accent = accent;
        }
    }
    private static final class Dynamic extends Point {
        final int value, staff; final String voice; final boolean instant;
        Dynamic(int m, long tick, int value, int staff, String voice, boolean instant) { super(m, tick); this.value = value; this.staff = staff; this.voice = voice; this.instant = instant; }
        boolean matches(RawNote n) { return (staff == 0 || staff == n.staff) && (voice.isEmpty() || voice.equals(n.voice)); }
    }
    private static final class Wedge {
        final Point start; Point end; final int staff, sign; final String voice;
        Wedge(Point start, int staff, String voice, int sign) { this.start = start; this.staff = staff; this.voice = voice; this.sign = sign; }
        boolean matches(RawNote n) { return (staff == 0 || staff == n.staff) && (voice.isEmpty() || voice.equals(n.voice)); }
    }
    private static final class RawTempo extends Point {
        final double bpm;
        RawTempo(int m, long tick, double bpm) { super(m, tick); this.bpm = bpm; }
    }
    private static final class RawPedal extends Point {
        final boolean down;
        RawPedal(int m, long tick, boolean down) { super(m, tick); this.down = down; }
    }
    private static final class Part {
        final String id; final List<List<RawNote>> notes = new ArrayList<>();
        final List<Dynamic> dynamics = new ArrayList<>(); final List<Wedge> wedges = new ArrayList<>();
        final List<RawPedal> pedals = new ArrayList<>(); final List<RawTempo> tempos = new ArrayList<>();
        final Map<String, Wedge> openWedges = new HashMap<>();
        Part(String id) { this.id = id; }
    }
    static Score read(Document document, String original) throws Exception {
        Element root = document.getDocumentElement();
        if (!root.getTagName().equals("score-partwise")) throw new IOException("目前支援 score-partwise MusicXML");
        Score score = new Score(); score.originalXml = original;
        score.title = text(child(root, "work"), "work-title", text(root, "movement-title", "匯入樂曲"));
        Element partList = child(root, "part-list");
        for (Element part : children(partList, "score-part")) score.partNames.put(part.getAttribute("id"), text(part, "part-name", part.getAttribute("id")));
        List<Element> elements = children(root, "part");
        int count = 0; for (Element part : elements) count = Math.max(count, children(part, "measure").size());
        if (count == 0 || count > 10000) throw new IOException("樂譜小節數量無效");
        List<PlaybackRoute.Bar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) bars.add(new PlaybackRoute.Bar(Integer.toString(i + 1)));
        List<Part> parts = new ArrayList<>(); int noteCount = 0;
        boolean meterSeen = false;
        for (Element element : elements) {
            String id = element.getAttribute("id"); if (id.isEmpty()) id = "P" + (parts.size() + 1);
            for (Part previous : parts) if (previous.id.equals(id)) throw new IOException("重複的 part ID：" + id);
            Part part = new Part(id); parts.add(part); score.partNames.putIfAbsent(id, id);
            int divisions = 1, beats = 4, beatType = 4, transpose = 0;
            Map<Integer, Integer> staffTransposes = new HashMap<>();
            List<Element> measures = children(element, "measure");
            if (measures.size() != count) warn(score, "部分樂器的小節數不同，目前按小節索引對齊");
            for (int m = 0; m < measures.size(); m++) {
                Element measure = measures.get(m); PlaybackRoute.Bar bar = bars.get(m);
                if (parts.size() == 1 && !measure.getAttribute("number").isEmpty()) bar.number = measure.getAttribute("number");
                List<RawNote> notes = new ArrayList<>(); part.notes.add(notes);
                double cursor = 0, maximum = 0, chordStart = 0;
                for (Element item : children(measure, null)) {
                    switch (item.getTagName()) {
                        case "attributes": {
                            divisions = integer(text(item, "divisions", ""), divisions);
                            if (divisions < 1 || divisions > 1000000) throw new IOException("無效的 divisions");
                            Element time = child(item, "time");
                            if (time != null) {
                                beats = beats(text(time, "beats", "4")); beatType = integer(text(time, "beat-type", "4"), 4);
                                if (beats < 1 || beats > 32 || beatType < 1 || beatType > 64) throw new IOException("不支援的拍號");
                                if (!meterSeen) { score.beats = beats; score.beatType = beatType; meterSeen = true; }
                                else if (score.beats != beats || score.beatType != beatType) warn(score, "變拍號會保留於原譜轉存，演奏資料匯出使用起始拍號");
                                if (children(time, "beats").size() > 1) warn(score, "複合拍號目前採用第一組拍值");
                            }
                            for (Element trans : children(item, "transpose")) {
                                int value = integer(text(trans, "chromatic", "0"), 0) + 12 * integer(text(trans, "octave-change", "0"), 0);
                                if (trans.hasAttribute("number")) staffTransposes.put(integer(trans.getAttribute("number"), 1), value); else transpose = value;
                                score.features.add("移調");
                            }
                            break;
                        }
                        case "backup": case "forward": {
                            double duration = duration(item, divisions);
                            cursor += item.getTagName().equals("backup") ? -duration : duration;
                            if (cursor < -.00001) throw new IOException("第 " + bar.number + " 小節聲部時間位置無效");
                            cursor = Math.max(0, cursor); maximum = Math.max(maximum, cursor); break;
                        }
                        case "direction": direction(score, part, bars, m, item, cursor, divisions); break;
                        case "sound": sound(score, part, bar, m, Math.round(cursor + decimal(text(item, "offset", "0"), 0) * Score.PPQ / divisions), item, 0, ""); break;
                        case "note": {
                            if (child(item, "grace") != null) { warn(score, "裝飾音暫略過"); break; }
                            double duration = duration(item, divisions); boolean chord = child(item, "chord") != null;
                            double start = chord ? chordStart : cursor;
                            if (!chord) { chordStart = cursor; cursor += duration; }
                            maximum = Math.max(maximum, start + duration);
                            Element pitch = child(item, "pitch");
                            if (pitch == null) { if (child(item, "unpitched") != null) warn(score, "無固定音高的打擊樂已略過"); break; }
                            if (duration <= 0) throw new IOException("第 " + bar.number + " 小節音符缺少有效 duration");
                            int staff = integer(text(item, "staff", "1"), 1); if (staff < 1 || staff > 32) throw new IOException("不支援的 staff：" + staff);
                            String voice = text(item, "voice", "1");
                            String step = text(pitch, "step", "C");
                            int pc = switch (step) { case "C" -> 0; case "D" -> 2; case "E" -> 4; case "F" -> 5; case "G" -> 7; case "A" -> 9; case "B" -> 11; default -> throw new IOException("無效音高：" + step); };
                            double alter = decimal(text(pitch, "alter", "0"), 0);
                            if (alter != Math.rint(alter)) { warn(score, "微分音目前略過"); break; }
                            int midi = (integer(text(pitch, "octave", "4"), 4) + 1) * 12 + pc + (int) alter + staffTransposes.getOrDefault(staff, transpose);
                            if (midi < 21 || midi > 108) { warn(score, "鋼琴音域之外的音符已略過"); break; }
                            boolean tieStart = false, tieStop = false;
                            List<Element> ties = children(item, "tie"); ties.addAll(children(child(item, "notations"), "tied"));
                            for (Element tie : ties) { String type = tie.getAttribute("type"); tieStart |= type.equals("start") || type.equals("continue"); tieStop |= type.equals("stop") || type.equals("continue"); }
                            Integer explicit = item.hasAttribute("dynamics") ? midiVelocity(decimal(item.getAttribute("dynamics"), 100)) : null;
                            if (explicit != null) score.features.add("力度");
                            Element notations = child(item, "notations"), articulation = child(notations, "articulations");
                            int accent = child(articulation, "strong-accent") != null ? 20 : child(articulation, "accent") != null ? 12 : 0;
                            if (accent > 0) score.features.add("重音");
                            if (child(notations, "arpeggiate") != null) warn(score, "琶音記號目前以同時和弦播放");
                            if (child(articulation, "staccato") != null || child(articulation, "tenuto") != null) warn(score, "斷奏／持音記號目前保留原音符時值");
                            notes.add(new RawNote(m, Math.round(start), midi, Math.max(1, Math.round(start + duration) - Math.round(start)), voice, staff, tieStart, tieStop, explicit, accent));
                            if (++noteCount > 50000) throw new IOException("原譜超過 50,000 音符");
                            break;
                        }
                        case "barline": barline(score, bars, m, item); break;
                        default: break;
                    }
                }
                long nominal = (long) beats * Score.PPQ * 4 / beatType;
                long length = measure.getAttribute("implicit").equals("yes") ? Math.round(maximum) : Math.max(Math.round(maximum), nominal);
                bar.length = Math.max(bar.length, length);
            }
            if (!part.openWedges.isEmpty()) warn(score, "部分漸強／漸弱缺少終點，未套用該區間");
        }
        long[] starts = new long[count + 1];
        for (int i = 0; i < count; i++) {
            PlaybackRoute.Bar bar = bars.get(i);
            if (bar.length <= 0) bar.length = (long) score.beats * Score.PPQ * 4 / score.beatType; starts[i + 1] = starts[i] + bar.length;
            for (long position : bar.jumpPositions) if (position > 0 && position < bar.length) warn(score, "第 " + bar.number + " 小節的中途跳轉目前在小節結尾執行");
        }
        TreeMap<Long, Double> tempos = new TreeMap<>(); Map<Long, String> tempoOwners = new HashMap<>();
        for (Part part : parts) {
            for (List<RawNote> notes : part.notes) for (RawNote note : notes) locate(note, starts);
            for (Dynamic mark : part.dynamics) locate(mark, starts); part.dynamics.sort(Comparator.comparingLong(p -> p.tick));
            for (Wedge wedge : part.wedges) { locate(wedge.start, starts); if (wedge.end != null) locate(wedge.end, starts); }
            for (RawPedal pedal : part.pedals) locate(pedal, starts); part.pedals.sort(Comparator.comparingLong(p -> p.tick));
            for (RawTempo tempo : part.tempos) {
                locate(tempo, starts); Double previous = tempos.get(tempo.tick);
                if (previous == null || part.id.equals(tempoOwners.get(tempo.tick))) { tempos.put(tempo.tick, tempo.bpm); tempoOwners.put(tempo.tick, part.id); }
                else if (Math.abs(previous - tempo.bpm) > .001) warn(score, "不同樂器的同時速度標記衝突，採用第一個樂器速度值");
            }
            for (List<RawNote> notes : part.notes) for (RawNote note : notes) note.velocity = velocity(part, note);
        }
        score.bpm = tempos.containsKey(0L) ? (int) Math.round(tempos.get(0L)) : 120;
        tempos.putIfAbsent(0L, (double) score.bpm);
        List<Integer> route = PlaybackRoute.expand(bars, message -> warn(score, message));
        score.originalMeasureCount = count;
        int previousBar = -1; long output = 0; Map<String, Score.Note> ties = new HashMap<>(); int[] visits = new int[count];
        Map<String, Boolean> playedPedals = new HashMap<>();
        for (int index : route) {
            PlaybackRoute.Bar bar = bars.get(index); boolean jump = previousBar >= 0 && previousBar + 1 != index;
            if (jump) { if (!ties.isEmpty()) warn(score, "跳轉邊界的延音線已分開起音"); ties.clear(); }
            long begin = starts[index], end = starts[index + 1];
            score.measures.add(new Score.Measure(output, bar.length, bar.number, ++visits[index]));
            score.tempos.add(new Score.Tempo(output, tempos.floorEntry(begin).getValue()));
            for (Map.Entry<Long, Double> tempo : tempos.subMap(begin, false, end, false).entrySet()) score.tempos.add(new Score.Tempo(output + tempo.getKey() - begin, tempo.getValue()));
            for (Part part : parts) {
                boolean pedalDown = false;
                for (RawPedal pedal : part.pedals) { if (pedal.tick >= begin) break; pedalDown = pedal.down; }
                boolean played = playedPedals.getOrDefault(part.id, false);
                if (jump && played) { score.pedals.add(new Score.Pedal(output, false, part.id)); played = false; }
                if (played != pedalDown) score.pedals.add(new Score.Pedal(output, pedalDown, part.id));
                played = pedalDown;
                for (RawPedal pedal : part.pedals) if (pedal.tick >= begin && (pedal.tick < end || (index == count - 1 && pedal.tick == end))) {
                    score.pedals.add(new Score.Pedal(output + pedal.tick - begin, pedal.down, part.id)); played = pedal.down;
                }
                playedPedals.put(part.id, played);
                if (index >= part.notes.size()) continue;
                for (RawNote raw : part.notes.get(index)) {
                    long tick = output + raw.local; String key = part.id + "\u001f" + raw.voice + "\u001f" + raw.pitch;
                    Score.Note previous = raw.tieStop ? ties.remove(key) : null;
                    if (raw.tieStop && previous == null) warn(score, "部分延音線終點未找到相同樂器／聲部的起點");
                    if (previous != null && previous.end() != tick) { warn(score, "時間不連續的延音線已分開起音"); previous = null; }
                    Score.Note note;
                    if (previous != null) {
                        score.notes.remove(previous);
                        note = new Score.Note(raw.pitch, previous.start, previous.duration + raw.duration, previous.velocity, part.id, raw.voice, previous.staff, previous.staffMask | (1 << (raw.staff - 1)));
                        score.features.add(previous.staff != raw.staff ? "跨譜表延音線" : "延音線");
                    } else note = new Score.Note(raw.pitch, tick, raw.duration, raw.velocity, part.id, raw.voice, raw.staff);
                    score.notes.add(note); if (raw.tieStart) ties.put(key, note);
                    if (score.notes.size() > 100000) throw new IOException("展開反覆後超過 100,000 音符");
                }
            }
            output += bar.length; previousBar = index;
        }
        score.endTick = output;
        if (!ties.isEmpty()) warn(score, "部分延音線缺少對應終點");
        if (score.notes.isEmpty()) throw new IOException("樂譜中沒有可播放的鋼琴音符");
        score.sort();
        if (score.seconds(score.length()) > 7200) throw new IOException("演奏長度超過兩小時，請檢查反覆與速度");
        return score;
    }
    private static void direction(Score score, Part part, List<PlaybackRoute.Bar> bars, int m, Element direction, double cursor, int divisions) throws IOException {
        long tick = Math.round(cursor + decimal(text(direction, "offset", "0"), 0) * Score.PPQ / divisions);
        int staff = integer(text(direction, "staff", "0"), 0); String voice = text(direction, "voice", "");
        Element sound = child(direction, "sound");
        if (sound != null) {
            long soundTick = child(sound, "offset") == null ? tick : Math.round(cursor + decimal(text(sound, "offset", "0"), 0) * Score.PPQ / divisions);
            sound(score, part, bars.get(m), m, soundTick, sound, staff, voice);
        }
        for (Element dt : children(direction, "direction-type")) {
            Element metronome = child(dt, "metronome");
            if (metronome != null && (sound == null || sound.getAttribute("tempo").isEmpty())) {
                double value = decimal(text(metronome, "per-minute", ""), Double.NaN);
                double unit = switch (text(metronome, "beat-unit", "quarter")) { case "whole" -> 4; case "half" -> 2; case "quarter" -> 1; case "eighth" -> .5; case "16th" -> .25; case "32nd" -> .125; case "64th" -> .0625; default -> Double.NaN; };
                double scale = 1, fraction = .5; for (Element dot : children(metronome, "beat-unit-dot")) { scale += fraction; fraction /= 2; }
                tempo(score, part, m, tick, value * unit * scale);
            }
            Element dynamics = child(dt, "dynamics");
            if (dynamics != null && (sound == null || sound.getAttribute("dynamics").isEmpty())) {
                for (Element mark : children(dynamics, null)) {
                    String name = mark.getTagName(); int value = dynamicValue(name);
                    if (value == 0) { warn(score, "未解讀力度記號：" + name); continue; }
                    boolean instant = name.equals("fp") || name.startsWith("sf") || name.equals("fz") || name.equals("rfz");
                    part.dynamics.add(new Dynamic(m, tick, value, staff, voice, instant)); score.features.add("力度");
                    if (name.equals("fp")) part.dynamics.add(new Dynamic(m, tick, 40, staff, voice, false));
                }
            }
            Element wedge = child(dt, "wedge");
            if (wedge != null) {
                String number = wedge.hasAttribute("number") ? wedge.getAttribute("number") : "1";
                String key = number + ":" + staff + ":" + voice; String type = wedge.getAttribute("type");
                if (type.equals("crescendo") || type.equals("diminuendo")) {
                    Wedge w = new Wedge(new Point(m, tick), staff, voice, type.equals("crescendo") ? 1 : -1);
                    part.wedges.add(w); if (part.openWedges.put(key, w) != null) warn(score, "漸強／漸弱起點編號重複，部分區間未套用"); score.features.add("漸強／漸弱");
                } else if (type.equals("stop")) {
                    Wedge w = part.openWedges.remove(key);
                    if (w == null) {
                        String candidate = null; boolean ambiguous = false;
                        for (Map.Entry<String, Wedge> entry : part.openWedges.entrySet()) {
                            Wedge open = entry.getValue();
                            if (entry.getKey().startsWith(number + ":") && (staff == 0 || open.staff == 0 || open.staff == staff) && (voice.isEmpty() || open.voice.equals(voice))) {
                                if (candidate != null) ambiguous = true; candidate = entry.getKey();
                            }
                        }
                        if (!ambiguous && candidate != null) w = part.openWedges.remove(candidate);
                    }
                    if (w == null) warn(score, "漸強／漸弱終點未找到對應起點（number=" + number + "）"); else w.end = new Point(m, tick);
                }
            }
            Element pedal = child(dt, "pedal");
            if (pedal != null) {
                String type = pedal.getAttribute("type");
                if (type.equals("change")) { part.pedals.add(new RawPedal(m, tick, false)); part.pedals.add(new RawPedal(m, tick, true)); }
                else if (type.equals("start") || type.equals("resume")) part.pedals.add(new RawPedal(m, tick, true));
                else if (type.equals("stop") || type.equals("discontinue")) part.pedals.add(new RawPedal(m, tick, false));
                score.features.add("踏板");
            }
            if (child(dt, "segno") != null && bars.get(m).segno.isEmpty()) bars.get(m).segno = "default";
            if (child(dt, "coda") != null && bars.get(m).coda.isEmpty()) bars.get(m).coda = "default";
            for (Element words : children(dt, "words")) {
                String value = words.getTextContent().trim().toLowerCase(Locale.ROOT), normalized = value.replaceAll("[\\s.]+", "");
                if (normalized.equals("fine")) { if (sound == null || !sound.hasAttribute("fine")) { bars.get(m).fine = true; bars.get(m).jumpPositions.add(tick); score.features.add("跳轉"); } }
                else if (normalized.equals("tocoda")) { if (bars.get(m).toCoda.isEmpty()) { bars.get(m).toCoda = "default"; bars.get(m).jumpPositions.add(tick); score.features.add("跳轉"); } }
                else if (dcWords(normalized)) { if (sound == null || !sound.hasAttribute("dacapo")) { bars.get(m).dc = true; bars.get(m).jumpPositions.add(tick); score.features.add("跳轉"); } }
                else if (dsWords(normalized)) { if (bars.get(m).ds.isEmpty()) { bars.get(m).ds = "default"; bars.get(m).jumpPositions.add(tick); score.features.add("跳轉"); } }
                else if (value.matches(".*\\b(ritard(?:ando)?|rit|rall(?:entando)?|accel(?:erando)?|riten(?:uto)?)(?=\\b|[.\\s]).*")) {
                    if (sound == null || sound.getAttribute("tempo").isEmpty()) warn(score, "文字速度未自動解讀：" + words.getTextContent().trim());
                } else if (normalized.equals("cresc") || normalized.equals("dim")) warn(score, "文字力度未自動解讀：" + words.getTextContent().trim());
            }
        }
    }
    private static boolean dcWords(String value) { return value.equals("dc") || value.equals("dcalfine") || value.equals("dcalcoda") || value.equals("dacapo") || value.equals("dacapoalfine") || value.equals("dacapoalcoda"); }
    private static boolean dsWords(String value) { return value.equals("ds") || value.equals("dsalfine") || value.equals("dsalcoda") || value.equals("dalsegno") || value.equals("dalsegnoalfine") || value.equals("dalsegnoalcoda"); }
    private static void sound(Score score, Part part, PlaybackRoute.Bar bar, int m, long tick, Element sound, int staff, String voice) {
        if (sound.hasAttribute("tempo")) tempo(score, part, m, tick, decimal(sound.getAttribute("tempo"), Double.NaN));
        if (sound.hasAttribute("dynamics")) { part.dynamics.add(new Dynamic(m, tick, midiVelocity(decimal(sound.getAttribute("dynamics"), 100)), staff, voice, false)); score.features.add("力度"); }
        if (sound.hasAttribute("damper-pedal")) { String value = sound.getAttribute("damper-pedal"); part.pedals.add(new RawPedal(m, tick, value.equals("yes") || decimal(value, 0) > 0)); score.features.add("踏板"); }
        if (sound.hasAttribute("segno")) bar.segno = sound.getAttribute("segno");
        if (sound.hasAttribute("coda")) bar.coda = sound.getAttribute("coda");
        if (sound.getAttribute("dacapo").equals("yes")) { bar.dc = true; bar.dcAtStart = tick <= 0; }
        if (sound.hasAttribute("dalsegno")) { bar.ds = sound.getAttribute("dalsegno").isEmpty() ? "default" : sound.getAttribute("dalsegno"); bar.dsAtStart = tick <= 0; }
        if (sound.hasAttribute("tocoda")) { bar.toCoda = sound.getAttribute("tocoda").isEmpty() ? "default" : sound.getAttribute("tocoda"); bar.codaAtStart = tick <= 0; }
        if (sound.hasAttribute("fine") && !sound.getAttribute("fine").equals("no")) { bar.fine = true; bar.fineAtStart = tick <= 0; }
        if (sound.getAttribute("forward-repeat").equals("yes")) bar.repeatAfterJump = true;
        if (sound.hasAttribute("dacapo") || sound.hasAttribute("fine") || sound.hasAttribute("dalsegno") || sound.hasAttribute("tocoda")) { bar.jumpPositions.add(tick); score.features.add("跳轉"); }
        if (sound.hasAttribute("time-only")) warn(score, "sound time-only 尚未套用，跳轉每個指令執行一次");
    }
    private static void tempo(Score score, Part part, int m, long tick, double value) {
        if (!Double.isFinite(value) || value < 10 || value > 400) { warn(score, "速度標記無有效數值（支援 10–400 BPM）"); return; }
        part.tempos.add(new RawTempo(m, tick, value)); score.features.add("速度");
    }
    private static void barline(Score score, List<PlaybackRoute.Bar> bars, int m, Element barline) throws IOException {
        PlaybackRoute.Bar bar = bars.get(m);
        for (Element repeat : children(barline, "repeat")) {
            String direction = repeat.getAttribute("direction");
            if (direction.equals("forward")) { if (barline.getAttribute("location").equals("right") && m + 1 < bars.size()) bars.get(m + 1).forward = true; else bar.forward = true; }
            else if (direction.equals("backward")) {
                PlaybackRoute.Bar target = barline.getAttribute("location").equals("left") && m > 0 ? bars.get(m - 1) : bar;
                target.backward = true; target.backwardAfterJump = repeat.getAttribute("after-jump").equals("yes");
                target.times = integer(repeat.getAttribute("times"), 2); if (target.times < 1 || target.times > 32) throw new IOException("反覆次數須為 1–32");
            }
            score.features.add("反覆");
        }
        for (Element ending : children(barline, "ending")) {
            if (ending.getAttribute("type").equals("start")) {
                PlaybackRoute.Bar target = !barline.getAttribute("location").equals("left") && m + 1 < bars.size() ? bars.get(m + 1) : bar;
                for (String token : ending.getAttribute("number").split("[,\\s]+")) {
                    String[] range = token.replace(".", "").split("-"); int low = integer(range[0], 0), high = range.length == 2 ? integer(range[1], 0) : low;
                    if (low < 1 || high > 32 || high < low) { warn(score, "無法解讀跳房子編號：" + ending.getAttribute("number")); continue; }
                    for (int pass = low; pass <= high; pass++) target.endingStart.add(pass);
                }
            } else if (ending.getAttribute("type").equals("stop") || ending.getAttribute("type").equals("discontinue")) {
                if (barline.getAttribute("location").equals("left") && m > 0) bars.get(m - 1).endingStop = true; else bar.endingStop = true;
            }
            score.features.add("跳房子");
        }
    }
    private static int velocity(Part part, RawNote note) {
        if (note.explicit != null) return clamp(note.explicit + note.accent);
        Dynamic latest = latest(part, note, note.tick, false); int value = latest == null ? 90 : latest.value; long mark = latest == null ? -1 : latest.tick;
        Wedge selected = null;
        for (Wedge wedge : part.wedges) if (wedge.end != null && wedge.end.tick > wedge.start.tick && wedge.start.tick <= note.tick && mark <= wedge.start.tick && wedge.matches(note) && (selected == null || wedge.start.tick >= selected.start.tick)) selected = wedge;
        if (selected != null) {
            Dynamic before = latest(part, note, selected.start.tick, false); int begin = before == null ? 90 : before.value;
            int target = clamp(begin + selected.sign * 24);
            for (Dynamic after : part.dynamics) if (!after.instant && after.tick >= selected.end.tick && after.matches(note)) { target = after.value; break; }
            double ratio = Math.min(1, (note.tick - selected.start.tick) / (double) (selected.end.tick - selected.start.tick)); value = (int) Math.round(begin + (target - begin) * ratio);
        }
        for (Dynamic instant : part.dynamics) if (instant.instant && instant.tick == note.tick && instant.matches(note)) value = instant.value;
        return clamp(value + note.accent);
    }
    private static Dynamic latest(Part part, RawNote note, long tick, boolean instant) {
        Dynamic selected = null; for (Dynamic mark : part.dynamics) { if (mark.tick > tick) break; if (mark.matches(note) && (!mark.instant || instant)) selected = mark; } return selected;
    }
    private static int dynamicValue(String name) {
        return switch (name) { case "pppp" -> 10; case "ppp" -> 16; case "pp" -> 28; case "p" -> 40; case "mp" -> 56; case "mf" -> 72; case "f" -> 90; case "ff" -> 110; case "fff" -> 124; case "ffff" -> 127; case "sf", "sfz", "fz", "rfz", "fp" -> 108; case "sffz" -> 124; default -> 0; };
    }
    private static int midiVelocity(double percent) { return clamp((int) Math.round(percent * .9)); }
    private static int clamp(int value) { return Math.max(1, Math.min(127, value)); }
    private static int beats(String value) { int result = 0; for (String token : value.split("\\+")) result += integer(token.trim(), 0); return result; }
    private static void locate(Point point, long[] starts) { point.tick = Math.max(0, starts[point.measure] + point.local); }
    private static double duration(Element element, int divisions) throws IOException {
        double value = decimal(text(element, "duration", "0"), 0); if (!Double.isFinite(value) || value < 0) throw new IOException("無效的音符 duration"); return value * Score.PPQ / divisions;
    }
    private static void warn(Score score, String message) { if (!score.warnings.contains(message)) score.warnings.add(message); }
    private static int integer(String text, int fallback) { try { return Integer.parseInt(text); } catch (Exception e) { return fallback; } }
    private static double decimal(String text, double fallback) { try { return Double.parseDouble(text); } catch (Exception e) { return fallback; } }
    private static Element child(Element parent, String tag) { if (parent != null) for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element && ((Element) n).getTagName().equals(tag)) return (Element) n; return null; }
    private static List<Element> children(Element parent, String tag) { List<Element> result = new ArrayList<>(); if (parent != null) for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element && (tag == null || ((Element) n).getTagName().equals(tag))) result.add((Element) n); return result; }
    private static String text(Element parent, String tag, String fallback) { Element element = child(parent, tag); return element == null ? fallback : element.getTextContent().trim(); }
}
