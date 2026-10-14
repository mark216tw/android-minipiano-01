package com.minipiano.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable compiled events: note-off, ordered pedal changes, then note-on. */
final class PlaybackProgram {
    static final int NOTE_OFF = 0, PEDAL = 1, NOTE_ON = 2;
    static final class Event {
        final long tick; final double seconds; final int type, id, part; final boolean down;
        Event(long tick, TempoMap timeline, int type, int id, int part, boolean down) {
            this.tick = tick; seconds = timeline.seconds(tick); this.type = type; this.id = id; this.part = part; this.down = down;
        }
    }
    final Score score;
    final TempoMap timeline;
    final Event[] events;
    final List<Score.Channel> channels;
    final Map<String, Integer> parts = new LinkedHashMap<>();
    final int[] noteParts, noteChannels, pedalParts;
    final boolean[] initialMuted;
    final double duration;
    final long length;
    PlaybackProgram(Score source) {
        score = source.copy(); timeline = score.timeline(); channels = score.channels(); length = score.length(); duration = timeline.seconds(length);
        Map<String, Integer> channelIds = new LinkedHashMap<>(); initialMuted = new boolean[channels.size()];
        for (int i = 0; i < channels.size(); i++) { channelIds.put(channels.get(i).key, i); initialMuted[i] = score.mutedVoices.contains(channels.get(i).key); }
        for (Score.Note n : score.notes) parts.computeIfAbsent(n.part, key -> parts.size());
        for (Score.Pedal p : score.pedals) parts.computeIfAbsent(p.part, key -> parts.size());
        noteParts = new int[score.notes.size()]; noteChannels = new int[score.notes.size()]; pedalParts = new int[score.pedals.size()];
        ArrayList<Event> compiled = new ArrayList<>();
        for (int id = 0; id < score.notes.size(); id++) {
            Score.Note n = score.notes.get(id); noteParts[id] = parts.get(n.part); noteChannels[id] = channelIds.get(n.channelKey());
            compiled.add(new Event(n.start, timeline, NOTE_ON, id, noteParts[id], false)); compiled.add(new Event(n.end(), timeline, NOTE_OFF, id, noteParts[id], false));
        }
        for (int i = 0; i < score.pedals.size(); i++) { Score.Pedal p = score.pedals.get(i); pedalParts[i] = parts.get(p.part); compiled.add(new Event(p.tick, timeline, PEDAL, 0, pedalParts[i], p.down)); }
        compiled.sort(Comparator.comparingDouble((Event e) -> e.seconds).thenComparingInt(e -> e.type)); events = compiled.toArray(new Event[0]);
    }
}
