package com.minipiano.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Expands repeats/voltas and explicit D.C./D.S./Fine/Coda into finite measure visits. */
final class PlaybackRoute {
    static final class Bar {
        String number;
        long length;
        boolean forward, backward, endingStop, dc, fine, repeatAfterJump, backwardAfterJump;
        boolean fineAtStart, codaAtStart, dcAtStart, dsAtStart;
        int times = 2;
        String segno = "", coda = "", ds = "", toCoda = "";
        final Set<Integer> endingStart = new LinkedHashSet<>(), endings = new LinkedHashSet<>();
        final List<Long> jumpPositions = new ArrayList<>();
        Bar(String number) { this.number = number; }
    }
    private static final class Repeat {
        final int start, end, times; int pass = 1;
        Repeat(int start, int end, int times) { this.start = start; this.end = end; this.times = times; }
    }
    static List<Integer> expand(List<Bar> bars, Consumer<String> warning) throws IOException {
        List<Repeat> repeats = new ArrayList<>(); ArrayDeque<Integer> starts = new ArrayDeque<>();
        Map<Integer, Repeat> backs = new HashMap<>();
        Set<Integer> activeEnding = new LinkedHashSet<>();
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            if (bar.forward) starts.push(i);
            if (!bar.endingStart.isEmpty()) activeEnding = new LinkedHashSet<>(bar.endingStart);
            bar.endings.addAll(activeEnding);
            if (bar.endingStop) activeEnding.clear();
            if (bar.backward) {
                int start = starts.isEmpty() ? 0 : starts.pop();
                Repeat repeat = new Repeat(start, i, bar.times); repeats.add(repeat); backs.put(i, repeat);
            }
        }
        if (!starts.isEmpty()) warning.accept("前反覆沒有對應後反覆，按書寫順序處理該標記");
        Map<Integer, Repeat> endings = new HashMap<>(); Repeat previousEnding = null;
        for (int i = 0; i < bars.size(); i++) {
            if (bars.get(i).endings.isEmpty()) { previousEnding = null; continue; }
            Repeat selected = null;
            for (Repeat repeat : repeats) if (i >= repeat.start && i <= repeat.end && (selected == null || repeat.start > selected.start || (repeat.start == selected.start && repeat.end < selected.end))) selected = repeat;
            if (selected == null) selected = previousEnding;
            if (selected == null) for (Repeat repeat : repeats) if (repeat.end == i - 1) selected = repeat;
            if (selected != null) { endings.put(i, selected); previousEnding = selected; }
            else warning.accept("第 " + bars.get(i).number + " 小節跳房子未找到對應反覆，按書寫順序播放");
        }
        Map<String, Integer> segnos = new HashMap<>(), codas = new HashMap<>();
        for (int i = 0; i < bars.size(); i++) {
            if (!bars.get(i).segno.isEmpty()) marker(segnos, bars.get(i).segno, i, "Segno", warning);
            if (!bars.get(i).coda.isEmpty()) marker(codas, bars.get(i).coda, i, "Coda", warning);
        }
        List<Integer> result = new ArrayList<>(); Set<Integer> jumps = new LinkedHashSet<>(), codaJumps = new LinkedHashSet<>();
        int i = 0, steps = 0; boolean afterJump = false, repeatsEnabled = true;
        while (i < bars.size()) {
            if (++steps > 10000 || result.size() >= 10000) throw new IOException("反覆／跳轉超過 10,000 小節，請檢查樂譜演奏順序");
            Bar bar = bars.get(i); Repeat ending = endings.get(i);
            boolean play = bar.endings.isEmpty() || ending == null || bar.endings.contains(ending.pass);
            if (play && afterJump && bar.fine && bar.fineAtStart) break;
            if (play && afterJump && bar.codaAtStart && !bar.toCoda.isEmpty() && codaJumps.add(i)) {
                Integer target = target(codas, bar.toCoda);
                if (target != null) { i = target; continue; }
                warning.accept("找不到 Coda 目標：" + bar.toCoda);
            }
            if (play && ((bar.dc && bar.dcAtStart) || (!bar.ds.isEmpty() && bar.dsAtStart)) && jumps.add(i)) {
                Integer target = bar.dc ? Integer.valueOf(0) : target(segnos, bar.ds);
                if (target != null) {
                    afterJump = true; repeatsEnabled = bar.repeatAfterJump;
                    for (Repeat repeat : repeats) repeat.pass = repeatsEnabled || bars.get(repeat.end).backwardAfterJump ? 1 : repeat.times;
                    i = target; continue;
                }
                warning.accept("找不到 Segno 目標：" + bar.ds);
            }
            if (play) result.add(i);
            if (play && afterJump && bar.fine) break;
            if (play && afterJump && !bar.toCoda.isEmpty() && codaJumps.add(i)) {
                Integer target = target(codas, bar.toCoda);
                if (target != null) { i = target; continue; }
                warning.accept("找不到 Coda 目標：" + bar.toCoda);
            }
            if (play && (bar.dc || !bar.ds.isEmpty()) && jumps.add(i)) {
                Integer target = bar.dc ? Integer.valueOf(0) : target(segnos, bar.ds);
                if (target != null) {
                    afterJump = true; repeatsEnabled = bar.repeatAfterJump;
                    for (Repeat repeat : repeats) repeat.pass = repeatsEnabled || bars.get(repeat.end).backwardAfterJump ? 1 : repeat.times;
                    i = target; continue;
                }
                warning.accept("找不到 Segno 目標：" + bar.ds);
            }
            Repeat back = backs.get(i);
            if (back != null && (repeatsEnabled || (afterJump && bar.backwardAfterJump)) && back.pass < back.times) {
                back.pass++;
                for (Repeat inner : repeats) if (inner != back && inner.start >= back.start && inner.end <= back.end) inner.pass = 1;
                i = back.start;
            } else i++;
        }
        return result;
    }
    private static void marker(Map<String, Integer> markers, String id, int index, String name, Consumer<String> warning) {
        Integer previous = markers.putIfAbsent(id, index);
        if (previous != null && previous != index) { markers.put(id, -1); warning.accept(name + " 標記重複且位置不同：" + id); }
    }
    private static Integer target(Map<String, Integer> markers, String id) {
        Integer target = markers.get(id);
        if (target == null && markers.size() == 1 && id.equals("default")) return markers.values().iterator().next();
        return target != null && target >= 0 ? target : null;
    }
}
