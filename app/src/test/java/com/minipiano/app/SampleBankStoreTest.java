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
    @Test public void switchingKeepsOnlySelectedBankAndCancelsObsoleteRequests() {
        var context = RuntimeEnvironment.getApplication();
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
        assertNull(SampleBankStore.bank(PianoSound.SYNTHETIC));
        SampleBankStore.start(context, PianoSound.SALAMANDER);
        assertTrue(SampleBankStore.status(PianoSound.SALAMANDER).contains("載入"));
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertNull(SampleBankStore.bank(PianoSound.SALAMANDER));
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
    }
}
