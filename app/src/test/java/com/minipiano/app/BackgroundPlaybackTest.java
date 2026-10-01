package com.minipiano.app;

import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.Queue;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class BackgroundPlaybackTest {
    // Drive queued audio commands without a real sound device or wall-clock playback.
    @SuppressWarnings("unchecked") private void commands(PianoAudio audio) throws Exception {
        Field field = PianoAudio.class.getDeclaredField("commands"); field.setAccessible(true);
        Queue<Runnable> queue = (Queue<Runnable>) field.get(audio); Runnable command;
        while ((command = queue.poll()) != null) command.run();
    }
    @Test public void activityPauseAndRecreationRetainTrackPositionAndSpeed() throws Exception {
        PlaybackController playback = PlaybackController.acquire(RuntimeEnvironment.getApplication());
        try {
            playback.audio.close(); playback.load("track", Score.demo()); commands(playback.audio);
            playback.audio.seek(240); playback.audio.speed(1.25); playback.speedIndex = 3; commands(playback.audio);
            playback.audio.playing = true;
            try (var activity = Robolectric.buildActivity(MainActivity.class).setup()) {
                commands(playback.audio);
                activity.pause().stop(); commands(playback.audio);
                assertTrue(playback.audio.playing); assertEquals(240, playback.audio.position, 0);
            }
            try (var recreated = Robolectric.buildActivity(MainActivity.class).setup()) {
                commands(playback.audio);
                assertTrue(playback.audio.playing); assertEquals(240, playback.audio.position, 0);
                assertSame(playback, PlaybackController.peek()); assertEquals(3, playback.speedIndex);
            }
        } finally { playback.release(); }
    }
    @Test public void serviceControlsPlaybackAndDeletionStopsOnlyCurrentTrack() throws Exception {
        PlaybackController playback = PlaybackController.acquire(RuntimeEnvironment.getApplication());
        try {
            playback.audio.close(); playback.load("track", Score.demo()); commands(playback.audio);
            var service = Robolectric.buildService(PlaybackService.class).create();
            try {
                service.get().onStartCommand(new Intent().setAction(PlaybackService.PLAY), 0, 1); commands(playback.audio);
                assertTrue(playback.audio.playing);
                assertNotNull(Shadows.shadowOf(service.get()).getLastForegroundNotification());
                playback.rename("track", "新名稱"); assertEquals("新名稱", playback.score.title);
                playback.deleted("other"); commands(playback.audio); assertTrue(playback.audio.playing);
                service.get().onStartCommand(new Intent().setAction(PlaybackService.PAUSE), 0, 2); commands(playback.audio); assertFalse(playback.audio.playing);
                service.get().onStartCommand(new Intent().setAction(PlaybackService.PLAY), 0, 3); commands(playback.audio); assertTrue(playback.audio.playing);
                service.get().onStartCommand(new Intent().setAction(PlaybackService.STOP), 0, 4); commands(playback.audio); assertEquals(0, playback.audio.position, 0);
                playback.deleted("track"); commands(playback.audio); assertNull(playback.id); assertTrue(playback.score.notes.isEmpty());
            } finally { service.destroy(); }
        } finally { playback.serviceActive = false; playback.release(); }
    }
    @Test public void combinedButtonUsesIconsAndAccessibleState() throws Exception {
        PlaybackController playback = PlaybackController.acquire(RuntimeEnvironment.getApplication());
        try {
            playback.audio.close(); playback.load("track", Score.demo()); commands(playback.audio);
            try (var activity = Robolectric.buildActivity(MainActivity.class).setup()) {
                Field field = MainActivity.class.getDeclaredField("play"); field.setAccessible(true);
                ImageButton button = (ImageButton) field.get(activity.get()); assertEquals("播放", button.getContentDescription());
                playback.audio.playing = true;
                var method = MainActivity.class.getDeclaredMethod("updatePlaybackButtons"); method.setAccessible(true); method.invoke(activity.get());
                assertEquals("暫停", button.getContentDescription()); assertTrue(button.isEnabled());
            }
        } finally { playback.release(); }
    }
}
