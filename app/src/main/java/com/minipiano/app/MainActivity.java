package com.minipiano.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int IMPORT = 10, EXPORT = 11;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Recorder recorder = new Recorder();
    private PianoAudio audio;
    private PianoView piano;
    private ScoreStorage storage;
    private Score score = new Score();
    private UiTheme theme;
    private LinearLayout mainRoot;
    private TextView title, detail, time, range;
    private TextView soundStatus;
    private String lastSoundStatus = "";
    private String lastScoreDetail = "";
    private Button record, play, pedal, tempo, speedButton, importButton, exportButton;
    private Button voicesButton, summaryButton;
    private java.util.List<Score.Channel> channels = java.util.Collections.emptyList();
    private boolean exportOriginal;
    private SeekBar progress;
    private boolean pedalDown, metro, countIn = true, counting, busy, dragging, resumed;
    private int bpm = 120, beats = 4, beatType = 4, widthIndex = 1, speedIndex = 2;
    private final double[] speeds = {.5, .75, 1, 1.25, 1.5};
    private int exportStep = 120;
    private long scoreLength;
    private double durationSeconds;
    private final Runnable beginRecording = this::beginRecording;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (!dragging && !score.notes.isEmpty()) progress.setProgress((int) (score.seconds((long) audio.position) * 1000 / Math.max(.001, durationSeconds)));
            play.setText(audio.playing ? "Ⅱ 暫停" : "▶ 播放");
            time.setText(formatTime(score.seconds((long) audio.position) / speeds[speedIndex]) + " / " + formatTime(durationSeconds / speeds[speedIndex]));
            if (!recorder.active() && !counting && !score.notes.isEmpty()) {
                Score.Measure measure = score.measureAt((long) audio.position);
                String info = score.notes.size() + " 音符 · " + channels.size() + " 聲部 · 樂曲 "
                    + String.format(Locale.ROOT, "%.1f BPM", score.timeline().bpmAt(audio.position) * speeds[speedIndex])
                    + (measure == null ? "" : " · 原譜第 " + measure.number + " 小節（第 " + measure.visit + " 次）")
                    + (score.mutedVoices.isEmpty() ? "" : " · 靜音 " + score.mutedVoices.size() + " 聲部");
                if (!info.equals(lastScoreDetail)) { detail.setText(info); lastScoreDetail = info; }
            }
            piano.invalidate();
            String sound = audio.soundStatus();
            if (!sound.equals(lastSoundStatus)) { soundStatus.setText("   " + sound); soundStatus.setContentDescription(sound); lastSoundStatus = sound; }
            if (audio.error != null) { String error = audio.error; audio.error = null; message("音訊無法啟動", error); }
            handler.postDelayed(this, 33);
        }
    };
    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio = new PianoAudio(this); storage = new ScoreStorage(this);
        var preferences = getPreferences(MODE_PRIVATE);
        bpm = preferences.getInt("bpm", 120); beats = preferences.getInt("beats", 4); beatType = preferences.getInt("beatType", 4);
        countIn = preferences.getBoolean("countIn", true); metro = preferences.getBoolean("metro", false);
        if (state != null) { exportStep = state.getInt("exportStep", 120); exportOriginal = state.getBoolean("exportOriginal", false); }
        createUi(); applyTheme(); setBusy(true);
        files.execute(() -> {
            try { Score saved = storage.load(); runOnUiThread(() -> { if (isDestroyed()) return; useScore(saved != null ? saved : Score.demo()); setBusy(false); }); }
            catch (Exception e) { runOnUiThread(() -> { setBusy(false); message("無法還原演奏", e.getMessage()); }); }
        });
    }
    private void enterFullscreen() {
        new UiTheme(this).window(this, true);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) enterFullscreen();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL);
        if (color == 0xFF70847B || color == 0xFF687D73) view.setTag("muted");
        return view;
    }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private Button button(String text, LinearLayout parent, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setTextSize(14); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setTextColor(0xFF244B43); button.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFFE1EFE8); bg.setCornerRadius(dp(13)); button.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44)); params.setMargins(0, 0, dp(7), 0); parent.addView(button, params);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private void scrollRow(LinearLayout root, LinearLayout row) {
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.addView(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50)); root.addView(scroll, params);
    }
    private void createUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFF2F9F5);
        mainRoot = root; UiTheme.insets(root, dp(10), true);
        LinearLayout header = row();
        TextView brand = label("mini 鋼琴", 23, 0xFF183D35); brand.setTypeface(null, Typeface.BOLD); header.addView(brand);
        soundStatus = label("   採樣音色載入中", 12, 0xFF70847B); soundStatus.setSingleLine(true); soundStatus.setEllipsize(android.text.TextUtils.TruncateAt.END); header.addView(soundStatus, new LinearLayout.LayoutParams(0, -1, 1));
        ImageButton gear = new ImageButton(this); gear.setImageResource(R.drawable.ic_settings); gear.setContentDescription("設定"); gear.setTag("settingsGear"); gear.setPadding(dp(12), dp(12), dp(12), dp(12));
        gear.setOnClickListener(v -> settings()); header.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout actions = row();
        importButton = button("↥ 匯入", actions, this::importScore);
        exportButton = button("↧ 匯出", actions, this::exportScore);
        record = button("● 錄製", actions, this::toggleRecord);
        play = button("▶ 播放", actions, () -> { if (audio.playing) audio.pause(); else audio.play(); });
        button("■ 停止", actions, this::stopEverything);
        speedButton = button("速度 1×", actions, () -> { speedIndex = (speedIndex + 1) % speeds.length; audio.speed(speeds[speedIndex]); speedButton.setText("速度 " + speeds[speedIndex] + "×"); });
        voicesButton = button("聲部", actions, this::voiceSettings); voicesButton.setTag("voiceControls");
        summaryButton = button("匯入摘要", actions, () -> message("匯入摘要", score.importSummary())); summaryButton.setTag("importSummary");
        scrollRow(root, actions);
        LinearLayout info = row();
        title = label("我的演奏", 15, 0xFF224B40); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        time = label("0:00 / 0:00", 12, 0xFF687D73); info.addView(time); root.addView(info, new LinearLayout.LayoutParams(-1, dp(27)));
        progress = new SeekBar(this); progress.setMax(1000); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(26)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {}
            public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            public void onStopTrackingTouch(SeekBar bar) { audio.seek(score.tick(durationSeconds * bar.getProgress() / 1000)); dragging = false; }
        });
        LinearLayout controls = row();
        tempo = button("錄製 " + bpm + " BPM", controls, this::settings);
        button("音域 −", controls, () -> { piano.octave(-1); updateRange(); });
        range = label("C3 – B4  ", 12, 0xFF687D73); controls.addView(range);
        button("音域 ＋", controls, () -> { piano.octave(1); updateRange(); });
        button("鍵寬", controls, () -> { widthIndex = (widthIndex + 1) % 3; piano.width(new int[]{8, 16, 21}[widthIndex]); updateRange(); });
        pedal = button("延音：關", controls, () -> setPedal(!pedalDown));
        scrollRow(root, controls);
        detail = label("綠色：手動彈奏  ·  黃色：自動演奏  ·  多指和弦與滑奏", 11, 0xFF70847B);
        root.addView(detail, new LinearLayout.LayoutParams(-1, dp(24)));
        piano = new PianoView(this, new PianoView.Listener() {
            public void down(int pointer, int pitch) { audio.on(pointer, pitch); recorder.on(pointer, pitch); }
            public void up(int pointer) { audio.off(pointer); recorder.off(pointer); }
        }, audio);
        root.addView(piano, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); updateRange();
    }
    private void updateRange() {
        int octave = piano.base() / 12 - 1, last = piano.lastPitch();
        String[] names = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
        range.setText("C" + octave + " – " + names[last % 12] + (last / 12 - 1) + "  ");
    }
    private void setPedal(boolean down) { pedalDown = down; pedal.setText(down ? "✓ 延音：開" : "延音：關"); audio.pedal(down); recorder.pedal(down); styleButton(pedal); }
    private void setBusy(boolean value) { busy = value; updateEnabled(); }
    private void updateEnabled() {
        boolean idle = !busy && !recorder.active() && !counting;
        importButton.setEnabled(idle); exportButton.setEnabled(idle && !score.notes.isEmpty());
        play.setEnabled(idle && !score.notes.isEmpty()); speedButton.setEnabled(idle);
        progress.setEnabled(idle && !score.notes.isEmpty()); record.setEnabled(!busy); tempo.setEnabled(idle);
        voicesButton.setEnabled(idle && !channels.isEmpty()); summaryButton.setEnabled(idle && score.originalXml != null);
    }
    private void useScore(Score value) {
        score = value; scoreLength = value.length(); durationSeconds = score.seconds(scoreLength); channels = score.channels(); audio.load(value); title.setText(value.title); lastScoreDetail = "";
        detail.setText(value.notes.size() + " 個音符 · " + channels.size() + " 聲部 · " + value.timeline().size() + " 速度段 · " + value.beats + "/" + value.beatType);
        updateEnabled();
    }
    private void voiceSettings() {
        String[] names = new String[channels.size()]; boolean[] audible = new boolean[channels.size()];
        for (int i = 0; i < channels.size(); i++) { names[i] = channels.get(i).label; audible[i] = !score.mutedVoices.contains(channels.get(i).key); }
        dialog().setTitle("聲部（勾選＝播放）").setMultiChoiceItems(names, audible, (d, which, enabled) -> {
            String key = channels.get(which).key; if (enabled) score.mutedVoices.remove(key); else score.mutedVoices.add(key);
            audio.mute(which, !enabled); save(score);
        }).setPositiveButton("完成", null).show();
    }
    private void toggleRecord() {
        if (recorder.active() || counting) { finishRecording(); return; }
        if (!score.notes.isEmpty()) dialog().setTitle("開始新錄製？").setMessage("新錄製會取代目前曲目。若需要保留，請先匯出。").setPositiveButton("開始", (d, w) -> prepareRecording()).setNegativeButton("取消", null).show();
        else prepareRecording();
    }
    private void prepareRecording() {
        audio.stop(); piano.releaseAll(); setPedal(false);
        if (!countIn) { beginRecording(); return; }
        counting = true; record.setText("取消倒數"); updateEnabled();
        int pulseBpm = bpm * beatType / 4;
        audio.metronome(true, pulseBpm, beats); detail.setText("錄製前倒數 " + beats + " 拍…");
        handler.postDelayed(beginRecording, Math.round(beats * 60000.0 / pulseBpm));
    }
    private void beginRecording() {
        if (!resumed) return;
        counting = false; piano.releaseAll(); setPedal(false);
        recorder.start(bpm, beats, beatType); audio.metronome(metro, bpm * beatType / 4, beats);
        record.setText("■ 結束錄製"); record.setTextColor(0xFFB03F48); detail.setText("● 錄製中 · " + bpm + " BPM · " + beats + "/" + beatType + " · 再按一次完成"); updateEnabled();
    }
    private void finishRecording() {
        boolean wasCounting = counting;
        handler.removeCallbacks(beginRecording); counting = false; audio.metronome(false, bpm, beats);
        piano.releaseAll(); setPedal(false);
        if (recorder.active()) {
            Score recorded = recorder.stop(); recorded.title = "我的演奏 " + new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.TAIWAN).format(new java.util.Date());
            useScore(recorded); save(recorded);
        } else if (wasCounting) useScore(score);
        record.setText("● 錄製"); styleButton(record); updateEnabled();
    }
    private void stopEverything() { if (recorder.active() || counting) finishRecording(); audio.stop(); piano.releaseAll(); setPedal(false); }
    private void save(Score value) {
        Score snapshot = value.copy(); files.execute(() -> { try { storage.save(snapshot); } catch (Exception e) { runOnUiThread(() -> toast("演奏儲存失敗：" + e.getMessage())); } });
    }
    private void settings() {
        if (recorder.active() || counting) { toast("請先結束錄製"); return; }
        startActivity(new Intent(this, SettingsActivity.class));
    }
    private void styleButton(Button button) {
        boolean selected = button == pedal && pedalDown;
        button.setSelected(selected); button.setTextColor(selected ? theme.onPrimary : theme.text); button.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
        button.setBackground(UiTheme.rounded(selected ? theme.primary : theme.surface, dp(13)));
        if (button == record && recorder.active()) button.setTextColor(theme.dark ? 0xFFFF969F : 0xFFB03F48);
    }
    private void tint(View view) {
        if (view instanceof ImageButton) {
            ImageButton image = (ImageButton) view; image.setImageTintList(ColorStateList.valueOf(theme.text)); image.setBackground(UiTheme.rounded(theme.surface, dp(14)));
        } else if (view instanceof Button) styleButton((Button) view);
        else if (view instanceof TextView) ((TextView) view).setTextColor("muted".equals(view.getTag()) ? theme.muted : theme.text);
        if (view instanceof SeekBar) {
            SeekBar bar = (SeekBar) view; bar.setThumbTintList(ColorStateList.valueOf(theme.primary)); bar.setProgressTintList(ColorStateList.valueOf(theme.primary)); bar.setProgressBackgroundTintList(ColorStateList.valueOf(theme.surface));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) tint(group.getChildAt(i));
        }
    }
    private void applyTheme() {
        theme = new UiTheme(this); mainRoot.setBackgroundColor(theme.background); tint(mainRoot); piano.theme(theme); theme.window(this, true);
    }
    private AlertDialog.Builder dialog() { return new AlertDialog.Builder(new ContextThemeWrapper(this, theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme)); }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); if (mainRoot != null) applyTheme(); }
    private void importScore() {
        audio.pause();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, IMPORT);
    }
    private void exportScore() {
        if (score.originalXml != null) {
            dialog().setTitle("匯出 MusicXML").setItems(new String[]{"原譜轉存（保留所有樂譜記號）", "演奏資料（量化／重新建立鋼琴譜）"}, (d, which) -> {
                exportOriginal = which == 0; if (exportOriginal) createExportDocument(); else exportQuantized();
            }).show();
        } else { exportOriginal = false; exportQuantized(); }
    }
    private void exportQuantized() {
        final String[] choices = {"八分音符（較簡單）", "十六分音符（較細緻）"};
        dialog().setTitle("MusicXML 節奏量化").setSingleChoiceItems(choices, exportStep == 240 ? 0 : 1, (d, which) -> exportStep = which == 0 ? 240 : 120)
            .setNegativeButton("取消", null).setPositiveButton("選擇儲存位置", (d, w) -> {
                createExportDocument();
            }).show();
    }
    private void createExportDocument() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.recordare.musicxml+xml"); intent.putExtra(Intent.EXTRA_TITLE, score.title.replaceAll("[\\\\/:*?\"<>|]", "_") + (exportOriginal ? "_原譜" : "_演奏") + ".musicxml");
        startActivityForResult(intent, EXPORT);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); setBusy(true);
        if (request == IMPORT) {
            files.execute(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException("無法開啟檔案");
                    Score imported = MusicXml.read(input); storage.save(imported);
                    runOnUiThread(() -> { if (isDestroyed()) return; useScore(imported); setBusy(false); message("匯入摘要", imported.importSummary()); });
                } catch (Exception e) { runOnUiThread(() -> { setBusy(false); message("匯入失敗", e.getMessage()); }); }
            });
        } else if (request == EXPORT) {
            Score snapshot = score.copy(); boolean original = exportOriginal; int step = exportStep;
            files.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException("無法寫入檔案");
                    Score value = snapshot.notes.isEmpty() ? storage.load() : snapshot;
                    if (value == null) throw new java.io.IOException("沒有可匯出的曲目");
                    if (original && value.originalXml == null) throw new java.io.IOException("沒有原始 MusicXML，請重新匯入");
                    output.write((original ? value.originalXml : MusicXml.write(value.quantized(step))).getBytes(StandardCharsets.UTF_8));
                    runOnUiThread(() -> { setBusy(false); toast("MusicXML 已匯出"); });
                } catch (Exception e) { runOnUiThread(() -> { setBusy(false); message("匯出失敗", e.getMessage()); }); }
            });
        } else setBusy(false);
    }
    private static String formatTime(double seconds) { int value = (int) seconds; return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60); }
    private void toast(String text) { if (!isDestroyed()) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void message(String title, String text) { if (!isDestroyed()) dialog().setTitle(title).setMessage(text == null ? "請稍後再試" : text).setPositiveButton("知道了", null).show(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("exportStep", exportStep); state.putBoolean("exportOriginal", exportOriginal); }
    @Override protected void onResume() {
        super.onResume();
        var preferences = UiTheme.preferences(this);
        bpm = preferences.getInt("bpm", 120); beats = preferences.getInt("beats", 4); beatType = preferences.getInt("beatType", 4);
        countIn = preferences.getBoolean("countIn", true); metro = preferences.getBoolean("metro", false); tempo.setText("錄製 " + bpm + " BPM");
        audio.configure(this); applyTheme(); resumed = true; handler.post(refresh);
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(refresh); handler.removeCallbacks(beginRecording);
        if (recorder.active() || counting) finishRecording();
        piano.releaseAll(); setPedal(false); audio.pause(); audio.silenceLive(); audio.metronome(false, bpm, beats); super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); audio.close(); files.shutdown(); super.onDestroy(); }
}
