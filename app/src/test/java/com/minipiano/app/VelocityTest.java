package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class VelocityTest {
    @Test public void lowerStrikeIsLouderAndBoundsAreClamped() {
        assertEquals(Velocity.MIN, Velocity.fromPosition(0, 100, 0));
        assertEquals(Velocity.MAX, Velocity.fromPosition(0, 100, 100));
        assertTrue(Velocity.fromPosition(0, 100, 80) > Velocity.fromPosition(0, 100, 20));
        assertEquals(Velocity.MAX, Velocity.fromPosition(0, 100, 400));
        assertEquals(Velocity.MIN, Velocity.fromPosition(0, 100, -400));
    }

    @Test public void blackKeyRangeIsMappedIndependently() {
        assertEquals(Velocity.MIN, Velocity.fromPosition(0, 61, 0));
        assertEquals(Velocity.MAX, Velocity.fromPosition(0, 61, 61));
        assertTrue(Velocity.fromPosition(0, 61, 30) > Velocity.fromPosition(0, 61, 10));
    }
}
