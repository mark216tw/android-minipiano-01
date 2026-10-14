package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MusicXmlTest {
    private Score read(String xml) throws Exception { return MusicXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))); }
    private String wrap(String measures) { return "<score-partwise version=\"4.0\"><part id=\"P1\">" + measures + "</part></score-partwise>"; }
    private String note(String extra, String step, int duration) {
        return "<note>" + extra + "<pitch><step>" + step + "</step><octave>4</octave></pitch><duration>" + duration + "</duration><voice>1</voice></note>";
    }
    @Test public void chordBackupAndForwardKeepIndependentTimelines() throws Exception {
        Score s = read(wrap("<measure number=\"1\"><attributes><divisions>2</divisions></attributes>" +
            note("", "C", 2) + note("<chord/>", "E", 2) +
            "<backup><duration>2</duration></backup><forward><duration>1</duration></forward>" + note("", "G", 1) + "</measure>"));
        assertEquals(3, s.notes.size()); assertEquals(0, s.notes.get(0).start); assertEquals(0, s.notes.get(1).start);
        assertEquals(240, s.notes.get(2).start); assertEquals(240, s.notes.get(2).duration);
    }
    @Test public void tiesMergeAcrossMeasuresAndPartsRemainConcurrent() throws Exception {
        String c1 = "<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><tie type=\"start\"/></note>";
        String c2 = c1.replace("start", "stop");
        Score s = read("<score-partwise><part id=\"P1\"><measure>" + c1 + "</measure><measure>" + c2 + "</measure></part>" +
            "<part id=\"P2\"><measure>" + note("", "G", 1) + "</measure></part></score-partwise>");
        assertEquals(2, s.notes.size()); assertEquals(3840, s.notes.get(0).duration); assertEquals(0, s.notes.get(1).start);
    }
    @Test public void pickupGraceRestAndDottedMetronomeAreHandled() throws Exception {
        Score s = read(wrap("<measure implicit=\"yes\"><attributes><divisions>1</divisions></attributes>" +
            "<direction><direction-type><metronome><beat-unit>quarter</beat-unit><beat-unit-dot/><per-minute>80</per-minute></metronome></direction-type></direction>" +
            "<note><grace/><pitch><step>D</step><octave>4</octave></pitch></note>" + note("", "C", 1) + "</measure>" +
            "<measure><note><rest/><duration>1</duration></note>" + note("", "E", 1) + "</measure>"));
        assertEquals(120, s.bpm); assertEquals(2, s.notes.size()); assertEquals(960, s.notes.get(1).start);
        assertTrue(s.warnings.contains("裝飾音暫略過"));
    }
    @Test public void exportRetainsOverlapsCrossBarTiesAndPedalBoundary() throws Exception {
        Score original = new Score(); original.title = "測試 & <鋼琴>"; original.bpm = 88;
        original.notes.add(new Score.Note(60, 0, 2400, 90));
        original.notes.add(new Score.Note(64, 0, 480, 90));
        original.notes.add(new Score.Note(67, 240, 1200, 90));
        original.notes.add(new Score.Note(48, 1440, 480, 90));
        original.pedals.add(new Score.Pedal(0, true)); original.pedals.add(new Score.Pedal(1920, false));
        Score restored = read(MusicXml.write(original));
        assertEquals(original.title, restored.title); assertEquals(88, restored.bpm); assertEquals(4, restored.notes.size());
        original.sort();
        for (int i = 0; i < original.notes.size(); i++) {
            Score.Note a = original.notes.get(i), b = restored.notes.get(i);
            assertEquals(a.pitch, b.pitch); assertEquals(a.start, b.start); assertEquals(a.duration, b.duration);
        }
        assertEquals(2, restored.pedals.size()); assertEquals(1920, restored.pedals.get(1).tick); assertFalse(restored.pedals.get(1).down);
    }
    @Test public void sixEightAndFinalPedalReleaseRoundTrip() throws Exception {
        Score s = new Score(); s.beats = 6; s.beatType = 8;
        s.notes.add(new Score.Note(61, 0, 2880, 90));
        s.pedals.add(new Score.Pedal(0, true)); s.pedals.add(new Score.Pedal(2880, false));
        Score restored = read(MusicXml.write(s));
        assertEquals(6, restored.beats); assertEquals(8, restored.beatType); assertEquals(2880, restored.notes.get(0).duration);
        assertEquals(2880, restored.pedals.get(1).tick);
    }
    @Test public void compressedMxlUsesContainerRootRatherThanFirstXml() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "unrelated.xml", "<wrong/>");
            put(zip, "META-INF/container.xml", "<container><rootfiles><rootfile full-path=\"scores/main.musicxml\"/></rootfiles></container>");
            put(zip, "scores/main.musicxml", wrap("<measure>" + note("", "C", 1) + "</measure>"));
        }
        Score s = MusicXml.read(new ByteArrayInputStream(output.toByteArray())); assertEquals(60, s.notes.get(0).pitch);
    }
    private void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    @Test public void standardDoctypeDoesNotRequireNetwork() throws Exception {
        Score s = read("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" \"https://invalid.example/partwise.dtd\">" + wrap("<measure>" + note("", "C", 1) + "</measure>"));
        assertEquals(1, s.notes.size());
    }
    @Test public void entitiesAndInvalidVoicePositionAreRejected() throws Exception {
        assertThrows(Exception.class, () -> read("<!DOCTYPE score-partwise [<!ENTITY x SYSTEM 'file:///secret'>]>" + wrap("<measure>" + note("", "C", 1) + "</measure>")));
        assertThrows(Exception.class, () -> read(wrap("<measure><backup><duration>1</duration></backup>" + note("", "C", 1) + "</measure>")));
    }
    @Test public void quantizationRetainsOriginalTimingAndPreventsZeroLength() {
        Score original = new Score(); original.notes.add(new Score.Note(60, 65, 3, 90));
        Score quantized = original.quantized(120);
        assertEquals(65, original.notes.get(0).start); assertEquals(3, original.notes.get(0).duration);
        assertEquals(120, quantized.notes.get(0).start); assertEquals(120, quantized.notes.get(0).duration);
        assertThrows(IllegalArgumentException.class, () -> original.quantized(0));
    }
    @Test public void demoScoreCanBeExchangedWithoutLosingNotes() throws Exception {
        Score demo = Score.demo(); Score restored = read(MusicXml.write(demo));
        assertEquals(demo.notes.size(), restored.notes.size()); assertEquals(demo.length(), restored.length());
    }
    @Test public void simultaneousEqualLengthNotesExportAsChord() throws Exception {
        Score s = new Score(); s.notes.add(new Score.Note(60, 0, 2400, 90));
        s.notes.add(new Score.Note(64, 0, 2400, 90)); s.notes.add(new Score.Note(67, 2400, 480, 90));
        String xml = MusicXml.write(s); assertTrue(xml.contains("<chord/>")); assertTrue(xml.contains("<type>whole</type>"));
        Score restored = read(xml); assertEquals(3, restored.notes.size());
        assertEquals(2400, restored.notes.get(0).duration); assertEquals(2400, restored.notes.get(1).duration);
        assertEquals(2400, restored.notes.get(2).start);
    }
}
