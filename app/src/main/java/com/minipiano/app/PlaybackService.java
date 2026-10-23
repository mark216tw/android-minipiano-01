package com.minipiano.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public final class PlaybackService extends Service {
    static final String PLAY = "com.minipiano.PLAY", PAUSE = "com.minipiano.PAUSE", STOP = "com.minipiano.STOP";
    private static final String CHANNEL = "playback";
    private static final float[] SPEEDS = {.5f, .75f, 1f, 1.25f, 1.5f};
    private static final long MAX_WAKE_LOCK_MS = 6L * 60 * 60 * 1000;
    private PlaybackController controller;
    private MediaSession session;
    private AudioManager manager;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wakeLock;
    private boolean hasFocus, requested;
    private Score lastScore;
    private String lastTitle;
    private long lastSecond = Long.MIN_VALUE;
    private boolean lastPlaying;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (controller == null) return;
            boolean playing = controller.audio.playing;
            if (playing && !wakeLock.isHeld()) wakeLock.acquire(wakeLockMs());
            if (!playing && !requested) {
                if (wakeLock.isHeld()) wakeLock.release();
                abandonFocus();
            }
            if (playing) requested = false;
            update();
            if (!playing && !requested && (!controller.uiVisible || controller.clients == 0)) { stopSelf(); return; }
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        controller = PlaybackController.peek();
        if (controller == null) { stopSelf(); return; }
        controller.serviceActive = true;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "音譜播放", NotificationManager.IMPORTANCE_LOW));
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mini-piano:playback");
        manager = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change -> {
                if (change == AudioManager.AUDIOFOCUS_GAIN) { controller.audio.duck(false); return; }
                if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { controller.audio.duck(true); return; }
                requested = false; controller.audio.duck(false); controller.pause(); abandonFocus();
            }, handler).build();
        session = new MediaSession(this, "MiniPiano");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { beginPlay(); }
            @Override public void onPause() { requested = false; controller.pause(); }
            @Override public void onStop() { requested = false; controller.stop(); }
            @Override public void onSeekTo(long position) { if (controller.score != null) controller.audio.seek(controller.score.tick(position / 1000.0)); }
        }); session.setActive(true);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (controller == null) { stopSelf(); return START_NOT_STICKY; }
        startForeground(1, notification());
        String action = intent == null ? null : intent.getAction();
        if (PLAY.equals(action)) beginPlay();
        else if (PAUSE.equals(action)) { requested = false; controller.pause(); }
        else if (STOP.equals(action)) { requested = false; controller.stop(); }
        handler.removeCallbacks(refresh); handler.post(refresh); return START_NOT_STICKY;
    }
    private void beginPlay() {
        if (!hasFocus) hasFocus = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasFocus) { controller.audio.error = "無法取得音訊焦點"; return; }
        controller.audio.duck(false);
        if (controller.score == null || controller.score.notes.isEmpty()) { abandonFocus(); return; }
        if (!wakeLock.isHeld()) wakeLock.acquire(wakeLockMs());
        requested = true; controller.audio.play();
        handler.postDelayed(() -> { requested = false; }, 1000);
    }
    private long wakeLockMs() {
        if (controller == null || controller.score == null) return 60_000;
        // Allow for the slowest playback speed with some headroom.
        long length = (long) (controller.score.seconds(controller.score.length()) * 2000);
        return Math.max(60_000, Math.min(MAX_WAKE_LOCK_MS, length + 120_000));
    }
    private void abandonFocus() { if (hasFocus) { manager.abandonAudioFocusRequest(focus); hasFocus = false; } }
    private PendingIntent action(String action, int request) {
        return PendingIntent.getService(this, request, new Intent(this, PlaybackService.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        boolean playing = controller.audio.playing;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_play)
            .setContentTitle(controller.score == null ? "mini 鋼琴" : controller.score.title)
            .setContentText(playing ? "播放中" : "已暫停／停止").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing)
            .addAction(new Notification.Action.Builder(playing ? R.drawable.ic_pause : R.drawable.ic_play, playing ? "暫停" : "播放", action(playing ? PAUSE : PLAY, 1)).build())
            .addAction(new Notification.Action.Builder(R.drawable.ic_stop, "停止", action(STOP, 2)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1)).build();
    }
    // MediaSession-associated media notifications are exempt from POST_NOTIFICATIONS.
    @android.annotation.SuppressLint("NotificationPermission")
    private void update() {
        Score score = controller.score;
        boolean playing = controller.audio.playing;
        String title = score == null ? "mini 鋼琴" : score.title;
        long position = score == null ? 0 : (long) (score.seconds((long) controller.audio.position) * 1000);
        long second = position / 1000;
        boolean scoreChanged = score != lastScore;
        boolean titleChanged = !title.equals(lastTitle);
        boolean playingChanged = playing != lastPlaying;
        if (scoreChanged || titleChanged) {
            lastScore = score;
            if (score != null) session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, score.title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (score.seconds(score.length()) * 1000)).build());
        }
        if (second != lastSecond || titleChanged || playingChanged) {
            lastSecond = second;
            session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, SPEEDS[controller.speedIndex]).build());
        }
        // Rebuilding the notification every second is unnecessary; only the title or play state is displayed.
        if (scoreChanged || titleChanged || playingChanged) {
            lastPlaying = playing; lastTitle = title;
            getSystemService(NotificationManager.class).notify(1, notification());
        }
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (controller != null) { controller.pause(); controller.serviceActive = false; controller.closeIfUnused(); }
        if (session != null) session.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (manager != null) abandonFocus();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
