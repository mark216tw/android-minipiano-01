package com.minipiano.app;

import org.json.JSONArray;
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
    private JSONObject manifest() throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("piano/instrument.json")) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void bundledBankCoversAll88KeysWithThreeLayers() throws Exception {
        JSONObject manifest = manifest();
        assertEquals(2, manifest.getInt("version"));
        assertEquals(3, manifest.getInt("velocityLayers"));
        JSONArray samples = manifest.getJSONArray("samples");
        assertEquals(90, samples.length());
        JSONObject layers = new JSONObject();
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i);
            String layer = entry.getString("layer");
            JSONArray covered = layers.optJSONArray(layer);
            if (covered == null) { covered = new JSONArray(); layers.put(layer, covered); }
            for (int pitch = entry.getInt("lowMidi"); pitch <= entry.getInt("highMidi"); pitch++) covered.put(pitch);
        }
        assertEquals(3, layers.length());
        var keys = layers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            var covered = layers.getJSONArray(key);
            assertEquals(88, covered.length());
            for (int pitch = 21; pitch <= 108; pitch++) assertTrue(key, contains(covered, pitch));
        }
        assertFalse(manifest.has("releaseSamples"));
        assertFalse(manifest.has("pedalSamples"));
        assertTrue(manifest.getBoolean("gainCalibrated"));
    }

    @Test public void bundledFilesMatchManifestHashes() throws Exception {
        JSONObject manifest = manifest();
        verify(manifest.getJSONArray("samples"));
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("piano/ATTRIBUTION.txt")) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("Alexander Holm")); assertTrue(text.contains("CC BY 3.0"));
        }
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("piano/LICENSE.txt")) { assertTrue(input.readAllBytes().length > 10000); }
    }

    private void verify(JSONArray entries) throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i); byte[] bytes;
            try (InputStream input = assets.open("piano/" + entry.getString("file"))) { bytes = input.readAllBytes(); }
            StringBuilder digest = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            assertEquals(entry.getString("sha256"), digest.toString());
        }
    }

    private static boolean contains(JSONArray array, int value) throws Exception {
        for (int i = 0; i < array.length(); i++) if (array.getInt(i) == value) return true;
        return false;
    }
}
