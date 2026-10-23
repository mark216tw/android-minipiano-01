package com.minipiano.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Immutable decoded bank with velocity layers. */
final class SampleBank {
    interface Source { InputStream open(String name) throws IOException; }
    interface Decoder { PianoSample open(String name, int root) throws Exception; }
    interface Progress { void loaded(int count, int total); }
    static final int SOFT = 0, LOUD = 1;
    private static final long MEMORY_LIMIT = 64L * 1024 * 1024;
    private static final int VELOCITY_CROSSFADE = 6;
    private final PianoSample[][] pitches;
    private final double[][] gains;
    private final int layers, sampleRate, channels;
    private final int[] velocityLow, velocityHigh;
    private final double legacyLow, legacyHigh;
    private final boolean legacy;
    long decodedBytes;
    int sampleCount;
    private boolean calibrated;

    static final class Selection {
        final int first, second;
        final double mix, firstGain, secondGain;
        Selection(int first, int second, double mix, double firstGain, double secondGain) {
            this.first = first; this.second = second; this.mix = mix; this.firstGain = firstGain; this.secondGain = secondGain;
        }
    }

    private SampleBank(int layers, boolean legacy, int sampleRate, int channels, double legacyLow, double legacyHigh,
                       int[] velocityLow, int[] velocityHigh) {
        this.layers = layers; this.legacy = legacy; this.sampleRate = sampleRate; this.channels = channels;
        this.legacyLow = legacyLow; this.legacyHigh = legacyHigh;
        this.velocityLow = velocityLow; this.velocityHigh = velocityHigh;
        pitches = new PianoSample[layers][128];
        gains = new double[layers][128];
        for (double[] row : gains) java.util.Arrays.fill(row, 1);
    }

    PianoSample sample(int pitch, int layer) {
        return pitch >= 21 && pitch <= 108 && layer >= 0 && layer < layers ? pitches[layer][pitch] : null;
    }
    PianoSample sample(int pitch) { return sample(pitch, layers - 1); }
    int layers() { return layers; }
    boolean calibrated() { return calibrated; }
    int sampleRate() { return sampleRate; }
    int channels() { return channels; }
    double gain(int pitch, int layer) {
        return pitch >= 21 && pitch <= 108 && layer >= 0 && layer < layers ? gains[layer][pitch] : 1;
    }

    /** 0 selects the first layer, 1 the last; velocities between centers blend. */
    double blend(int velocity) {
        if (layers < 2) return 1;
        if (legacy) {
            if (legacyHigh <= legacyLow) return velocity >= legacyHigh ? 1 : 0;
            return Math.max(0, Math.min(1, (velocity - legacyLow) / (legacyHigh - legacyLow)));
        }
        Selection selection = select(60, velocity);
        return selection.second < 0 ? 0 : selection.mix;
    }

    Selection select(int pitch, int velocity) {
        int value = Math.max(1, Math.min(127, velocity));
        if (layers == 1) return new Selection(0, -1, 0, gain(pitch, 0), gain(pitch, 0));
        if (legacy) {
            double mix = legacyHigh <= legacyLow ? (value >= legacyHigh ? 1 : 0) : Math.max(0, Math.min(1, (value - legacyLow) / (legacyHigh - legacyLow)));
            return new Selection(SOFT, LOUD, mix, gain(pitch, SOFT), gain(pitch, LOUD));
        }
        int layer = 0;
        while (layer < layers - 1 && value > (velocityHigh[layer] + velocityLow[layer + 1]) / 2.0) layer++;
        int second = -1;
        double mix = 0;
        if (layer + 1 < layers) {
            double boundary = (velocityHigh[layer] + velocityLow[layer + 1]) / 2.0;
            if (value > boundary - VELOCITY_CROSSFADE) {
                second = layer + 1;
                mix = (value - boundary + VELOCITY_CROSSFADE) / (2.0 * VELOCITY_CROSSFADE);
            }
        }
        if (second < 0 && layer > 0) {
            double boundary = (velocityHigh[layer - 1] + velocityLow[layer]) / 2.0;
            if (value < boundary + VELOCITY_CROSSFADE) {
                second = layer;
                layer--;
                mix = (value - boundary + VELOCITY_CROSSFADE) / (2.0 * VELOCITY_CROSSFADE);
            }
        }
        if (second < 0) return new Selection(layer, -1, 0, gain(pitch, layer), gain(pitch, layer));
        return new Selection(layer, second, Math.max(0, Math.min(1, mix)), gain(pitch, layer), gain(pitch, second));
    }

    static SampleBank load(Source source, Progress progress) throws Exception {
        return load(source, (name, root) -> PianoSample.read(source.open(name), root), progress);
    }

    static SampleBank load(Source source, Decoder decoder, Progress progress) throws Exception {
        JSONObject manifest = readManifest(source);
        JSONArray samples = manifest.getJSONArray("samples");
        int layerCount = manifest.optInt("velocityLayers", 1);
        JSONArray layerDefinitions = manifest.optJSONArray("layers");
        boolean legacy = layerDefinitions == null;
        if (layerDefinitions != null) layerCount = layerDefinitions.length();
        if (layerCount < 1 || layerCount > 4) throw new IOException("鋼琴音色力度層數量無效");
        int sampleRate = manifest.optInt("sampleRate", 0), channels = manifest.optInt("channels", 0);
        double crossfadeLow = 64, crossfadeHigh = 88;
        JSONArray crossfade = manifest.optJSONArray("velocityCrossfade");
        if (crossfade != null && crossfade.length() >= 2) { crossfadeLow = crossfade.getDouble(0); crossfadeHigh = crossfade.getDouble(1); }
        String[] names = new String[layerCount];
        int[] low = new int[layerCount], high = new int[layerCount];
        if (legacy) {
            for (int i = 0; i < layerCount; i++) {
                names[i] = layerCount == 1 ? "loud" : i == 0 ? "soft" : "loud";
                low[i] = i == 0 ? 0 : (int) crossfadeLow;
                high[i] = i == layerCount - 1 ? 127 : (int) crossfadeHigh;
            }
        } else {
            for (int i = 0; i < layerCount; i++) {
                JSONObject definition = layerDefinitions.getJSONObject(i);
                names[i] = definition.getString("name");
                low[i] = Math.max(1, Math.min(127, definition.optInt("low", 1)));
                high[i] = Math.max(low[i], Math.min(127, definition.optInt("high", 127)));
            }
        }
        SampleBank result = new SampleBank(layerCount, legacy, sampleRate, channels, crossfadeLow, crossfadeHigh, low, high);
        result.calibrated = !legacy;
        if (legacy && layerCount > 1) for (int pitch = 0; pitch < 128; pitch++) result.gains[0][pitch] = 1.78;
        if (samples.length() < 1 || samples.length() > 512) throw new IOException("鋼琴音色包採樣數量無效");
        int[] roots = new int[layerCount];
        int total = samples.length();
        int[] entryLayer = new int[total], entryRoot = new int[total];
        String[] entryName = new String[total];
        for (int i = 0; i < total; i++) {
            JSONObject entry = samples.getJSONObject(i); String name = entry.getString("file");
            if (!name.matches("note_[0-9]{3}(_[a-z]+)?\\.(wav|flac)")) throw new IOException("音色檔名無效");
            entryName[i] = name; entryRoot[i] = entry.getInt("rootMidi");
            entryLayer[i] = layerIndex(entry.optString("layer", names[layerCount - 1]), names, legacy, layerCount);
        }
        PianoSample[] decoded = new PianoSample[total];
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors())), task -> {
            Thread thread = new Thread(task, "mini-piano-decoder"); thread.setDaemon(true); return thread;
        });
        try {
            List<Future<PianoSample>> futures = new ArrayList<>();
            AtomicInteger done = new AtomicInteger();
            for (int i = 0; i < total; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    try { return decoder.open(entryName[index], entryRoot[index]); }
                    finally { progress.loaded(done.incrementAndGet(), total); }
                }));
            }
            for (int i = 0; i < total; i++) {
                try { decoded[i] = futures.get(i).get(); }
                catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    if (cause instanceof Error) throw (Error) cause;
                    throw (Exception) cause;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        for (int i = 0; i < total; i++) {
            JSONObject entry = samples.getJSONObject(i); String name = entryName[i];
            PianoSample sample = decoded[i];
            int expectedRate = entry.optInt("rate", sampleRate), expectedChannels = entry.optInt("channels", channels);
            if (expectedRate > 0 && sample.rate != expectedRate) throw new IOException("音色取樣率與對照表不符：" + name);
            if (expectedChannels > 0 && sample.channels != expectedChannels) throw new IOException("音色聲道與對照表不符：" + name);
            if (entry.has("frames") && sample.frames != entry.getInt("frames")) throw new IOException("音色長度與對照表不符：" + name);
            result.decodedBytes += sample.pcm.length * 2L;
            if (result.decodedBytes > MEMORY_LIMIT) throw new IOException("鋼琴音色記憶體超過上限");
            int sampleLow = entry.getInt("lowMidi"), sampleHigh = entry.getInt("highMidi");
            if (sampleLow < 21 || sampleHigh > 108 || sampleLow > sampleHigh || entryRoot[i] < sampleLow || entryRoot[i] > sampleHigh) throw new IOException("音色音域無效");
            double gain = entry.optDouble("gain", 1);
            if (!Double.isFinite(gain) || gain <= 0) throw new IOException("音色增益無效");
            for (int pitch = sampleLow; pitch <= sampleHigh; pitch++) {
                if (result.pitches[entryLayer[i]][pitch] != null) throw new IOException("音色音域重複");
                result.pitches[entryLayer[i]][pitch] = sample;
                result.gains[entryLayer[i]][pitch] = gain;
            }
            roots[entryLayer[i]]++;
        }
        for (int layer = 0; layer < layerCount; layer++) for (int pitch = 21; pitch <= 108; pitch++) {
            if (result.pitches[layer][pitch] == null) throw new IOException("音色缺少 MIDI " + pitch);
        }
        result.sampleCount = roots[layerCount - 1];
        return result;
    }

    private static int layerIndex(String name, String[] names, boolean legacy, int layers) {
        if (legacy) return layers == 1 ? 0 : "soft".equals(name) ? 0 : 1;
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
        return layers - 1;
    }

    private static JSONObject readManifest(Source source) throws Exception {
        try (InputStream input = source.open("instrument.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 256 * 1024) throw new IOException("音色對照表過大");
                output.write(buffer, 0, count);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
