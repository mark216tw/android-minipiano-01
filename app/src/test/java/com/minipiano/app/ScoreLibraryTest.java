package com.minipiano.app;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ScoreLibraryTest {
    @Test public void independentScoresRenameUpdateAndDeletePersist() throws Exception {
        Context context = RuntimeEnvironment.getApplication(); ScoreLibrary library = new ScoreLibrary(context);
        Score first = Score.demo(); first.title = "第一首";
        Score second = first.copy(); second.title = "第二首";
        String a = library.add(first, "匯入"), b = library.add(second, "錄製");
        assertNotEquals(a, b); assertEquals(2, library.list().size());
        library.rename(a, "新名稱");
        library.update(a, first); // A queued playback-setting update must not undo a rename.
        ScoreLibrary reopened = new ScoreLibrary(context);
        assertEquals("新名稱", reopened.load(a).title);
        assertEquals("第二首", reopened.load(b).title);
        assertEquals(b, reopened.selected());
        reopened.delete(b); assertNull(reopened.selected());
        assertEquals(1, reopened.list().size()); assertEquals(first.notes.size(), reopened.load(a).notes.size());
    }
    @Test public void migrationIsRepeatableAndRetainsOriginal() throws Exception {
        Context context = RuntimeEnvironment.getApplication(); Score old = Score.demo(); old.title = "舊演奏";
        new ScoreStorage(context).save(old);
        ScoreLibrary library = new ScoreLibrary(context); library.migrate();
        context.getSharedPreferences("scoreLibrary", Context.MODE_PRIVATE).edit().putBoolean("migrated", false).commit();
        new ScoreLibrary(context).migrate();
        assertEquals(1, library.list().size()); assertEquals("legacy", library.selected());
        assertEquals("舊演奏", library.load("legacy").title); assertNotNull(new ScoreStorage(context).load());
    }
    @Test public void invalidNamesDoNotChangeStoredTitle() throws Exception {
        ScoreLibrary library = new ScoreLibrary(RuntimeEnvironment.getApplication()); Score score = Score.demo();
        String id = library.add(score, "錄製");
        try { library.rename(id, "   "); fail("Expected invalid name"); } catch (IllegalArgumentException expected) { }
        assertEquals(score.title, library.load(id).title);
    }
}
