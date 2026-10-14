package com.minipiano.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Immutable decoded bank: two velocity layers per key, shared across voices. */
final class SampleBank {
    interface Source { InputStream open(String name) throws IOException; }
    interface Progress { void loaded(int count, int total); }
    static final int SOFT = 0, LOUD = 1;
    private final PianoSample[][] pitches = new PianoSample[2][128];
    private final int layers;
    private final double crossfadeLow, crossfadeHigh;
    long decodedBytes;
    int sampleCount;
    private SampleBank(int layers, double crossfadeLow, double crossfadeHigh) {
        this.layers = layers; this.crossfadeLow = crossfadeLow; this.crossfadeHigh = crossfadeHigh;
    }
    PianoSample sample(int pitch, int layer) {
        return pitch >= 21 && pitch <= 108 && layer >= 0 && layer < layers ? pitches[layer][pitch] : null;
    }
    PianoSample sample(int pitch) { return sample(pitch, LOUD); }
    int layers() { return layers; }
    /** 0 selects the soft layer, 1 the loud layer; velocities between the crossfade points blend. */
    double blend(int velocity) {
        if (layers < 2) return 1;
        if (crossfadeHigh <= crossfadeLow) return velocity >= crossfadeHigh ? 1 : 0;
        return Math.max(0, Math.min(1, (velocity - crossfadeLow) / (crossfadeHigh - crossfadeLow)));
    }
    static SampleBank load(Source source, Progress progress) throws Exception {
        JSONObject manifest;
        try (InputStream input = source.open("instrument.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) { if (output.size() + count > 256 * 1024) throw new IOException("音色對照表過大"); output.write(buffer, 0, count); }
            manifest = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
        JSONArray samples = manifest.getJSONArray("samples");
        if (samples.length() < 1 || samples.length() > 176) throw new IOException("鋼琴音色包採樣數量無效");
        int layers = Math.max(1, Math.min(2, manifest.optInt("velocityLayers", 1)));
        double low = 64, high = 88;
        JSONArray crossfade = manifest.optJSONArray("velocityCrossfade");
        if (crossfade != null && crossfade.length() >= 2) { low = crossfade.getDouble(0); high = crossfade.getDouble(1); }
        SampleBank result = new SampleBank(layers, low, high);
        int sampleRate = manifest.getInt("sampleRate"), channels = manifest.getInt("channels");
        int[] roots = new int[layers];
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i); String name = entry.getString("file");
            if (!name.matches("note_[0-9]{3}(_soft|_loud)?\\.wav")) throw new IOException("音色檔名無效");
            int layer = layerIndex(entry.optString("layer", null), layers);
            PianoSample sample;
            try (InputStream input = source.open(name)) { sample = PianoSample.read(input, entry.getInt("rootMidi")); }
            if (sample.rate != sampleRate || sample.channels != channels || sample.frames != entry.getInt("frames")) throw new IOException("音色格式與對照表不符：" + name);
            result.decodedBytes += sample.pcm.length * 2L;
            if (result.decodedBytes > 64 * 1024 * 1024) throw new IOException("鋼琴音色記憶體超過上限");
            int sampleLow = entry.getInt("lowMidi"), sampleHigh = entry.getInt("highMidi");
            if (sampleLow < 21 || sampleHigh > 108 || sampleLow > sampleHigh || sample.root < sampleLow || sample.root > sampleHigh) throw new IOException("音色音域無效");
            for (int pitch = sampleLow; pitch <= sampleHigh; pitch++) { if (result.pitches[layer][pitch] != null) throw new IOException("音色音域重複"); result.pitches[layer][pitch] = sample; }
            roots[layer]++;
            progress.loaded(i + 1, samples.length());
        }
        for (int layer = 0; layer < layers; layer++) for (int pitch = 21; pitch <= 108; pitch++) if (result.pitches[layer][pitch] == null) throw new IOException("音色缺少 MIDI " + pitch);
        result.sampleCount = roots[0];
        return result;
    }
    private static int layerIndex(String layer, int layers) {
        if (layers < 2) return LOUD;
        return "soft".equals(layer) ? SOFT : LOUD;
    }
}
