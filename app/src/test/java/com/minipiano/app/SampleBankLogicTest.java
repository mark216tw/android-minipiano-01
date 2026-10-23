package com.minipiano.app;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class SampleBankLogicTest {
    @Test public void threeLayersSelectAndCrossfadeByVelocity() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("instrument.json", manifest().getBytes(StandardCharsets.UTF_8));
        for (String name : new String[]{"note_060_soft.wav", "note_060_medium.wav", "note_060_loud.wav"}) {
            files.put(name, wav(60, 200, 2));
        }
        SampleBank.Source source = name -> {
            byte[] data = files.get(name);
            if (data == null) throw new java.io.FileNotFoundException(name);
            return new ByteArrayInputStream(data);
        };
        SampleBank bank = SampleBank.load(source, (count, total) -> {});
        assertEquals(3, bank.layers());
        assertEquals(1, bank.sampleCount);
        assertEquals(60, bank.sample(60).root);
        assertEquals(2, bank.gain(60, 0), 0);
        assertEquals(3, bank.gain(60, 1), 0);
        assertEquals(4, bank.gain(60, 2), 0);

        SampleBank.Selection soft = bank.select(60, 1);
        assertEquals(0, soft.first); assertEquals(-1, soft.second); assertEquals(2, soft.firstGain, 0);
        SampleBank.Selection middle = bank.select(60, 73);
        assertEquals(1, middle.first); assertEquals(-1, middle.second); assertEquals(3, middle.firstGain, 0);
        SampleBank.Selection loud = bank.select(60, 127);
        assertEquals(2, loud.first); assertEquals(-1, loud.second); assertEquals(4, loud.firstGain, 0);
        SampleBank.Selection boundary = bank.select(60, 50);
        assertEquals(0, boundary.first); assertEquals(1, boundary.second); assertTrue(boundary.mix > 0 && boundary.mix < 1);

        assertTrue(bank.calibrated());
    }

    private static String manifest() {
        return "{"
            + "\"version\":2,\"sampleRate\":32000,\"channels\":2,\"velocityLayers\":3,"
            + "\"layers\":[{\"name\":\"soft\",\"low\":1,\"high\":50},{\"name\":\"medium\",\"low\":51,\"high\":95},{\"name\":\"loud\",\"low\":96,\"high\":127}],"
            + "\"samples\":["
            + "{\"file\":\"note_060_soft.wav\",\"layer\":\"soft\",\"rootMidi\":60,\"lowMidi\":21,\"highMidi\":108,\"rate\":32000,\"channels\":2,\"frames\":200,\"gain\":2},"
            + "{\"file\":\"note_060_medium.wav\",\"layer\":\"medium\",\"rootMidi\":60,\"lowMidi\":21,\"highMidi\":108,\"rate\":32000,\"channels\":2,\"frames\":200,\"gain\":3},"
            + "{\"file\":\"note_060_loud.wav\",\"layer\":\"loud\",\"rootMidi\":60,\"lowMidi\":21,\"highMidi\":108,\"rate\":32000,\"channels\":2,\"frames\":200,\"gain\":4}]"
            + "}";
    }

    private static byte[] wav(int root, int frames, int channels) {
        int dataSize = frames * channels * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ascii(out, "RIFF"); le32(out, 36 + dataSize); ascii(out, "WAVE");
        ascii(out, "fmt "); le32(out, 16); le16(out, 1); le16(out, channels); le32(out, 32000);
        le32(out, 32000 * channels * 2); le16(out, channels * 2); le16(out, 16);
        ascii(out, "data"); le32(out, dataSize);
        for (int i = 0; i < frames; i++) for (int c = 0; c < channels; c++) le16(out, (int) (Math.sin(i * .1) * 1000));
        return out.toByteArray();
    }

    private static void ascii(ByteArrayOutputStream out, String text) { out.writeBytes(text.getBytes(StandardCharsets.US_ASCII)); }
    private static void le16(ByteArrayOutputStream out, int value) { out.write(value & 255); out.write((value >> 8) & 255); }
    private static void le32(ByteArrayOutputStream out, int value) { le16(out, value & 65535); le16(out, (value >> 16) & 65535); }
}
