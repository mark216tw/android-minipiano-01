package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DamperTest {
    @Test public void bassRingsLongerThanTreble() {
        assertTrue(Damper.releaseSeconds(21) > Damper.releaseSeconds(60));
        assertTrue(Damper.releaseSeconds(60) > Damper.releaseSeconds(88));
        assertTrue(Damper.releaseSeconds(88) > .04);
        assertTrue(Damper.releaseSeconds(21) < .3);
    }

    @Test public void topOctaveIsUndamped() {
        assertTrue(Damper.releaseSeconds(89) > 1);
        assertTrue(Damper.releaseSeconds(108) > 1);
    }
}
