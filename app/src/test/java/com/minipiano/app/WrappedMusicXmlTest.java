package com.minipiano.app;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class WrappedMusicXmlTest {
    private Score read(String content) throws Exception {
        return MusicXml.read(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void actualAttachmentCanBeImported() throws Exception {
        String file = System.getProperty("minipiano.attachmentTestFile");
        assumeNotNull(file);
        try (var input = Files.newInputStream(Path.of(file))) {
            Score score = MusicXml.read(input);
            assertEquals("Little song in canon form (Op. 68 No. 27)", score.title);
            assertEquals(60, score.bpm); assertEquals(2, score.beats); assertEquals(4, score.beatType);
            assertTrue(score.notes.size() > 300);
            assertEquals(68, score.notes.get(0).pitch); assertEquals(0, score.notes.get(0).start);
            assertEquals(240, score.notes.get(0).duration);
            assertNotNull(score.originalXml); assertTrue(score.originalXml.startsWith("<?xml"));
            assertTrue(score.channels().size() >= 4); assertTrue(score.features.contains("力度")); assertTrue(score.features.contains("漸強／漸弱"));
            assertTrue(score.notes.stream().anyMatch(n -> n.velocity < 50)); assertTrue(score.notes.stream().anyMatch(n -> n.velocity > 90));
        }
    }
    @Test public void jsonEscapesAndMetadataDoNotChangeEmbeddedScore() throws Exception {
        Score original = Score.demo(); original.title = "測試 \"鋼琴\" & 小星星";
        String xml = MusicXml.write(original);
        JSONObject wrapper = new JSONObject().put("filename", "shelf:116").put("meta", new JSONObject().put("tempo_bpm", "200"))
            .put("musicxml", xml).put("title", "metadata title");
        Score result = read("\uFEFF \n" + wrapper);
        assertEquals(original.title, result.title); assertEquals(original.bpm, result.bpm);
        assertEquals(original.notes.size(), result.notes.size()); assertEquals(original.length(), result.length());
    }
    @Test public void invalidWrapperHasActionableError() {
        for (String invalid : new String[]{"{}", "{\"musicxml\":null}", "{\"musicxml\":123}", "{\"musicxml\":{}}", "{\"musicxml\":\" \"}"}) {
            Exception exception = assertThrows(Exception.class, () -> read(invalid));
            assertTrue(exception.getMessage().contains("musicxml"));
        }
    }
    @Test public void extractedXmlStillRejectsCustomEntities() throws Exception {
        String xml = "<!DOCTYPE score-partwise [<!ENTITY secret SYSTEM 'file:///secret'>]><score-partwise/>";
        String wrapper = new JSONObject().put("musicxml", xml).toString();
        Exception exception = assertThrows(Exception.class, () -> read(wrapper));
        assertTrue(exception.getMessage().contains("XML 實體"));
    }
}
