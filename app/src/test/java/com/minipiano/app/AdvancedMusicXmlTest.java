package com.minipiano.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class AdvancedMusicXmlTest {
    private Score read(String measures) throws Exception { return xml("<score-partwise><part id=\"P1\">" + measures + "</part></score-partwise>"); }
    private Score xml(String content) throws Exception { return MusicXml.read(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))); }
    private String attributes() { return "<attributes><divisions>1</divisions><time><beats>1</beats><beat-type>4</beat-type></time></attributes>"; }
    private String note(String step, int octave, int duration, int voice, int staff, String extra) {
        return "<note><pitch><step>" + step + "</step><octave>" + octave + "</octave></pitch><duration>" + duration + "</duration><voice>" + voice + "</voice><staff>" + staff + "</staff>" + extra + "</note>";
    }
    private String bar(String step, String before, String after) { return "<measure>" + attributes() + before + note(step, 4, 1, 1, 1, "") + after + "</measure>"; }
    private String pitches(Score score) { return score.notes.stream().map(n -> Integer.toString(n.pitch)).collect(Collectors.joining(",")); }
    @Test public void dynamicsAndWedgeRespectStaffAndExactOffsets() throws Exception {
        Score s = read("<measure>" + attributes() +
            "<direction><direction-type><dynamics><p/></dynamics></direction-type><staff>1</staff></direction>" + note("C", 4, 1, 1, 1, "") +
            "<direction><direction-type><wedge type=\"crescendo\" number=\"1\"/></direction-type><staff>1</staff></direction>" +
            "<direction><direction-type><wedge type=\"stop\" number=\"1\"/></direction-type><offset>2</offset><staff>1</staff></direction>" +
            "<direction><direction-type><dynamics><f/></dynamics></direction-type><offset>2</offset><staff>1</staff></direction>" +
            note("D", 4, 1, 1, 1, "") + note("E", 4, 1, 1, 1, "") + note("F", 4, 1, 1, 1, "") +
            "<backup><duration>4</duration></backup>" + note("C", 3, 4, 2, 2, "") + "</measure>");
        assertEquals(90, s.notes.get(0).velocity); // Unmarked lower staff is independent.
        assertEquals(40, s.notes.get(1).velocity); assertEquals(40, s.notes.get(2).velocity);
        assertEquals(65, s.notes.get(3).velocity); assertEquals(90, s.notes.get(4).velocity);
        assertEquals(2, s.channels().size()); assertTrue(s.features.contains("漸強／漸弱"));
    }
    @Test public void noteDynamicsOverridesDirectionsAndSforzandoIsNotPermanent() throws Exception {
        Score s = read("<measure>" + attributes() +
            "<direction><direction-type><dynamics><p/></dynamics></direction-type></direction>" +
            "<direction><direction-type><dynamics><sfz/></dynamics></direction-type></direction>" + note("C", 4, 1, 1, 1, "") +
            note("D", 4, 1, 1, 1, "") + note("E", 4, 1, 1, 1, "").replace("<note>", "<note dynamics=\"100\">") + "</measure>");
        assertEquals(108, s.notes.get(0).velocity); assertEquals(40, s.notes.get(1).velocity); assertEquals(90, s.notes.get(2).velocity);
    }
    @Test public void tempoChangesBuildInvertibleTimelineAndPlaybackEvents() throws Exception {
        Score s = read("<measure>" + attributes() + "<direction><sound tempo=\"120\"/></direction>" +
            "<direction><direction-type><metronome><beat-unit>eighth</beat-unit><beat-unit-dot/><per-minute>80</per-minute></metronome></direction-type><offset>2</offset></direction>" +
            note("C", 4, 4, 1, 1, "") + "</measure>");
        assertEquals(2, s.timeline().size()); assertEquals(1, s.seconds(960), .00001); assertEquals(3, s.seconds(1920), .00001);
        assertEquals(1440, s.tick(2)); PlaybackProgram program = new PlaybackProgram(s);
        assertEquals(3, program.events[program.events.length - 1].seconds, .00001);
        for (int tick = 0; tick < 1920; tick++) assertEquals(tick, s.tick(s.seconds(tick)));
    }
    @Test public void speedChangePreservesActuallyHeardAgeAndSeekUsesTempoMap() {
        Score s = new Score(); s.notes.add(new Score.Note(60, 0, 1920, 90)); s.tempos.add(new Score.Tempo(960, 60)); s.sort();
        PlaybackClock clock = new PlaybackClock(new PlaybackProgram(s), 48000); clock.onset(0); clock.advance(48000);
        assertEquals(960, clock.tick(), .00001); clock.speed(.5); clock.advance(48000);
        assertEquals(1200, clock.tick(), .00001); assertEquals(2, clock.age(0), .00001);
        clock.speed(2); clock.advance(24000); assertEquals(1680, clock.tick(), .00001); assertEquals(2.5, clock.age(0), .00001);
        clock.seek(1440); assertEquals(2, clock.seconds(), .00001); assertEquals(1, clock.age(0), .00001);
    }
    @Test public void repeatRestoresAuthoredTempoAndVolumeOnEachPass() throws Exception {
        Score s = read(bar("C", "<direction><sound tempo=\"60\" dynamics=\"50\"/></direction><barline location=\"left\"><repeat direction=\"forward\"/></barline>", "") +
            bar("D", "<direction><sound tempo=\"120\" dynamics=\"100\"/></direction>", "<barline><repeat direction=\"backward\"/></barline>"));
        assertEquals("60,62,60,62", pitches(s)); assertEquals(3, s.seconds(s.length()), .00001); assertEquals(4, s.timeline().size());
        assertEquals(45, s.notes.get(0).velocity); assertEquals(90, s.notes.get(1).velocity); assertEquals(45, s.notes.get(2).velocity);
        assertEquals("1", s.measureAt(960).number); assertEquals(2, s.measureAt(960).visit);
    }
    @Test public void firstAndSecondEndingAndTimesThreeExpandCorrectly() throws Exception {
        String first = bar("C", "<barline location=\"left\"><repeat direction=\"forward\"/></barline>", "");
        String ending = bar("D", "<barline location=\"left\"><ending type=\"start\" number=\"1\"/></barline>", "<barline><ending type=\"stop\" number=\"1\"/><repeat direction=\"backward\"/></barline>");
        String second = bar("E", "<barline location=\"left\"><ending type=\"start\" number=\"2\"/></barline>", "<barline><ending type=\"stop\" number=\"2\"/></barline>");
        assertEquals("60,62,60,64", pitches(read(first + ending + second)));
        ending = ending.replace("number=\"1\"", "number=\"1,2\"").replace("direction=\"backward\"", "direction=\"backward\" times=\"3\"");
        second = second.replace("number=\"2\"", "number=\"3\"");
        assertEquals("60,62,60,62,60,64", pitches(read(first + ending + second)));
    }
    @Test public void nestedRepeatsResetInnerPasses() throws Exception {
        String forward = "<barline location=\"left\"><repeat direction=\"forward\"/></barline>", back = "<barline><repeat direction=\"backward\"/></barline>";
        Score s = read(bar("C", forward, "") + bar("D", forward, "") + bar("E", "", back) + bar("F", "", back));
        assertEquals("60,62,64,62,64,65,60,62,64,62,64,65", pitches(s));
    }
    @Test public void rightForwardAndLeftBackwardApplyToAdjacentBar() throws Exception {
        Score s = read(bar("C", "", "<barline location=\"right\"><repeat direction=\"forward\"/></barline>") + bar("D", "", "") +
            bar("E", "<barline location=\"left\"><repeat direction=\"backward\"/></barline>", ""));
        assertEquals("60,62,62,64", pitches(s));
    }
    @Test public void missingJumpTargetIsReportedWithoutLoopingAndSoundOffsetOverridesDirection() throws Exception {
        Score s = read(bar("C", "<direction><offset>1</offset><sound tempo=\"60\"><offset>0</offset></sound></direction>", "<direction><sound dalsegno=\"missing\"/></direction>"));
        assertEquals(60, s.bpm); assertEquals(1, s.notes.size()); assertTrue(s.warnings.contains("找不到 Segno 目標：missing"));
    }
    @Test public void daCapoAlFineAndDalSegnoAlCodaHaveFiniteRoutes() throws Exception {
        Score dc = read(bar("C", "", "") + bar("D", "", "<direction><sound fine=\"yes\"/></direction>") + bar("E", "", "<direction><sound dacapo=\"yes\"/></direction>"));
        assertEquals("60,62,64,60,62", pitches(dc));
        Score words = read(bar("C", "", "") + bar("D", "", "<direction><direction-type><words>Fine</words></direction-type></direction>") +
            bar("E", "", "<direction><direction-type><words>Da Capo al Fine</words></direction-type></direction>"));
        assertEquals("60,62,64,60,62", pitches(words));
        Score ds = read(bar("C", "<direction><sound segno=\"S\"/></direction>", "") + bar("D", "", "<direction><sound tocoda=\"C\"/></direction>") +
            bar("E", "", "<direction><sound dalsegno=\"S\"/></direction>") + bar("F", "<direction><sound coda=\"C\"/></direction>", "<direction><sound fine=\"yes\"/></direction>"));
        assertEquals("60,62,64,60,62,65", pitches(ds));
    }
    @Test public void toCodaAtStartSkipsThatMeasureOnReturn() throws Exception {
        Score s = read(bar("C", "<direction><sound segno=\"S\"/></direction>", "") +
            bar("D", "<direction><sound tocoda=\"C\"/></direction>", "") + bar("E", "", "<direction><sound dalsegno=\"S\"/></direction>") +
            bar("F", "<direction><sound coda=\"C\"/></direction>", "<direction><sound fine=\"yes\"/></direction>"));
        assertEquals("60,62,64,60,65", pitches(s));
    }
    @Test public void printedToCodaWordsDoNotOverrideNamedSoundTarget() throws Exception {
        Score s = read(bar("C", "<direction><sound segno=\"S\"/></direction>", "") +
            bar("D", "", "<direction><direction-type><words>To Coda</words></direction-type><sound tocoda=\"one\"/></direction>") +
            bar("E", "", "<direction><sound dalsegno=\"S\"/></direction>") +
            bar("F", "<direction><sound coda=\"one\"/></direction>", "<direction><sound fine=\"yes\"/></direction>") +
            bar("G", "<direction><sound coda=\"two\"/></direction>", ""));
        assertEquals("60,62,64,60,62,65", pitches(s));
    }
    @Test public void crossStaffTiesJoinWithoutMergingDifferentVoices() throws Exception {
        Score s = read("<measure>" + attributes() + note("C", 4, 1, 1, 1, "<tie type=\"start\"/>") +
            "<backup><duration>1</duration></backup>" + note("C", 4, 1, 2, 2, "") + "</measure>" +
            "<measure>" + note("C", 4, 1, 1, 2, "<tie type=\"stop\"/>") + "</measure>");
        assertEquals(2, s.notes.size()); assertEquals(960, s.notes.get(0).duration); assertEquals(480, s.notes.get(1).duration);
        assertEquals(3, s.notes.get(0).staffMask); assertTrue(s.features.contains("跨譜表延音線"));
        assertTrue(s.channels().get(0).label.contains("1、2"));
    }
    @Test public void originalXmlAndWarningsArePreservedAndRecordedExportRetainsVelocityTempo() throws Exception {
        String content = "<score-partwise><identification><creator>Test Composer</creator></identification><part id=\"P1\">" +
            bar("C", "<direction><direction-type><words>ritard.</words></direction-type></direction><direction><sound tempo=\"60\" dynamics=\"50\"/></direction>", "") + "</part></score-partwise>";
        Score s = xml(content); assertEquals(content, s.originalXml); assertTrue(s.warnings.contains("文字速度未自動解讀：ritard."));
        Score generated = xml(MusicXml.write(s.quantized(120))); assertEquals(s.bpm, generated.bpm); assertEquals(s.notes.get(0).velocity, generated.notes.get(0).velocity);
    }
    @Test public void fractionalDivisionsDoNotAccumulateTupletRoundingDrift() throws Exception {
        StringBuilder notes = new StringBuilder(); for (int i = 0; i < 7; i++) notes.append(note("C", 4, 1, 1, 1, ""));
        Score s = read("<measure><attributes><divisions>7</divisions><time><beats>1</beats><beat-type>4</beat-type></time></attributes>" + notes + "</measure>");
        assertEquals(480, s.length()); assertEquals(480, s.notes.get(6).end());
    }
    @Test public void pedalOrderAndPartsAreKeptIndependentInCompiledPlayback() throws Exception {
        String part1 = "<part id=\"P1\"><measure>" + attributes() + "<direction><direction-type><pedal type=\"start\"/></direction-type></direction>" + note("C", 4, 1, 1, 1, "") + "</measure></part>";
        String part2 = "<part id=\"P2\"><measure>" + attributes() + note("D", 4, 1, 1, 1, "") + "</measure></part>";
        Score score = xml("<score-partwise>" + part1 + part2 + "</score-partwise>"); PlaybackProgram program = new PlaybackProgram(score);
        assertEquals(2, program.parts.size()); assertEquals(1, score.pedals.size()); assertEquals("P1", score.pedals.get(0).part);
        assertEquals(PlaybackProgram.PEDAL, program.events[0].type); assertEquals(0, program.events[0].part);
        score.mutedVoices.add(score.notes.get(0).channelKey()); PlaybackProgram muted = new PlaybackProgram(score);
        assertTrue(muted.initialMuted[0]); assertFalse(muted.initialMuted[1]);
    }
}
