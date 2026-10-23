package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LimiterTest {
    private static final int RATE = 48000, LOOKAHEAD = 72;

    private float[] render(Limiter limiter, float[] input) {
        float[] buffer = input.clone();
        limiter.process(buffer, buffer.length / 2);
        return buffer;
    }

    private float[] signal(int frames, double leftAmplitude, double rightAmplitude, double frequency) {
        float[] buffer = new float[frames * 2];
        for (int i = 0; i < frames; i++) {
            float value = (float) Math.sin(i * 2 * Math.PI * frequency / RATE);
            buffer[i * 2] = (float) (value * leftAmplitude);
            buffer[i * 2 + 1] = (float) (value * rightAmplitude);
        }
        return buffer;
    }

    @Test public void quietSignalPassesUnchangedAfterLookahead() {
        float[] input = signal(2000, .5, .5, 440);
        float[] output = render(new Limiter(RATE, .95, .0015, .12), input);
        for (int frame = LOOKAHEAD; frame < 2000; frame++) {
            assertEquals(input[(frame - LOOKAHEAD) * 2], output[frame * 2], 1e-6);
            assertEquals(input[(frame - LOOKAHEAD) * 2 + 1], output[frame * 2 + 1], 1e-6);
        }
    }

    @Test public void loudSignalIsBoundedAndStereoLinked() {
        float[] input = signal(4000, 1.5, .2, 220);
        float[] output = render(new Limiter(RATE, .95, .0015, .12), input);
        float peak = 0, rightPeak = 0;
        for (int i = 0; i < output.length; i += 2) {
            assertTrue(Float.isFinite(output[i]) && Float.isFinite(output[i + 1]));
            peak = Math.max(peak, Math.abs(output[i]));
            rightPeak = Math.max(rightPeak, Math.abs(output[i + 1]));
        }
        assertTrue(peak <= .9501f);
        assertTrue(rightPeak < .2f);
    }

    @Test public void gainRecoversAfterBurst() {
        Limiter limiter = new Limiter(RATE, .95, .0015, .12);
        float[] loud = signal(2000, 1.5, 1.5, 220);
        render(limiter, loud);
        float[] quiet = new float[48000 * 2];
        java.util.Arrays.fill(quiet, .2f);
        float[] output = render(limiter, quiet);
        float last = Math.abs(output[output.length - 2]);
        assertEquals(.2, last, .01);
    }

    @Test public void resetClearsHistory() {
        Limiter limiter = new Limiter(RATE, .95, .0015, .12);
        render(limiter, signal(4000, 1.5, 1.5, 100));
        limiter.reset();
        float[] input = signal(1000, .5, .5, 440);
        float[] output = render(limiter, input);
        for (int frame = LOOKAHEAD; frame < 1000; frame++) {
            assertEquals(input[(frame - LOOKAHEAD) * 2], output[frame * 2], 1e-6);
        }
    }
}
