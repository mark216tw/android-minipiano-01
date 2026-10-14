package com.minipiano.app;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class SampleBankTest {
    @Test public void bundledBankCoversAll88KeysWithRealStereoPcmAndAttribution() throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets();
        final int[] progress = {0};
        SampleBank bank = SampleBank.load(name -> assets.open("piano/" + name), (count, total) -> { assertEquals(60, total); progress[0] = count; });
        assertEquals(60, progress[0]); assertTrue(bank.decodedBytes < 64L * 1024 * 1024);
        for (int pitch = 21; pitch <= 108; pitch++) {
            PianoSample sample = bank.sample(pitch); assertNotNull(sample); assertTrue(Math.abs(sample.root - pitch) <= 1);
            assertEquals(32000, sample.rate); assertEquals(2, sample.channels); assertTrue(sample.frames > 32000 * 3);
            assertEquals(0, sample.pcm[sample.pcm.length - 1]);
        }
        assertNull(bank.sample(20)); assertNull(bank.sample(109));
        try (InputStream input = assets.open("piano/ATTRIBUTION.txt")) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8); assertTrue(text.contains("Alexander Holm")); assertTrue(text.contains("CC BY 3.0"));
        }
        try (InputStream input = assets.open("piano/LICENSE.txt")) { assertTrue(input.readAllBytes().length > 10000); }
    }
    @Test public void bundledWavesMatchManifestHashesAndHaveAudibleAttack() throws Exception {
        verifyWaves("piano");
    }
    private void verifyWaves(String directory) throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets(); JSONObject manifest;
        try (InputStream input = assets.open(directory + "/instrument.json")) { manifest = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
        var samples = manifest.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i); byte[] bytes;
            try (InputStream input = assets.open(directory + "/" + entry.getString("file"))) { bytes = input.readAllBytes(); }
            StringBuilder digest = new StringBuilder(); for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            assertEquals(entry.getString("sha256"), digest.toString());
            PianoSample sample = PianoSample.read(new java.io.ByteArrayInputStream(bytes), entry.getInt("rootMidi"));
            int peak = 0;
            for (int j = 0; j < Math.min(sample.pcm.length, sample.rate / 5 * 2); j++) peak = Math.max(peak, Math.abs(sample.pcm[j]));
            assertTrue("silent attack: " + entry.getString("file"), peak > 328);
            for (short value : sample.pcm) assertTrue(Math.abs(value) <= 27890);
        }
    }
}
