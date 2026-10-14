package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class SampleVoiceTest {
    private PianoSample sine() {
        short[] pcm = new short[24000 * 2];
        for (int i = 0; i < 24000; i++) { pcm[i * 2] = (short) (Math.sin(i * 2 * Math.PI * 440 / 24000) * 30000); pcm[i * 2 + 1] = (short) (pcm[i * 2] * .5); }
        return new PianoSample(69, 24000, 2, pcm);
    }
    private int crossings(int pitch) {
        SampleVoice voice = new SampleVoice(); voice.start(sine(), pitch, 48000);
        int crossings = 0; float previous = 0;
        for (int i = 0; i < 12000; i++) { voice.next(); if (i > 480 && previous <= 0 && voice.left > 0) crossings++; previous = voice.left; assertEquals(voice.left * .5, voice.right, .0001); }
        return crossings;
    }
    @Test public void resamplingPreservesPitchAndIndependentStereoChannels() {
        assertEquals(106, crossings(69), 1); assertEquals(211, crossings(81), 1);
    }
    @Test public void repeatedNoteHasIndependentAttackAndDoesNotRewindOldVoice() {
        PianoSample sample = sine(); SampleVoice old = new SampleVoice(), fresh = new SampleVoice();
        old.start(sample, 69, 48000); for (int i = 0; i < 613; i++) old.next();
        fresh.start(sample, 69, 48000); fresh.next(); old.next();
        assertEquals(0, fresh.left, 0); assertTrue(Math.abs(old.left) > .1); assertTrue(old.active); assertTrue(fresh.active);
    }
    @Test public void pedalHoldingAndReleaseHaveDifferentLifetimes() {
        PianoSample sample = sine(); SampleVoice held = new SampleVoice(), released = new SampleVoice();
        held.start(sample, 69, 48000); released.start(sample, 69, 48000);
        for (int i = 0; i < 4800; i++) { held.next(); released.next(); }
        released.release(.025);
        for (int i = 0; i < 16000; i++) { held.next(); released.next(); }
        assertTrue(held.active); assertFalse(released.active); assertEquals(0, released.left, 0);
    }
    @Test public void seekingDoesNotReplayAttackAndUsesTransposedSamplePosition() {
        PianoSample sample = sine(); SampleVoice playing = new SampleVoice(), seeked = new SampleVoice();
        playing.start(sample, 70, 48000); seeked.start(sample, 70, 48000);
        for (int i = 0; i < 12000; i++) playing.next();
        seeked.seek(.25); playing.next(); seeked.next();
        assertEquals(playing.left, seeked.left, .0001); assertEquals(playing.right, seeked.right, .0001);
        seeked.seek(20); assertFalse(seeked.active); seeked.next(); assertEquals(0, seeked.left, 0);
    }
    @Test public void sampleEndsCleanlyWithoutReadingBeyondArray() {
        SampleVoice voice = new SampleVoice(); voice.start(new PianoSample(60, 24000, 1, new short[]{0, 3277, 0}), 61, 48000);
        for (int i = 0; i < 20; i++) voice.next();
        assertFalse(voice.active); assertEquals(0, voice.left, 0); assertEquals(0, voice.right, 0);
    }
    private PianoSample constant(double value) {
        short[] pcm = new short[24000 * 2]; java.util.Arrays.fill(pcm, (short) (value * 32767));
        return new PianoSample(69, 24000, 2, pcm);
    }
    @Test public void softLayerIsLiftedSoLayerBlendKeepsRoughlyEqualLevel() {
        PianoSample soft = constant(.5), loud = constant(1);
        SampleVoice quiet = new SampleVoice(), strong = new SampleVoice();
        quiet.start(soft, loud, 0, 69, 48000); strong.start(soft, loud, 1, 69, 48000);
        for (int i = 0; i < 200; i++) { quiet.next(); strong.next(); }
        assertEquals(strong.left, quiet.left, .15);
    }
}
