package com.minipiano.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class SampleBank {
    interface Source { InputStream open(String name) throws IOException; }
    interface Progress { void loaded(int count, int total); }
    private final PianoSample[] pitches = new PianoSample[128];
    long decodedBytes;
    int sampleCount;
    PianoSample sample(int pitch) { return pitch >= 21 && pitch <= 108 ? pitches[pitch] : null; }
    static SampleBank load(Source source, Progress progress) throws Exception {
        JSONObject manifest;
        try (InputStream input = source.open("instrument.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) { if (output.size() + count > 256 * 1024) throw new IOException("音色對照表過大"); output.write(buffer, 0, count); }
            manifest = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
        JSONArray samples = manifest.getJSONArray("samples");
        if (samples.length() < 1 || samples.length() > 88) throw new IOException("鋼琴音色包採樣數量無效");
        SampleBank result = new SampleBank();
        result.sampleCount = samples.length();
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i); String name = entry.getString("file");
            if (!name.matches("note_[0-9]{3}\\.wav")) throw new IOException("音色檔名無效");
            PianoSample sample;
            try (InputStream input = source.open(name)) { sample = PianoSample.read(input, entry.getInt("rootMidi")); }
            if (sample.rate != manifest.getInt("sampleRate") || sample.channels != manifest.getInt("channels") || sample.frames != entry.getInt("frames")) throw new IOException("音色格式與對照表不符：" + name);
            result.decodedBytes += sample.pcm.length * 4L;
            if (result.decodedBytes > 64 * 1024 * 1024) throw new IOException("鋼琴音色記憶體超過上限");
            int low = entry.getInt("lowMidi"), high = entry.getInt("highMidi");
            if (low < 21 || high > 108 || low > high || sample.root < low || sample.root > high) throw new IOException("音色音域無效");
            for (int pitch = low; pitch <= high; pitch++) { if (result.pitches[pitch] != null) throw new IOException("音色音域重複"); result.pitches[pitch] = sample; }
            progress.loaded(i + 1, samples.length());
        }
        for (int pitch = 21; pitch <= 108; pitch++) if (result.pitches[pitch] == null) throw new IOException("音色缺少 MIDI " + pitch);
        return result;
    }
}
