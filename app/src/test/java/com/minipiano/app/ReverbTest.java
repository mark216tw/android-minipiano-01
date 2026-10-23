package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReverbTest {
    private static final int RATE = 48000;

    @Test public void disabledReverbIsBitExact() {
        Reverb reverb = new Reverb(RATE);
        reverb.configure(false, .3);
        float[] buffer = new float[512];
        for (int i = 0; i < buffer.length; i++) buffer[i] = (float) Math.sin(i * .1);
        float[] copy = buffer.clone();
        reverb.process(buffer, buffer.length / 2);
        assertArrayEquals(copy, buffer, 0);
        assertFalse(reverb.active());
    }

    @Test public void enabledReverbProducesFiniteTailThatDecays() {
        Reverb reverb = new Reverb(RATE);
        reverb.configure(true, .4);
        float[] warm = new float[512];
        for (int i = 0; i < 4; i++) { java.util.Arrays.fill(warm, 0); reverb.process(warm, warm.length / 2); }
        float[] impulse = new float[512];
        impulse[0] = 1; impulse[1] = 1;
        reverb.process(impulse, impulse.length / 2);
        assertTrue(reverb.active());
        float[] silence = new float[512];
        long frames = 0;
        boolean heard = false;
        for (float value : impulse) if (Math.abs(value) > 1e-4) heard = true;
        while (reverb.active() && frames < RATE * 8L) {
            java.util.Arrays.fill(silence, 0);
            reverb.process(silence, silence.length / 2);
            for (float value : silence) {
                assertTrue(Float.isFinite(value) && Math.abs(value) < 8);
                if (Math.abs(value) > 1e-4) heard = true;
            }
            frames += silence.length / 2;
        }
        assertTrue(heard);
        assertFalse(reverb.active());
    }

    @Test public void clearStopsTail() {
        Reverb reverb = new Reverb(RATE);
        reverb.configure(true, .4);
        float[] impulse = new float[512]; impulse[0] = 1; impulse[1] = 1;
        reverb.process(impulse, impulse.length / 2);
        assertTrue(reverb.active());
        reverb.clear();
        assertFalse(reverb.active());
    }
}
