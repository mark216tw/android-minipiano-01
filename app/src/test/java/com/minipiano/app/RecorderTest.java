package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecorderTest {
    @Test public void recordedNoteKeepsStrikeVelocity() {
        Recorder recorder = new Recorder();
        recorder.start(120, 4, 4);
        recorder.on(1, 60, 35);
        recorder.off(1);
        recorder.on(2, 64, 112);
        recorder.off(2);
        Score score = recorder.stop();
        assertEquals(2, score.notes.size());
        assertEquals(35, score.notes.get(0).velocity);
        assertEquals(112, score.notes.get(1).velocity);
    }

    @Test public void velocityIsClampedToMidiRange() {
        Recorder recorder = new Recorder();
        recorder.start(120, 4, 4);
        recorder.on(1, 60, 0);
        recorder.off(1);
        recorder.on(2, 62, 500);
        recorder.off(2);
        Score score = recorder.stop();
        assertEquals(1, score.notes.get(0).velocity);
        assertEquals(127, score.notes.get(1).velocity);
    }
}
