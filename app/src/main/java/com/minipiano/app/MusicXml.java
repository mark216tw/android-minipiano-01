package com.minipiano.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.json.JSONException;
import org.json.JSONObject;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline score-partwise reader/writer. No network resolution of XML entities. */
public final class MusicXml {
    private static final int LIMIT = 8 * 1024 * 1024;
    private MusicXml() {}
    private static byte[] bounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
        int count; while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("樂譜檔案過大（上限 8 MB）");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private static Document document(byte[] bytes) throws Exception {
        String text = new String(bytes, StandardCharsets.UTF_8);
        // Standard MusicXML DOCTYPE declarations are allowed, but never fetched.
        if (text.contains("<!ENTITY") || text.indexOf('\0') >= 0) throw new IOException("不支援 XML 實體或非 UTF-8 編碼");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false); factory.setExpandEntityReferences(false);
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        return builder.parse(new ByteArrayInputStream(bytes));
    }
    public static Score read(InputStream stream) throws Exception {
        byte[] bytes = bounded(stream, LIMIT);
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            Map<String, byte[]> entries = new LinkedHashMap<>(); int total = 0, count = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > 128) throw new IOException("MXL 包含過多檔案");
                    if (entry.isDirectory()) continue;
                    byte[] data = bounded(zip, LIMIT - total); total += data.length;
                    entries.put(entry.getName(), data);
                }
            }
            byte[] container = entries.get("META-INF/container.xml");
            if (container == null) throw new IOException("MXL 缺少 META-INF/container.xml");
            Document manifest = document(container);
            var roots = manifest.getElementsByTagName("rootfile");
            if (roots.getLength() == 0) throw new IOException("MXL 未指定樂譜");
            String path = ((Element) roots.item(0)).getAttribute("full-path");
            bytes = entries.get(path);
            if (bytes == null) throw new IOException("MXL 找不到樂譜：" + path);
        }
        bytes = unwrapJson(bytes);
        return MusicXmlReader.read(document(bytes), new String(bytes, StandardCharsets.UTF_8));
    }
    private static byte[] unwrapJson(byte[] bytes) throws IOException {
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (content.startsWith("\uFEFF")) content = content.substring(1).trim();
        if (!content.startsWith("{")) return bytes;
        Object value;
        try { value = new JSONObject(content).opt("musicxml"); }
        catch (JSONException e) { throw new IOException("JSON 包裝格式無效：" + e.getMessage(), e); }
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IOException("JSON 檔案必須包含非空字串 musicxml 欄位，內容為 MusicXML XML");
        }
        String xml = ((String) value).trim();
        if (xml.startsWith("\uFEFF")) xml = xml.substring(1).trim();
        if (!xml.startsWith("<")) throw new IOException("musicxml 欄位的內容必須是 MusicXML XML");
        byte[] result = xml.getBytes(StandardCharsets.UTF_8);
        if (result.length > LIMIT) throw new IOException("樂譜檔案過大（上限 8 MB）");
        return result;
    }
    private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    public static String write(Score source) {
        source.sort();
        // Greedy interval partitioning retains independently overlapping note lengths.
        List<List<Score.Note>> voices = new ArrayList<>(); List<Integer> staves = new ArrayList<>();
        for (Score.Note note : source.notes) {
            int staff = note.pitch < 60 ? 2 : 1, selected = -1;
            for (int v = 0; v < voices.size(); v++) {
                List<Score.Note> voice = voices.get(v);
                Score.Note last = voice.get(voice.size() - 1);
                if (staves.get(v) == staff && (last.end() <= note.start || (last.start == note.start && last.end() == note.end()))) { selected = v; break; }
            }
            if (selected < 0) { selected = voices.size(); voices.add(new ArrayList<>()); staves.add(staff); }
            voices.get(selected).add(note);
        }
        if (voices.isEmpty()) { voices.add(new ArrayList<>()); staves.add(1); }
        long measureLength = (long) source.beats * 480 * 4 / source.beatType;
        long measures = Math.max(1, (source.length() + measureLength - 1) / measureLength);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<score-partwise version=\"4.0\"><work><work-title>");
        xml.append(escape(source.title)).append("</work-title></work><part-list><score-part id=\"P1\"><part-name>Piano</part-name><score-instrument id=\"I1\"><instrument-name>Piano</instrument-name></score-instrument><midi-instrument id=\"I1\"><midi-channel>1</midi-channel><midi-program>1</midi-program></midi-instrument></score-part></part-list><part id=\"P1\">");
        for (long m = 0; m < measures; m++) {
            long begin = m * measureLength, end = begin + measureLength;
            xml.append("<measure number=\"").append(m + 1).append("\">");
            if (m == 0) {
                xml.append("<attributes><divisions>480</divisions><key><fifths>0</fifths></key><time><beats>").append(source.beats).append("</beats><beat-type>").append(source.beatType).append("</beat-type></time><staves>2</staves><clef number=\"1\"><sign>G</sign><line>2</line></clef><clef number=\"2\"><sign>F</sign><line>4</line></clef></attributes>");
                xml.append("<direction><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>").append(source.bpm).append("</per-minute></metronome></direction-type><sound tempo=\"").append(source.bpm).append("\"/></direction>");
            }
            for (Score.Tempo tempo : source.tempos) if (tempo.tick >= begin && tempo.tick < end) {
                xml.append("<direction><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>").append(tempo.bpm).append("</per-minute></metronome></direction-type><offset>").append(tempo.tick - begin).append("</offset><sound tempo=\"").append(tempo.bpm).append("\"/></direction>");
            }
            for (Score.Pedal pedal : source.pedals) if (pedal.tick >= begin && (pedal.tick < end || (m == measures - 1 && pedal.tick == end))) {
                xml.append("<direction><direction-type><pedal type=\"").append(pedal.down ? "start" : "stop").append("\" line=\"yes\"/></direction-type><offset>").append(pedal.tick - begin).append("</offset><staff>1</staff></direction>");
            }
            for (int v = 0; v < voices.size(); v++) {
                if (v > 0) xml.append("<backup><duration>").append(measureLength).append("</duration></backup>");
                long cursor = begin, previousStart = -1, previousFinish = -1;
                for (Score.Note note : voices.get(v)) {
                    if (note.end() <= begin) continue;
                    if (note.start >= end) break;
                    long start = Math.max(begin, note.start), finish = Math.min(end, note.end());
                    if (start > cursor) appendRest(xml, start - cursor, v + 1, staves.get(v));
                    appendNote(xml, note, finish - start, v + 1, staves.get(v), note.start < begin, note.end() > end, start == previousStart && finish == previousFinish);
                    cursor = finish;
                    previousStart = start; previousFinish = finish;
                }
                if (cursor < end) appendRest(xml, end - cursor, v + 1, staves.get(v));
            }
            xml.append("</measure>\n");
        }
        return xml.append("</part></score-partwise>\n").toString();
    }
    private static void appendRest(StringBuilder xml, long duration, int voice, int staff) {
        xml.append("<note><rest/><duration>").append(duration).append("</duration><voice>").append(voice).append("</voice>");
        appendType(xml, duration);
        xml.append("<staff>").append(staff).append("</staff></note>");
    }
    private static void appendNote(StringBuilder xml, Score.Note n, long duration, int voice, int staff, boolean stop, boolean start, boolean chord) {
        String[] steps = {"C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B"};
        boolean sharp = n.pitch % 12 == 1 || n.pitch % 12 == 3 || n.pitch % 12 == 6 || n.pitch % 12 == 8 || n.pitch % 12 == 10;
        xml.append("<note dynamics=\"").append(n.velocity * 100.0 / 90).append("\">"); if (chord) xml.append("<chord/>");
        xml.append("<pitch><step>").append(steps[n.pitch % 12]).append("</step>");
        if (sharp) xml.append("<alter>1</alter>");
        xml.append("<octave>").append(n.pitch / 12 - 1).append("</octave></pitch><duration>").append(duration).append("</duration>");
        if (stop) xml.append("<tie type=\"stop\"/>"); if (start) xml.append("<tie type=\"start\"/>");
        xml.append("<voice>").append(voice).append("</voice>"); appendType(xml, duration);
        xml.append("<staff>").append(staff).append("</staff>");
        if (stop || start) {
            xml.append("<notations>"); if (stop) xml.append("<tied type=\"stop\"/>"); if (start) xml.append("<tied type=\"start\"/>"); xml.append("</notations>");
        }
        xml.append("</note>");
    }
    private static void appendType(StringBuilder xml, long duration) {
        long[] ticks = {1920, 960, 480, 240, 120, 60, 30};
        String[] types = {"whole", "half", "quarter", "eighth", "16th", "32nd", "64th"};
        for (int i = 0; i < ticks.length; i++) {
            boolean dotted = duration * 2 == ticks[i] * 3;
            if (duration == ticks[i] || dotted) {
                xml.append("<type>").append(types[i]).append("</type>"); if (dotted) xml.append("<dot/>"); return;
            }
        }
        // Duration remains authoritative when a single standard glyph cannot represent it.
    }
}
