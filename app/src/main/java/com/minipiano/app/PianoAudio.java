package com.minipiano.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;

/** Continuous stereo stream. All voice state belongs to the audio thread. */
final class PianoAudio implements AutoCloseable {
    private static final int VOICES = 72, MAX_ACTIVE = 64, TABLE_SIZE = 2048;
    private final float[] sine = new float[TABLE_SIZE];
    private final Voice[] voices = new Voice[VOICES];
    private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final boolean[] sustain = new boolean[2];
    private final double[] increments = new double[128];
    private final Thread thread;
    private final int rate, frames;
    private final Limiter limiter;
    private final Reverb reverb;
    private final Context context;
    private final AudioManager manager;
    private double duck = 1;
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { rebuild = true; }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { rebuild = true; }
    };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context sender, Intent intent) { silenceLive(); pause(); }
    };
    private volatile boolean running = true, rebuild;
    volatile boolean playing;
    volatile double position;
    volatile String error;
    private volatile long keysLow, keysHigh;
    private final AtomicLongArray keyAttackTimes = new AtomicLongArray(128);
    private PlaybackProgram program;
    private PlaybackClock clock;
    private boolean[] muted = new boolean[0], partPedals = new boolean[0];
    private long[] pedalStarts = new long[0];
    private Score score;
    private long scoreLength;
    private int nextEvent;
    private double cursor, speed = 1;
    private boolean metronome;
    private int metroBpm = 120, metroBeats = 4;
    private long metroFrame, renderedFrames;
    private int clickRemaining;
    private double clickPhase;
    private int sound = PianoSound.SALAMANDER;
    private volatile int selectedSound = PianoSound.SALAMANDER;
    private double volume = 1, targetVolume = 1;
    private static final class Voice {
        boolean active, held, sampled; int id, pitch, velocity, group, age, part, channel;
        long started;
        double phase, level, decay, hammer, release = 1, resumeFade = 1;
        final SampleVoice sampleVoice = new SampleVoice();
    }
    PianoAudio(Context context) {
        TimbreBalance.prepare();
        this.context = context.getApplicationContext();
        manager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        rate = 48000;
        frames = Math.max(64, Math.min(512, integer(manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER), 192)));
        limiter = new Limiter(rate, .95, .0015, .12);
        reverb = new Reverb(rate);
        for (int i = 0; i < TABLE_SIZE; i++) sine[i] = (float) Math.sin(i * 2 * Math.PI / TABLE_SIZE);
        for (int i = 0; i < VOICES; i++) voices[i] = new Voice();
        for (int i = 0; i < 128; i++) increments[i] = 440 * Math.pow(2, (i - 69) / 12.0) / rate;
        configure(this.context);
        manager.registerAudioDeviceCallback(deviceCallback, new Handler(Looper.getMainLooper()));
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) this.context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        else this.context.registerReceiver(noisy, filter);
        thread = new Thread(this::render, "mini-piano-audio"); thread.start();
    }
    void configure(Context context) {
        int selection = PianoSound.selected(context);
        double level = Math.max(0, Math.min(100, UiTheme.preferences(context).getInt("pianoVolume", 100))) / 100.0;
        boolean reverbEnabled = UiTheme.preferences(context).getBoolean("pianoReverb", false);
        double reverbLevel = Math.max(0, Math.min(60, UiTheme.preferences(context).getInt("pianoReverbMix", 18))) / 100.0;
        selectedSound = selection;
        SampleBankStore.start(context, selection);
        commands.add(() -> {
            if (sound != selection) { clear(0); clear(1); limiter.reset(); reverb.clear(); }
            sound = selection; targetVolume = level; reverb.configure(reverbEnabled, reverbLevel);
        });
    }
    String soundStatus() {
        int selected = selectedSound;
        if (selected == PianoSound.SYNTHETIC) return PianoSound.name(selected);
        String status = SampleBankStore.status(selected);
        return PianoSound.name(selected) + (SampleBankStore.bank(selected) != null ? "" : " · " + status + (status.startsWith("載入失敗") ? "" : " · 暫用合成"));
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }
    void on(int id, int pitch, int velocity) { commands.add(() -> noteOn(id, pitch, velocity, 0)); }
    void off(int id) { commands.add(() -> noteOff(id, 0)); }
    void pedal(boolean down) { commands.add(() -> setPedal(0, down)); }
    void silenceLive() { commands.add(() -> clear(0)); }
    void duck(boolean enabled) { commands.add(() -> duck = enabled ? .25 : 1); }
    void metronome(boolean enabled, int bpm, int beats) {
        commands.add(() -> { metronome = enabled; metroBpm = bpm; metroBeats = beats; metroFrame = renderedFrames; });
    }
    void load(Score value) {
        PlaybackProgram compiled = new PlaybackProgram(value); PlaybackClock newClock = new PlaybackClock(compiled, rate); newClock.speed(speed);
        boolean[] newMuted = compiled.initialMuted.clone(), pedals = new boolean[compiled.parts.size()]; long[] starts = new long[compiled.parts.size()];
        commands.add(() -> {
            playing = false; clear(1); limiter.reset(); reverb.clear(); program = compiled; clock = newClock; clock.speed(speed); score = compiled.score; scoreLength = score.length();
            muted = newMuted; partPedals = pedals; pedalStarts = starts; cursor = position = 0; nextEvent = 0;
        });
    }
    void speed(double value) { commands.add(() -> { speed = value; if (clock != null) clock.speed(value); }); }
    void mute(int channel, boolean enabled) {
        commands.add(() -> {
            if (channel < 0 || channel >= muted.length) return; muted[channel] = enabled;
            for (Voice v : voices) if (v.group == 1 && v.channel == channel) {
                if (enabled) { v.held = false; release(v, true); } else { v.active = false; v.sampleVoice.clear(); }
            }
            if (!enabled && playing) restoreNotes(channel);
        });
    }
    void play() { commands.add(() -> {
        if (score == null || score.notes.isEmpty()) return;
        if (clock.finished()) clock.seek(0);
        restore(); playing = true;
    }); }
    void pause() { commands.add(() -> { playing = false; clear(1); }); }
    void stop() { commands.add(() -> { playing = false; clear(1); limiter.reset(); reverb.clear(); if (clock != null) clock.seek(0); cursor = position = 0; nextEvent = 0; }); }
    void seek(long tick) { commands.add(() -> {
        cursor = score == null ? 0 : Math.max(0, Math.min(scoreLength, tick)); if (clock != null) clock.seek((long) cursor);
        clear(1); limiter.reset(); reverb.clear(); nextEvent = 0;
        if (playing) restore();
        position = cursor;
    }); }
    private void restore() {
        for (Voice v : voices) if (v.group == 1) { v.active = false; v.sampleVoice.clear(); }
        clearAttacks();
        cursor = clock.tick(); java.util.Arrays.fill(partPedals, false); java.util.Arrays.fill(pedalStarts, Long.MAX_VALUE);
        for (int i = 0; i < score.pedals.size(); i++) {
            Score.Pedal p = score.pedals.get(i); if (p.tick > cursor) break;
            int part = program.pedalParts[i]; if (p.down && !partPedals[part]) pedalStarts[part] = p.tick; partPedals[part] = p.down;
        }
        restoreNotes(-1);
        nextEvent = 0;
        while (nextEvent < program.events.length && program.events[nextEvent].seconds <= clock.seconds()) nextEvent++;
    }
    private void restoreNotes(int channel) {
        cursor = clock.tick();
        for (int id = 0; id < score.notes.size(); id++) {
            Score.Note n = score.notes.get(id);
            if (n.start > cursor) break;
            double seconds = clock.age(id);
            int noteChannel = program.noteChannels[id], part = program.noteParts[id];
            if (muted[noteChannel] || (channel >= 0 && noteChannel != channel)) continue;
            boolean held = n.end() > cursor;
            if (!held && !(partPedals[part] && n.end() > pedalStarts[part])) continue;
            if (seconds > 30) continue;
            noteOn(id, n.pitch, n.velocity, 1, seconds < 1.0 / rate);
            for (Voice v : voices) if (v.active && v.group == 1 && v.id == id && v.held) {
                if (v.sampled) { v.sampleVoice.seek(seconds); v.active = v.sampleVoice.active; }
                else {
                    v.level *= Math.exp(-seconds / (1.4 + (108 - v.pitch) * .025));
                    v.hammer *= Math.exp(-seconds * rate * .00035); v.age = (int) (seconds * rate);
                    v.phase = (increments[v.pitch] * rate * seconds) % 1;
                }
                v.resumeFade = seconds > 1.0 / rate ? 0 : 1;
                v.held = held;
            }
        }
    }
    private void dispatch(PlaybackProgram.Event e) {
        if (e.type == PlaybackProgram.NOTE_ON) {
            clock.onset(e.id); Score.Note note = score.notes.get(e.id);
            if (!muted[program.noteChannels[e.id]]) noteOn(e.id, note.pitch, note.velocity, 1);
        } else if (e.type == PlaybackProgram.NOTE_OFF) noteOff(e.id, 1);
        else {
            boolean changed = partPedals[e.part] != e.down;
            if (e.down && changed) pedalStarts[e.part] = e.tick;
            partPedals[e.part] = e.down;
            if (!e.down) for (Voice v : voices) if (v.active && v.group == 1 && v.part == e.part && !v.held) release(v, false);
        }
    }
    boolean lit(int pitch) {
        return pitch < 64 ? (keysLow & (1L << pitch)) != 0 : (keysHigh & (1L << (pitch - 64))) != 0;
    }
    long attackTime(int pitch) { return keyAttackTimes.get(pitch); }
    private void noteOn(int id, int pitch, int velocity, int group) {
        noteOn(id, pitch, velocity, group, true);
    }
    private void noteOn(int id, int pitch, int velocity, int group, boolean attack) {
        if (pitch < 21 || pitch > 108) return;
        velocity = Math.max(1, Math.min(127, velocity));
        if (group == 1 && attack) keyAttackTimes.set(pitch, SystemClock.uptimeMillis());
        noteOff(id, group);
        Voice chosen = allocate();
        chosen.active = chosen.held = true; chosen.id = id; chosen.pitch = pitch; chosen.velocity = velocity; chosen.group = group; chosen.started = renderedFrames;
        chosen.part = group == 1 ? program.noteParts[id] : 0; chosen.channel = group == 1 ? program.noteChannels[id] : -1;
        SampleBank bank = sound == PianoSound.SYNTHETIC ? null : SampleBankStore.bank(sound);
        SampleBank.Selection selection = bank == null ? null : bank.select(pitch, velocity);
        PianoSample first = selection == null ? null : bank.sample(pitch, selection.first);
        PianoSample second = selection == null || selection.second < 0 ? null : bank.sample(pitch, selection.second);
        chosen.sampled = first != null || second != null;
        if (chosen.sampled) chosen.sampleVoice.start(first, second, selection.mix, selection.firstGain, selection.secondGain, pitch, rate);
        else chosen.sampleVoice.clear();
        chosen.phase = 0; chosen.age = 0; chosen.release = 1;
        chosen.resumeFade = 1;
        chosen.level = chosen.sampled
            ? TimbreBalance.strength(velocity) * .5 * (bank.calibrated() ? 1 : TimbreBalance.gain(sound, pitch))
            : TimbreBalance.voiceLevel(sound, false, pitch, velocity);
        chosen.hammer = 1;
        chosen.decay = Math.exp(-1.0 / (rate * (1.4 + (108 - pitch) * .025)));
    }
    private Voice allocate() {
        Voice free = null;
        int active = 0;
        for (Voice v : voices) {
            if (!v.active) { if (free == null) free = v; }
            else active++;
        }
        if (active < MAX_ACTIVE && free != null) return free;
        Voice victim = oldest(false, true);
        if (victim == null) victim = oldest(false, false);
        if (victim == null) victim = oldest(true, false);
        if (victim != null) { victim.held = false; release(victim, .006); }
        if (free != null) return free;
        return victim == null ? voices[0] : victim;
    }
    private Voice oldest(boolean includeHeld, boolean requireUnpedaled) {
        Voice result = null;
        for (Voice v : voices) {
            if (!v.active) continue;
            if (!includeHeld && v.held) continue;
            if (requireUnpedaled && pedaled(v)) continue;
            if (result == null || v.started < result.started) result = v;
        }
        return result;
    }
    private boolean pedaled(Voice voice) {
        return voice.group == 0 ? sustain[0] : voice.part < partPedals.length && partPedals[voice.part];
    }
    private void noteOff(int id, int group) {
        for (Voice v : voices) if (v.active && v.id == id && v.group == group && v.held) {
            v.held = false;
            boolean damped = !(group == 0 ? sustain[0] : partPedals[v.part]);
            if (damped) release(v, false);
        }
    }
    private void setPedal(int group, boolean down) {
        sustain[group] = down;
        if (!down) for (Voice v : voices) if (v.active && v.group == group && !v.held) release(v, false);
    }
    private void release(Voice voice, boolean fast) {
        release(voice, fast ? .025 : Damper.releaseSeconds(voice.pitch));
    }
    private void release(Voice voice, double seconds) {
        if (voice.sampled) voice.sampleVoice.release(seconds);
        else voice.release = Math.exp(-1.0 / (rate * seconds));
    }
    private void clear(int group) {
        sustain[group] = false;
        for (Voice v : voices) if (v.active && v.group == group) {
            v.held = false; release(v, true);
        }
        if (group == 1) { java.util.Arrays.fill(partPedals, false); keysLow = keysHigh = 0; clearAttacks(); }
    }
    private void clearAttacks() { for (int pitch = 21; pitch <= 108; pitch++) keyAttackTimes.set(pitch, 0); }
    private float wave(double phase) { return sine[((int) (phase * TABLE_SIZE)) & (TABLE_SIZE - 1)]; }
    private double resumeGain(Voice voice) {
        double gain = Math.min(1, voice.resumeFade); if (gain < 1) voice.resumeFade += 1.0 / (rate * .003); return gain;
    }
    private boolean active() {
        if (playing || metronome || reverb.active()) return true;
        for (Voice v : voices) if (v.active) return true;
        return false;
    }
    private AudioTrack createTrack() {
        int minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        if (minimum <= 0) throw new IllegalStateException("裝置不支援音訊串流");
        AudioTrack track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(Math.max(minimum, frames * 8 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
        track.setBufferSizeInFrames(Math.max(frames * 2, minimum / 8));
        return track;
    }
    private void render() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        AudioTrack track = null;
        boolean trackPlaying = false;
        long idleFrames = 0, warmFrames = rate * 2L;
        int failures = 0;
        try {
            float[] buffer = new float[frames * 2], clickBuffer = new float[frames * 2];
            while (running) {
                Runnable command; while ((command = commands.poll()) != null) command.run();
                if (rebuild) {
                    rebuild = false;
                    if (track != null) { try { track.pause(); track.flush(); track.release(); } catch (Exception ignored) {} }
                    track = null; trackPlaying = false; idleFrames = 0; limiter.reset(); reverb.clear();
                }
                if (track == null) {
                    try { track = createTrack(); failures = 0; }
                    catch (Exception e) {
                        if (++failures > 3) throw e;
                        error = e.getMessage();
                        try { Thread.sleep(200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }
                }
                boolean sounding = active();
                if (!sounding && idleFrames >= warmFrames) {
                    if (trackPlaying) { try { track.pause(); track.flush(); } catch (IllegalStateException ignored) {} trackPlaying = false; }
                    Runnable next;
                    try { next = commands.poll(1, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    if (next != null) next.run();
                    continue;
                }
                if (!trackPlaying) { track.play(); trackPlaying = true; }
                for (int sample = 0; sample < frames; sample++) {
                    if (sounding && playing) {
                        while (nextEvent < program.events.length && program.events[nextEvent].seconds <= clock.seconds()) dispatch(program.events[nextEvent++]);
                        clock.advance(1);
                        if (clock.finished()) { cursor = scoreLength; playing = false; clear(1); }
                    }
                    if (!sounding) {
                        buffer[sample * 2] = 0; buffer[sample * 2 + 1] = 0;
                        clickBuffer[sample * 2] = 0; clickBuffer[sample * 2 + 1] = 0;
                        continue;
                    }
                    double left = 0, right = 0;
                    for (Voice v : voices) if (v.active) {
                        if (v.sampled) {
                            v.sampleVoice.next(); double fade = resumeGain(v);
                            left += v.sampleVoice.left * v.level * fade; right += v.sampleVoice.right * v.level * fade;
                            v.active = v.sampleVoice.active;
                            if (!v.active) v.sampleVoice.clear();
                            continue;
                        }
                        double tone = wave(v.phase) + .42 * v.hammer * wave(v.phase * 2) + .20 * v.hammer * wave(v.phase * 3) + .08 * v.hammer * wave(v.phase * 5);
                        double attack = Math.min(1, v.age++ / (rate * .002));
                        double toneLevel = tone * v.level * attack * resumeGain(v); left += toneLevel; right += toneLevel;
                        v.phase += increments[v.pitch]; if (v.phase >= 1) v.phase -= 1;
                        v.level *= v.decay * v.release; v.hammer *= .99965;
                        if (v.level < .00003) v.active = false;
                    }
                    volume += (targetVolume - volume) * .001;
                    double click = 0;
                    if (metronome && renderedFrames >= metroFrame) {
                        clickRemaining = rate / 40; clickPhase = ((renderedFrames * metroBpm / (rate * 60L)) % metroBeats == 0) ? 1800 : 1200;
                        metroFrame += Math.round(rate * 60.0 / metroBpm);
                    }
                    if (clickRemaining > 0) { click = .12 * wave(clickRemaining * clickPhase / rate) * clickRemaining / (rate / 40.0); clickRemaining--; }
                    renderedFrames++;
                    double master = volume * 1.5 * duck;
                    buffer[sample * 2] = (float) (left * master);
                    buffer[sample * 2 + 1] = (float) (right * master);
                    clickBuffer[sample * 2] = (float) click;
                    clickBuffer[sample * 2 + 1] = (float) click;
                }
                reverb.process(buffer, frames);
                for (int frame = 0; frame < frames; frame++) {
                    buffer[frame * 2] += clickBuffer[frame * 2];
                    buffer[frame * 2 + 1] += clickBuffer[frame * 2 + 1];
                }
                limiter.process(buffer, frames);
                long low = 0, high = 0;
                for (Voice v : voices) if (v.active && v.held && v.group == 1) {
                    if (v.pitch < 64) low |= 1L << v.pitch; else high |= 1L << (v.pitch - 64);
                }
                keysLow = low; keysHigh = high; if (clock != null) cursor = clock.tick(); position = cursor;
                try {
                    int written = 0;
                    while (running && written < buffer.length) {
                        int count = track.write(buffer, written, buffer.length - written, AudioTrack.WRITE_BLOCKING);
                        if (count < 0) throw new IllegalStateException("音訊輸出錯誤：" + count);
                        if (count == 0) throw new IllegalStateException("音訊輸出停止");
                        written += count;
                    }
                } catch (Exception e) {
                    if (++failures > 3) throw e;
                    error = e.getMessage();
                    try { track.pause(); track.flush(); track.release(); } catch (Exception ignored) {}
                    track = null; trackPlaying = false; idleFrames = 0;
                    try { Thread.sleep(100); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                    continue;
                }
                idleFrames = sounding ? 0 : idleFrames + frames;
            }
        } catch (Exception e) { error = e.getMessage(); playing = false; }
        finally { if (track != null) { try { track.stop(); } catch (Exception ignored) {} track.release(); } }
    }
    @Override public void close() {
        running = false;
        try { manager.unregisterAudioDeviceCallback(deviceCallback); } catch (Exception ignored) {}
        try { context.unregisterReceiver(noisy); } catch (Exception ignored) {}
        commands.add(() -> { });
        try { thread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
