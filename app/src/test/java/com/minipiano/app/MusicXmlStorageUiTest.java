package com.minipiano.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class MusicXmlStorageUiTest {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><score-partwise><work><work-title>測試 &amp; 保存</work-title></work><part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list><part id=\"P1\"><measure><attributes><divisions>1</divisions><time><beats>1</beats><beat-type>4</beat-type></time></attributes><direction><sound tempo=\"60\" dynamics=\"50\"/></direction><note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note><backup><duration>1</duration></backup><note><pitch><step>C</step><octave>3</octave></pitch><duration>1</duration><voice>2</voice><staff>2</staff></note></measure></part></score-partwise>";
    @Before public void reset() { var context = RuntimeEnvironment.getApplication(); context.deleteFile("performance.json"); UiTheme.preferences(context).edit().clear().putBoolean("sampledPiano", false).commit(); }
    private Score imported() throws Exception { return MusicXml.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8))); }
    @Test public void sourceTempoVoiceAndMuteSurviveSavingAndOriginalRemainsExact() throws Exception {
        var context = RuntimeEnvironment.getApplication(); Score score = imported(); score.mutedVoices.add(score.notes.get(0).channelKey());
        new ScoreStorage(context).save(score); Score restored = new ScoreStorage(context).load();
        assertEquals(XML, restored.originalXml); assertEquals(60, restored.timeline().bpmAt(0), 0);
        assertEquals(score.notes.get(0).voice, restored.notes.get(0).voice); assertEquals(2, restored.channels().size());
        assertEquals(score.mutedVoices, restored.mutedVoices); assertEquals(score.endTick, restored.endTick); assertEquals(score.features, restored.features);
    }
    @Test public void legacyFourFieldRecordingLoadsWithDefaultVoiceAndTempo() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        String legacy = "{\"title\":\"舊錄製\",\"bpm\":120,\"beats\":4,\"beatType\":4,\"notes\":[[60,0,480,90]],\"pedals\":[[0,true],[480,false]]}";
        try (var output = context.openFileOutput("performance.json", 0)) { output.write(legacy.getBytes(StandardCharsets.UTF_8)); }
        Score score = new ScoreStorage(context).load(); assertEquals("舊錄製", score.title); assertNull(score.originalXml);
        assertEquals(.5, score.seconds(score.length()), .00001); assertEquals("P1", score.notes.get(0).part); assertEquals(1, score.channels().size());
    }
    @Test public void voiceDialogMutesAndOriginalExportWritesUnmodifiedXml() throws Exception {
        var context = RuntimeEnvironment.getApplication(); new ScoreStorage(context).save(imported());
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get(); View content = activity.findViewById(android.R.id.content);
            Button voices = content.findViewWithTag("voiceControls");
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!voices.isEnabled() && System.nanoTime() < deadline) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); }
            assertTrue(voices.isEnabled()); voices.performClick();
            var dialog = ShadowAlertDialog.getLatestAlertDialog(); assertEquals(2, dialog.getListView().getCount());
            View choice = dialog.getListView().getAdapter().getView(0, null, dialog.getListView());
            assertTrue(dialog.getListView().isItemChecked(0)); dialog.getListView().performItemClick(choice, 0, 0); dialog.dismiss();
            deadline = System.nanoTime() + 5_000_000_000L;
            ScoreLibrary library = new ScoreLibrary(context);
            File saved = new File(context.getFilesDir(), "scores/" + library.selected() + ".json");
            while (System.nanoTime() < deadline) {
                if (saved.exists() && !new File(saved + ".new").exists() && !new File(saved + ".bak").exists()) {
                    try { if (new org.json.JSONObject(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8)).getJSONArray("mutedVoices").length() == 1) break; }
                    catch (Exception ignored) {}
                }
                Thread.sleep(10);
            }
            assertEquals(1, library.load(library.selected()).mutedVoices.size());
            findButton(content, "↧ 匯出").performClick(); var export = ShadowAlertDialog.getLatestAlertDialog(); export.getListView().performItemClick(null, 0, 0);
            Intent request = Shadows.shadowOf(activity).getNextStartedActivity(); assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.getAction());
            assertTrue(request.getStringExtra(Intent.EXTRA_TITLE).contains("_原譜"));
            File output = new File(context.getCacheDir(), "original-export.musicxml");
            activity.onActivityResult(11, android.app.Activity.RESULT_OK, new Intent().setData(Uri.fromFile(output)));
            deadline = System.nanoTime() + 5_000_000_000L;
            while ((!output.exists() || output.length() == 0) && System.nanoTime() < deadline) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); }
            assertEquals(XML, new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8));
        }
    }
    private Button findButton(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text)) return (Button) view;
        if (view instanceof android.view.ViewGroup) { var group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { Button found = findButton(group.getChildAt(i), text); if (found != null) return found; } }
        return null;
    }
}
