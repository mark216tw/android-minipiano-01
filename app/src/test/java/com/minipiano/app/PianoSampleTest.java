package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class PianoSampleTest {
    private byte[] wav() {
        ByteBuffer b = ByteBuffer.allocate(52).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(44).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        b.putShort((short) 1).putShort((short) 2).putInt(24000).putInt(96000).putShort((short) 4).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(8).putShort((short) -32768).putShort((short) 16384).putShort((short) 0).putShort((short) 32767);
        return b.array();
    }
    @Test public void decoderPreservesSignedPcmAndChannelOrder() throws Exception {
        PianoSample sample = PianoSample.read(new ByteArrayInputStream(wav()), 60);
        assertEquals(24000, sample.rate); assertEquals(2, sample.channels); assertEquals(2, sample.frames);
        assertArrayEquals(new float[]{-1, .5f, 0, 32767 / 32768f}, sample.pcm, .000001f);
    }
    @Test public void truncatedOrUnsupportedWaveFailsBeforeVoicePlayback() {
        assertThrows(java.io.IOException.class, () -> PianoSample.read(new ByteArrayInputStream(new byte[2]), 60));
        byte[] invalid = wav(); invalid[20] = 3;
        assertThrows(java.io.IOException.class, () -> PianoSample.read(new ByteArrayInputStream(invalid), 60));
        assertThrows(java.io.IOException.class, () -> PianoSample.read(new ByteArrayInputStream(java.util.Arrays.copyOf(wav(), 49)), 60));
    }
}
