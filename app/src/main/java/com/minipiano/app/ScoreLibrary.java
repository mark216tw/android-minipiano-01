package com.minipiano.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ScoreLibrary {
    static final class Entry {
        String id, title, source;
        long created;
        double seconds;
    }
    private final Context context;
    private final File directory;
    private final SharedPreferences preferences;
    ScoreLibrary(Context context) {
        this.context = context;
        directory = new File(context.getFilesDir(), "scores");
        preferences = context.getSharedPreferences("scoreLibrary", Context.MODE_PRIVATE);
    }
    private File path(String id, String suffix) {
        if (!id.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("無效音譜 ID");
        return new File(directory, id + suffix);
    }
    private ScoreStorage storage(String id) { return new ScoreStorage(path(id, ".json")); }
    synchronized void migrate() throws Exception {
        if (preferences.getBoolean("migrated", false)) return;
        Score old = new ScoreStorage(context).load();
        if (old != null) {
            if (!path("legacy", ".meta").exists()) addWithId("legacy", old, "既有音譜");
            select("legacy");
        }
        if (!preferences.edit().putBoolean("migrated", true).commit()) throw new java.io.IOException("無法記錄移轉狀態");
    }
    synchronized String add(Score score, String source) throws Exception {
        String id = UUID.randomUUID().toString(); addWithId(id, score, source); select(id); return id;
    }
    private void addWithId(String id, Score score, String source) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("無法建立音譜庫");
        storage(id).save(score);
        JSONObject meta = new JSONObject().put("title", score.title).put("source", source)
            .put("created", System.currentTimeMillis()).put("updated", System.currentTimeMillis()).put("seconds", score.seconds(score.length()));
        writeMeta(id, meta);
    }
    private JSONObject metadata(String id) throws Exception {
        return new JSONObject(new String(new AtomicFile(path(id, ".meta")).readFully(), StandardCharsets.UTF_8));
    }
    private void writeMeta(String id, JSONObject meta) throws Exception {
        AtomicFile file = new AtomicFile(path(id, ".meta")); FileOutputStream output = file.startWrite();
        try { output.write(meta.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
        catch (Exception e) { file.failWrite(output); throw e; }
    }
    synchronized List<Entry> list() throws Exception {
        List<Entry> entries = new ArrayList<>(); File[] files = directory.listFiles();
        if (files == null) return entries;
        for (File file : files) {
            if (!file.getName().endsWith(".meta")) continue;
            String id = file.getName().replaceFirst("\\.meta$", "");
            try {
                JSONObject meta = metadata(id); Entry entry = new Entry(); entry.id = id;
                entry.title = meta.getString("title"); entry.source = meta.getString("source");
                entry.created = meta.getLong("created"); entry.seconds = meta.getDouble("seconds"); entries.add(entry);
            } catch (Exception e) { Entry entry = new Entry(); entry.id = id; entry.title = "無法讀取的音譜"; entry.source = "資料損壞"; entries.add(entry); }
        }
        entries.sort((a, b) -> Long.compare(b.created, a.created)); return entries;
    }
    synchronized Score load(String id) throws Exception {
        Score score = storage(id).load(); if (score == null) throw new java.io.IOException("找不到音譜");
        score.title = metadata(id).getString("title"); return score;
    }
    synchronized void update(String id, Score score) throws Exception {
        JSONObject meta = metadata(id); storage(id).save(score);
        meta.put("updated", System.currentTimeMillis()); writeMeta(id, meta);
    }
    synchronized void rename(String id, String title) throws Exception {
        title = title.trim(); if (title.isEmpty() || title.length() > 80) throw new IllegalArgumentException("名稱需為 1 至 80 字");
        JSONObject meta = metadata(id); meta.put("title", title).put("updated", System.currentTimeMillis()); writeMeta(id, meta);
    }
    synchronized void delete(String id) throws Exception {
        File score = path(id, ".json");
        if (score.exists() && !score.delete()) throw new java.io.IOException("無法刪除音譜");
        new AtomicFile(score).delete(); new AtomicFile(path(id, ".meta")).delete();
        if (path(id, ".meta").exists()) throw new java.io.IOException("無法刪除音譜資訊");
        if (id.equals(selected())) select(null);
    }
    String selected() { return preferences.getString("selected", null); }
    void select(String id) { preferences.edit().putString("selected", id).apply(); }
}
