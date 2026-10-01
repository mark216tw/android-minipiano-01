package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimbreBalanceTest {
    @Test public void referenceStaysUnchangedAndContoursDoNotJumpAtBandEdges() {
        for (int pitch = 21; pitch <= 108; pitch++) {
            assertEquals(1, TimbreBalance.gain(PianoSound.SALAMANDER, pitch), 0);
            for (int sound = 0; sound < 3; sound++) {
                double gain = TimbreBalance.gain(sound, pitch); assertTrue(Double.isFinite(gain) && gain > 0 && gain < 8);
                if (pitch > 21) assertTrue(Math.abs(20 * Math.log10(gain / TimbreBalance.gain(sound, pitch - 1))) < 1);
            }
        }
        assertTrue(TimbreBalance.gain(PianoSound.UPRIGHT, 60) > 2);
        assertTrue(TimbreBalance.gain(PianoSound.SYNTHETIC, 24) > 1);
        assertTrue(TimbreBalance.gain(PianoSound.SYNTHETIC, 96) < .5);
    }
    @Test public void fallbackSyntheticHasSameLevelRegardlessOfSelectedBank() {
        for (int pitch = 21; pitch <= 108; pitch++) {
            double synthetic = TimbreBalance.voiceLevel(PianoSound.SYNTHETIC, false, pitch, 90);
            assertEquals(synthetic, TimbreBalance.voiceLevel(PianoSound.SALAMANDER, false, pitch, 90), 0);
            assertEquals(synthetic, TimbreBalance.voiceLevel(PianoSound.UPRIGHT, false, pitch, 90), 0);
            assertEquals(0, TimbreBalance.voiceLevel(PianoSound.UPRIGHT, true, pitch, 0), 0);
            assertTrue(TimbreBalance.voiceLevel(PianoSound.UPRIGHT, true, pitch, 127) > TimbreBalance.voiceLevel(PianoSound.UPRIGHT, true, pitch, 64));
        }
    }
    @Test public void mutedPianoDoesNotMuteMetronomeAndOutputStaysBounded() {
        float clickOnly = TimbreBalance.output(0, 1, .12);
        assertEquals(clickOnly, TimbreBalance.output(10, 0, .12), 0);
        assertEquals(0, TimbreBalance.output(1, 0, 0), 0);
        assertTrue(Math.abs(TimbreBalance.output(.2, .5, 0)) < Math.abs(TimbreBalance.output(.2, 1, 0)));
        for (int i = -640; i <= 640; i++) {
            float output = TimbreBalance.output(i / 10.0, 1, .12); assertTrue(Float.isFinite(output) && Math.abs(output) < 1);
        }
    }
}
