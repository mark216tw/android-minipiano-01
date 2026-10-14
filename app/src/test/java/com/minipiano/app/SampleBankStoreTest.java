package com.minipiano.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class SampleBankStoreTest {
    private SampleBank await(int sound) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (SampleBankStore.bank(sound) == null && System.nanoTime() < deadline) {
            assertFalse(SampleBankStore.status(sound), SampleBankStore.status(sound).startsWith("載入失敗")); Thread.sleep(10);
        }
        SampleBank bank = SampleBankStore.bank(sound); assertNotNull("Timed out: " + SampleBankStore.status(sound), bank); return bank;
    }
    @Test public void switchingKeepsOnlySelectedBankAndCancelsObsoleteRequests() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC)); assertNull(SampleBankStore.bank(PianoSound.SYNTHETIC));
        SampleBankStore.start(context, PianoSound.SALAMANDER); assertEquals(30, await(PianoSound.SALAMANDER).sampleCount);
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertNull(SampleBankStore.bank(PianoSound.SALAMANDER)); assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
    }
}
