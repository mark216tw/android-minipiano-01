package com.minipiano.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class ScoreStorage {
    private static final Object IO_LOCK = new Object();
    private final AtomicFile file;
    ScoreStorage(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "performance.json")); }
    ScoreStorage(File path) { file = new AtomicFile(path); }
    void save(Score score) throws Exception {
        synchronized (IO_LOCK) { saveLocked(score); }
    }
    private void saveLocked(Score score) throws Exception {
        JSONObject root = new JSONObject(); root.put("title", score.title); root.put("bpm", score.bpm);
        root.put("beats", score.beats); root.put("beatType", score.beatType);
        root.put("schemaVersion", 2); root.put("endTick", score.endTick); root.put("originalMeasureCount", score.originalMeasureCount);
        if (score.originalXml != null) root.put("originalXml", score.originalXml);
        JSONArray notes = new JSONArray(), pedals = new JSONArray();
        for (Score.Note n : score.notes) notes.put(new JSONArray().put(n.pitch).put(n.start).put(n.duration).put(n.velocity).put(n.part).put(n.voice).put(n.staff).put(n.staffMask));
        for (Score.Pedal p : score.pedals) pedals.put(new JSONArray().put(p.tick).put(p.down).put(p.part));
        root.put("notes", notes); root.put("pedals", pedals);
        JSONArray tempos = new JSONArray(), measures = new JSONArray();
        for (Score.Tempo tempo : score.tempos) tempos.put(new JSONArray().put(tempo.tick).put(tempo.bpm));
        for (Score.Measure measure : score.measures) measures.put(new JSONArray().put(measure.tick).put(measure.duration).put(measure.number).put(measure.visit));
        root.put("tempos", tempos); root.put("measures", measures); root.put("partNames", new JSONObject(score.partNames));
        root.put("mutedVoices", new JSONArray(score.mutedVoices)); root.put("features", new JSONArray(score.features)); root.put("warnings", new JSONArray(score.warnings));
        FileOutputStream output = file.startWrite();
        try { output.write(root.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
        catch (Exception e) { file.failWrite(output); throw e; }
    }
    Score load() throws Exception {
        synchronized (IO_LOCK) { return loadLocked(); }
    }
    private Score loadLocked() throws Exception {
        if (!file.getBaseFile().exists()) return null;
        JSONObject root = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
        Score score = new Score(); score.title = root.getString("title"); score.bpm = root.getInt("bpm");
        score.beats = root.getInt("beats"); score.beatType = root.getInt("beatType");
        JSONArray notes = root.getJSONArray("notes"), pedals = root.getJSONArray("pedals");
        for (int i = 0; i < notes.length(); i++) {
            JSONArray n = notes.getJSONArray(i);
            if (n.length() < 7) score.notes.add(new Score.Note(n.getInt(0), n.getLong(1), n.getLong(2), n.getInt(3)));
            else score.notes.add(new Score.Note(n.getInt(0), n.getLong(1), n.getLong(2), n.getInt(3), n.getString(4), n.getString(5), n.getInt(6), n.optInt(7, 1 << (n.getInt(6) - 1))));
        }
        for (int i = 0; i < pedals.length(); i++) { JSONArray p = pedals.getJSONArray(i); score.pedals.add(new Score.Pedal(p.getLong(0), p.getBoolean(1), p.optString(2, "P1"))); }
        score.endTick = root.optLong("endTick", 0); score.originalMeasureCount = root.optInt("originalMeasureCount", 0);
        score.originalXml = root.optString("originalXml", null);
        JSONArray tempos = root.optJSONArray("tempos"), measures = root.optJSONArray("measures");
        if (tempos != null) for (int i = 0; i < tempos.length(); i++) { JSONArray t = tempos.getJSONArray(i); score.tempos.add(new Score.Tempo(t.getLong(0), t.getDouble(1))); }
        if (measures != null) for (int i = 0; i < measures.length(); i++) { JSONArray m = measures.getJSONArray(i); score.measures.add(new Score.Measure(m.getLong(0), m.getLong(1), m.getString(2), m.getInt(3))); }
        JSONObject names = root.optJSONObject("partNames");
        if (names != null) for (java.util.Iterator<String> it = names.keys(); it.hasNext();) { String key = it.next(); score.partNames.put(key, names.getString(key)); }
        JSONArray muted = root.optJSONArray("mutedVoices"), features = root.optJSONArray("features"), warnings = root.optJSONArray("warnings");
        if (muted != null) for (int i = 0; i < muted.length(); i++) score.mutedVoices.add(muted.getString(i));
        if (features != null) for (int i = 0; i < features.length(); i++) score.features.add(features.getString(i));
        if (warnings != null) for (int i = 0; i < warnings.length(); i++) score.warnings.add(warnings.getString(i));
        score.sort(); return score;
    }
}
