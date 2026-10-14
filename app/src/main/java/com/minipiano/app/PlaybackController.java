package com.minipiano.app;

import android.content.Context;
import android.content.Intent;

/** Shared playback state survives activity transitions and recreation. Main-thread access. */
final class PlaybackController {
    private static PlaybackController instance;
    final Context context;
    final PianoAudio audio;
    Score score;
    String id;
    int speedIndex = 2;
    int clients;
    boolean serviceActive;
    private PlaybackController(Context context) { this.context = context.getApplicationContext(); audio = new PianoAudio(this.context); }
    static synchronized PlaybackController acquire(Context context) {
        if (instance == null) instance = new PlaybackController(context);
        instance.clients++; return instance;
    }
    static synchronized PlaybackController peek() { return instance; }
    void load(String id, Score score) { this.id = id; this.score = score; audio.load(score); }
    void play() {
        if (score == null || score.notes.isEmpty()) return;
        serviceActive = true;
        try { context.startForegroundService(new Intent(context, PlaybackService.class).setAction(PlaybackService.PLAY)); }
        catch (RuntimeException e) { serviceActive = false; audio.error = "無法啟動背景播放：" + e.getMessage(); }
    }
    void pause() { audio.pause(); }
    void stop() { audio.stop(); }
    void rename(String id, String title) { if (id.equals(this.id) && score != null) score.title = title; }
    void deleted(String id) {
        if (!id.equals(this.id)) return;
        stop(); this.id = null; score = new Score(); score.title = "尚未選取音譜"; audio.load(score);
    }
    synchronized void release() { clients = Math.max(0, clients - 1); closeIfUnused(); }
    void closeIfUnused() {
        synchronized (PlaybackController.class) {
            if (clients == 0 && !serviceActive) { audio.close(); if (instance == this) instance = null; }
        }
    }
}
