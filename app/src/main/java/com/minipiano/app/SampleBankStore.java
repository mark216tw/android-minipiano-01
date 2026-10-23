package com.minipiano.app;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One immutable bank at a time, with serialized/cancellable loading. No Activity references. */
final class SampleBankStore {
    private static final class State {
        final int sound; final SampleBank bank; final String status;
        State(int sound, SampleBank bank, String status) { this.sound = sound; this.bank = bank; this.status = status; }
    }
    private static volatile State state = new State(-1, null, "尚未載入");
    private static long generation;
    private static final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mini-piano-sample-loader"); thread.setDaemon(true); return thread;
    });
    private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "mini-piano-sample-watchdog"); thread.setDaemon(true); return thread;
    });
    private static final class Cancelled extends RuntimeException {}
    static SampleBank bank(int sound) { State current = state; return current.sound == sound ? current.bank : null; }
    static String status(int sound) { State current = state; return current.sound == sound ? current.status : "尚未載入"; }
    static void start(Context context) { start(context, PianoSound.selected(context)); }
    static synchronized void start(Context context, int sound) {
        if (sound == state.sound) return;
        final long request = ++generation;
        state = new State(sound, null, sound == PianoSound.SYNTHETIC ? "可立即使用" : "載入中");
        if (sound == PianoSound.SYNTHETIC) return;
        watchdog.schedule(() -> {
            synchronized (SampleBankStore.class) {
                if (generation == request && state.bank == null) {
                    state = new State(sound, null, "載入逾時，暫用合成");
                    generation++;
                }
            }
        }, 90, TimeUnit.SECONDS);
        AssetManager assets = context.getApplicationContext().getAssets();
        loader.execute(() -> {
            try {
                check(request);
                String directory = PianoSound.directory(sound) + "/";
                SampleBank loaded = SampleBank.load(name -> {
                    check(request); return assets.open(directory + name);
                }, (name, root) -> {
                    check(request);
                    if (name.endsWith(".flac")) return FlacSampleReader.read(assets, directory + name, root);
                    try (InputStream input = assets.open(directory + name)) { return PianoSample.read(input, root); }
                }, (count, total) -> publish(request, sound, null, "載入中 " + count + "/" + total));
                publish(request, sound, loaded, "已載入 · " + loaded.sampleCount + " 音高 · " + loaded.layers() + " 力度層");
                android.util.Log.i("MiniPiano", "Sample bank loaded: " + loaded.sampleCount + " roots, " + loaded.layers() + " layers, " + loaded.decodedBytes / 1024 / 1024 + " MiB");
            } catch (Cancelled ignored) {
                // A more recent selection owns the state; obsolete PCM is released.
            } catch (Exception | OutOfMemoryError e) {
                synchronized (SampleBankStore.class) {
                    if (generation == request) {
                        String detail = e.getMessage();
                        state = new State(sound, null, detail == null || detail.isEmpty() ? "載入失敗，暫用合成" : "載入失敗，暫用合成：" + detail);
                        generation++;
                    }
                }
                android.util.Log.e("MiniPiano", "Sample bank load failed", e);
            }
        });
    }
    private static synchronized void check(long request) { if (request != generation) throw new Cancelled(); }
    private static synchronized void publish(long request, int sound, SampleBank bank, String status) {
        check(request); state = new State(sound, bank, status);
    }
}
