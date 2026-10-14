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
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private PlaybackController playback;
    private PianoView piano;
    private ScoreLibrary library;
    private String currentId;
    private Button libraryButton;
    private boolean pendingLibraryPlay;
    private static final int LIBRARY = 103;
    private Score score = new Score();
    private UiTheme theme;
    private LinearLayout mainRoot, progressPanel;
    private TextView title, detail, time, range;
    private TextView soundStatus;
    private String lastSoundStatus = "";
    private String lastScoreDetail = "";
    private String lastTime = "";
    private Button record, pedal, tempo, speedButton, importButton, exportButton;
    private ImageButton play, stop, moreButton;
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
            syncPlayback();
            if (!dragging && !score.notes.isEmpty()) progress.setProgress((int) (score.seconds((long) audio.position) * 1000 / Math.max(.001, durationSeconds)));
            updatePlaybackButtons();
            String timeText = formatTime(score.seconds((long) audio.position) / speeds[speedIndex]) + " / " + formatTime(durationSeconds / speeds[speedIndex]);
            if (!timeText.equals(lastTime)) { time.setText(timeText); lastTime = timeText; }
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
            boolean animating = audio.playing || recorder.active() || counting;
            handler.postDelayed(this, animating ? 33 : 200);
        }
    };
    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playback = PlaybackController.acquire(this); audio = playback.audio; library = new ScoreLibrary(this);
        speedIndex = playback.speedIndex;
        var preferences = UiTheme.preferences(this);
        bpm = preferences.getInt("bpm", 120); beats = preferences.getInt("beats", 4); beatType = preferences.getInt("beatType", 4);
        countIn = preferences.getBoolean("countIn", true); metro = preferences.getBoolean("metro", false);
        if (state != null) { exportStep = state.getInt("exportStep", 120); exportOriginal = state.getBoolean("exportOriginal", false); }
        createUi(); applyTheme(); setBusy(true);
        if (playback.score != null) { currentId = playback.id; showScore(playback.score); setBusy(false); return; }
        files.execute(() -> {
            try { library.migrate(); String id = library.selected(); Score saved = id == null ? null : library.load(id); runOnUiThread(() -> { if (isDestroyed()) return; currentId = id; useScore(saved != null ? saved : emptyScore()); setBusy(false); }); }
            catch (Exception e) { runOnUiThread(() -> { setBusy(false); message("無法還原演奏", e.getMessage()); }); }
        });
    }
    private void enterFullscreen() {
        theme.window(this, true);
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
    private ImageButton playbackButton(LinearLayout parent, int icon, String description, Runnable action) {
        ImageButton button = new ImageButton(this); button.setImageResource(icon); button.setContentDescription(description);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48)); params.setMargins(0, 0, dp(7), 0);
        parent.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
    }
    private void speedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×"};
        dialog().setTitle("播放速度").setSingleChoiceItems(labels, speedIndex, (d, which) -> {
            speedIndex = which; playback.speedIndex = which; audio.speed(speeds[which]); speedButton.setText("速度 " + labels[which]); d.dismiss();
        }).setNegativeButton("取消", null).show();
    }
    private void moreMenu() {
        dialog().setTitle("更多選項").setItems(new String[]{"鋼琴音色", "聲部", "匯入摘要", "錄製設定"}, (d, which) -> {
            if (which == 0) soundSettings();
            else if (which == 1) { if (channels.isEmpty()) toast("目前沒有可調整的聲部"); else voiceSettings(); }
            else if (which == 2) { if (score.originalXml == null) toast("此曲目沒有匯入摘要"); else message("匯入摘要", score.importSummary()); }
            else recordingSettings();
        }).show();
    }
    private Button dialogButton(LinearLayout parent, String text, boolean weighted, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setTextSize(14); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(theme.text); button.setBackground(UiTheme.rounded(theme.surface, dp(12)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(weighted ? 0 : -2, dp(46), weighted ? 1 : 0); params.setMargins(0, dp(3), dp(8), dp(3));
        parent.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
    }
    private void selectionStyle(Button button, String text, boolean selected) {
        button.setSelected(selected);
        button.setText((selected ? "✓ " : "") + text);
        button.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextColor(selected ? UiTheme.foreground(theme.primary) : theme.text);
        GradientDrawable bg = UiTheme.rounded(selected ? theme.primary : theme.surface, dp(12));
        if (!selected) bg.setStroke(dp(2), theme.primary);
        button.setBackground(bg);
    }
    private void soundSettings() {
        int selected = PianoSound.selected(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), dp(4));
        String[] tags = {"sampledPiano", "syntheticPiano"};
        final Button[] buttons = new Button[2];
        LinearLayout soundRow = row(); content.addView(soundRow);
        for (int i = 0; i < 2; i++) {
            final int sound = PianoSound.SELECTABLE[i];
            Button button = new Button(this); button.setTextSize(14); button.setAllCaps(false);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
            button.setPadding(dp(8), 0, dp(8), 0); button.setTag(tags[i]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.setMargins(0, dp(3), dp(8), dp(3));
            soundRow.addView(button, params);
            button.setOnClickListener(v -> {
                PianoSound.select(this, sound); SampleBankStore.start(this, sound); audio.configure(this);
                for (int j = 0; j < 2; j++) selectionStyle(buttons[j], PianoSound.name(PianoSound.SELECTABLE[j]), PianoSound.selected(this) == PianoSound.SELECTABLE[j]);
            });
            buttons[i] = button;
        }
        TextView status = label("", 12, theme.muted); status.setPadding(0, dp(8), 0, dp(4)); content.addView(status);
        TextView volumeLabel = label("鋼琴音量", 14, theme.text); content.addView(volumeLabel);
        SeekBar volume = new SeekBar(this); volume.setTag("pianoVolume"); volume.setMax(100);
        volume.setProgress(Math.max(0, Math.min(100, UiTheme.preferences(this).getInt("pianoVolume", 100))));
        volume.setThumbTintList(ColorStateList.valueOf(theme.primary)); volume.setProgressTintList(ColorStateList.valueOf(theme.primary)); volume.setProgressBackgroundTintList(ColorStateList.valueOf(theme.surface));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) { UiTheme.preferences(MainActivity.this).edit().putInt("pianoVolume", value).apply(); volumeLabel.setText("鋼琴音量 " + value + "%"); audio.configure(MainActivity.this); }
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(volume, new LinearLayout.LayoutParams(-1, dp(48)));
        Button credits = dialogButton(content, "音色來源與授權", false, () -> showCredits(false)); credits.setTag("soundCredits");
        volumeLabel.setText("鋼琴音量 " + volume.getProgress() + "%");
        for (int j = 0; j < 2; j++) selectionStyle(buttons[j], PianoSound.name(PianoSound.SELECTABLE[j]), selected == PianoSound.SELECTABLE[j]);
        status.setText(PianoSound.name(selected) + " · " + SampleBankStore.status(selected));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        final boolean[] open = {true};
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!open[0]) return;
                int current = PianoSound.selected(MainActivity.this);
                status.setText(PianoSound.name(current) + " · " + SampleBankStore.status(current));
                handler.postDelayed(this, 400);
            }
        };
        AlertDialog shown = dialog().setTitle("鋼琴音色").setView(scroll).setPositiveButton("關閉", null).create();
        shown.setOnDismissListener(d -> open[0] = false);
        shown.show(); handler.post(tick);
    }
    private CheckBox recordingCheck(LinearLayout parent, String text, boolean checked, java.util.function.Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(this); box.setText(text); box.setTextColor(theme.text); box.setButtonTintList(ColorStateList.valueOf(theme.primary));
        box.setMinHeight(dp(48)); box.setPadding(dp(8), 0, dp(8), 0); box.setChecked(checked);
        box.setOnCheckedChangeListener((v, enabled) -> onChange.accept(enabled)); parent.addView(box); return box;
    }
    private void recordingSettings() {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), dp(4));
        content.addView(label("速度（20–300 BPM）", 14, theme.text));
        EditText input = new EditText(this); input.setTag("recordingBpm"); input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(Integer.toString(bpm)); input.setTextColor(theme.text); input.setBackgroundTintList(ColorStateList.valueOf(theme.primary));
        input.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_DONE) { applyRecordingBpm(input); return true; } return false; });
        input.setOnFocusChangeListener((v, focused) -> { if (!focused) applyRecordingBpm(input); });
        LinearLayout tempoRow = row(); content.addView(tempoRow);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(52), 1); inputParams.setMargins(0, dp(3), dp(8), dp(3));
        tempoRow.addView(input, inputParams);
        dialogButton(tempoRow, "套用速度", false, () -> applyRecordingBpm(input));
        content.addView(label("拍號", 14, theme.text));
        LinearLayout meterRow = row(); content.addView(meterRow);
        String[] names = {"4/4", "3/4", "6/8"}; final Button[] meters = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int meter = i;
            meters[i] = dialogButton(meterRow, names[i], true, () -> {
                beats = meter == 1 ? 3 : meter == 2 ? 6 : 4; beatType = meter == 2 ? 8 : 4;
                UiTheme.preferences(this).edit().putInt("beats", beats).putInt("beatType", beatType).apply();
                for (int j = 0; j < 3; j++) selectionStyle(meters[j], names[j], meter == j);
            });
        }
        int type = UiTheme.preferences(this).getInt("beatType", 4), currentBeats = UiTheme.preferences(this).getInt("beats", 4);
        int currentMeter = type == 8 ? 2 : currentBeats == 3 ? 1 : 0;
        for (int j = 0; j < 3; j++) selectionStyle(meters[j], names[j], currentMeter == j);
        recordingCheck(content, "錄製前倒數一小節", countIn, enabled -> { countIn = enabled; UiTheme.preferences(this).edit().putBoolean("countIn", enabled).apply(); });
        recordingCheck(content, "錄製時播放節拍器", metro, enabled -> { metro = enabled; UiTheme.preferences(this).edit().putBoolean("metro", enabled).apply(); });
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        dialog().setTitle("錄製設定").setView(scroll).setPositiveButton("關閉", null).show();
    }
    private void applyRecordingBpm(EditText input) {
        int value;
        try { value = Integer.parseInt(input.getText().toString()); } catch (Exception e) { input.setError("請輸入 20–300"); return; }
        if (value < 20 || value > 300) { input.setError("請輸入 20–300"); return; }
        input.setError(null); bpm = value; UiTheme.preferences(this).edit().putInt("bpm", value).apply(); tempo.setText("錄製 " + bpm + " BPM");
    }
    private void showCredits(boolean fullLicense) {
        int sound = PianoSound.selected(this);
        if (sound == PianoSound.SYNTHETIC) {
            dialog().setTitle("合成鋼琴").setMessage("由 APP 的正弦查表、泛音與衰減包絡產生，沒有使用外部鋼琴採樣。").setPositiveButton("關閉", null).show(); return;
        }
        String directory = PianoSound.directory(sound);
        String content;
        try (InputStream input = getAssets().open(directory + (fullLicense ? "/LICENSE.txt" : "/ATTRIBUTION.txt"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            content = new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { content = "無法讀取音色授權：" + e.getMessage(); }
        TextView text = new TextView(this); text.setText(content); text.setTextColor(theme.text); text.setTextSize(14); text.setPadding(dp(20), dp(12), dp(20), dp(12)); text.setTextIsSelectable(true);
        text.setLinkTextColor(theme.dark ? UiTheme.blend(theme.primary, android.graphics.Color.WHITE, .35f) : 0xFF175A98);
        android.text.util.Linkify.addLinks(text, android.text.util.Linkify.WEB_URLS);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(theme.background); scroll.addView(text);
        AlertDialog.Builder dialog = dialog();
        dialog.setTitle(fullLicense ? "CC BY 3.0 完整授權" : "音色來源與授權").setView(scroll).setPositiveButton("關閉", null);
        if (!fullLicense) dialog.setNeutralButton("完整授權", (d, which) -> showCredits(true)); dialog.show();
    }
    private void createUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFF2F9F5);
        mainRoot = root; UiTheme.insets(root, dp(10), true);
        LinearLayout header = row();
        TextView brand = label("mini 鋼琴", 23, 0xFF183D35); brand.setTypeface(null, Typeface.BOLD); header.addView(brand);
        soundStatus = label("   採樣音色載入中", 12, 0xFF70847B); soundStatus.setSingleLine(true); soundStatus.setEllipsize(android.text.TextUtils.TruncateAt.END); header.addView(soundStatus, new LinearLayout.LayoutParams(0, -1, 1));
        ImageButton gear = new ImageButton(this); gear.setImageResource(R.drawable.ic_settings); gear.setContentDescription("設定"); gear.setTag("settingsGear");
        gear.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER); gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setOnClickListener(v -> settings()); header.addView(gear, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout actions = row();
        importButton = button("↥ 匯入", actions, this::importScore);
        exportButton = button("↧ 匯出", actions, this::exportScore);
        libraryButton = button("音譜庫", actions, this::openLibrary);
        record = button("● 錄製", actions, this::toggleRecord);
        play = playbackButton(actions, R.drawable.ic_play, "播放", () -> { if (audio.playing) playback.pause(); else playback.play(); });
        stop = playbackButton(actions, R.drawable.ic_stop, "停止", this::stopEverything);
        speedButton = button("速度 " + new String[]{"0.5", "0.75", "1", "1.25", "1.5"}[speedIndex] + "×", actions, this::speedMenu);
        moreButton = new ImageButton(this); moreButton.setImageResource(R.drawable.ic_more); moreButton.setContentDescription("更多選項"); moreButton.setTag("moreMenu");
        moreButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(44), dp(44)); moreParams.setMargins(0, 0, dp(7), 0);
        actions.addView(moreButton, moreParams); moreButton.setOnClickListener(v -> moreMenu());
        scrollRow(root, actions);
        LinearLayout info = row();
        title = label("我的演奏", 15, 0xFF224B40); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        time = label("0:00 / 0:00", 15, 0xFF224B40); time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        info.addView(time);
        progressPanel = new LinearLayout(this); progressPanel.setOrientation(LinearLayout.VERTICAL);
        progressPanel.setBackground(UiTheme.rounded(theme.surface, dp(14)));
        progressPanel.setPadding(dp(12), dp(5), dp(12), dp(7));
        progressPanel.addView(info, new LinearLayout.LayoutParams(-1, dp(26)));
        progress = new SeekBar(this); progress.setMax(1000); progress.setTag("playbackProgress"); styleProgress(progress);
        progressPanel.addView(progress, new LinearLayout.LayoutParams(-1, dp(34)));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2); panelParams.setMargins(0, dp(2), 0, dp(2));
        root.addView(progressPanel, panelParams);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {}
            public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            public void onStopTrackingTouch(SeekBar bar) { audio.seek(score.tick(durationSeconds * bar.getProgress() / 1000)); dragging = false; }
        });
        LinearLayout controls = row();
        tempo = button("錄製 " + bpm + " BPM", controls, this::recordingSettings);
        button("音域 −", controls, () -> { piano.octave(-1); updateRange(); });
        range = label("C3 – B4  ", 12, 0xFF687D73); controls.addView(range);
        button("音域 ＋", controls, () -> { piano.octave(1); updateRange(); });
        button("鍵寬", controls, () -> { widthIndex = (widthIndex + 1) % 3; piano.width(new int[]{8, 16, 24}[widthIndex]); updateRange(); });
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
        libraryButton.setEnabled(idle);
        updatePlaybackButtons(); speedButton.setEnabled(idle); moreButton.setEnabled(idle);
        progress.setEnabled(idle && !score.notes.isEmpty()); record.setEnabled(!busy); tempo.setEnabled(idle);
    }
    private void updatePlaybackButtons() {
        boolean available = !busy && !recorder.active() && !counting && !score.notes.isEmpty();
        play.setEnabled(available);
        play.setImageResource(audio.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(audio.playing ? "暫停" : "播放");
        stop.setEnabled(!busy && (available || recorder.active() || counting));
        play.setAlpha(play.isEnabled() ? 1f : .4f); stop.setAlpha(stop.isEnabled() ? 1f : .4f);
    }
    private void useScore(Score value) {
        playback.load(currentId, value); showScore(value);
    }
    private void showScore(Score value) {
        score = value; scoreLength = value.length(); durationSeconds = score.seconds(scoreLength); channels = score.channels(); title.setText(value.title); lastScoreDetail = "";
        detail.setText(value.notes.size() + " 個音符 · " + channels.size() + " 聲部 · " + value.timeline().size() + " 速度段 · " + value.beats + "/" + value.beatType);
        updateEnabled();
    }
    private void syncPlayback() {
        if (playback.score == null) return;
        if (score != playback.score) { currentId = playback.id; showScore(playback.score); }
        else if (!title.getText().toString().equals(score.title)) title.setText(score.title);
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
        if (!score.notes.isEmpty()) dialog().setTitle("開始新錄製？").setMessage("完成後會新增至音譜庫，既有音譜會保留。").setPositiveButton("開始", (d, w) -> prepareRecording()).setNegativeButton("取消", null).show();
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
            if (!recorded.notes.isEmpty()) addRecording(recorded);
            else { useScore(score); toast("沒有錄到音符，未新增音譜"); }
        } else if (wasCounting) useScore(score);
        record.setText("● 錄製"); styleButton(record); updateEnabled();
    }
    private void stopEverything() { if (recorder.active() || counting) finishRecording(); audio.stop(); piano.releaseAll(); setPedal(false); }
    private void save(Score value) {
        String id = currentId; if (id == null) return;
        Score snapshot = value.copy(); files.execute(() -> { try { library.update(id, snapshot); } catch (Exception e) { runOnUiThread(() -> toast("演奏儲存失敗：" + e.getMessage())); } });
    }
    private Score emptyScore() { Score value = new Score(); value.title = "尚未選取音譜"; return value; }
    private void addRecording(Score recorded) {
        setBusy(true);
        files.execute(() -> {
            try { String id = library.add(recorded.copy(), "錄製"); runOnUiThread(() -> { if (isDestroyed()) return; currentId = id; useScore(recorded); setBusy(false); toast("錄製已加入音譜庫"); }); }
            catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; currentId = null; useScore(recorded); setBusy(false); message("錄製儲存失敗，請匯出保留", e.getMessage()); }); }
        });
    }
    private void openLibrary() {
        if (busy || recorder.active() || counting) return;
        setBusy(true);
        files.execute(() -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            setBusy(false); startActivityForResult(new Intent(this, ScoreLibraryActivity.class), LIBRARY);
        }));
    }
    private void reloadLibrary(boolean playNow) {
        setBusy(true);
        files.execute(() -> {
            try { String id = library.selected(); Score selected = id == null ? emptyScore() : library.load(id);
                runOnUiThread(() -> { if (isDestroyed()) return;
                    if (playNow || !java.util.Objects.equals(id, playback.id)) { currentId = id; useScore(selected); }
                    else if (playback.score != null) { playback.score.title = selected.title; syncPlayback(); }
                    setBusy(false); if (playNow && !selected.notes.isEmpty()) { if (resumed) playback.play(); else pendingLibraryPlay = true; }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; currentId = null; useScore(emptyScore()); setBusy(false); message("無法載入音譜", e.getMessage()); }); }
        });
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
            ImageButton image = (ImageButton) view; image.setImageTintList(ColorStateList.valueOf(theme.text));
            image.setBackground("settingsGear".equals(view.getTag()) ? null : UiTheme.rounded(theme.surface, dp(14)));
        } else if (view instanceof Button) styleButton((Button) view);
        else if (view instanceof TextView) ((TextView) view).setTextColor("muted".equals(view.getTag()) ? theme.muted : theme.text);
        if (view instanceof SeekBar) {
            SeekBar bar = (SeekBar) view;
            if ("playbackProgress".equals(view.getTag())) styleProgress(bar);
            else { bar.setThumbTintList(ColorStateList.valueOf(theme.primary)); bar.setProgressTintList(ColorStateList.valueOf(theme.primary)); bar.setProgressBackgroundTintList(ColorStateList.valueOf(theme.surface)); }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) tint(group.getChildAt(i));
        }
    }
    private void styleProgress(SeekBar bar) {
        GradientDrawable track = UiTheme.rounded(UiTheme.blend(theme.surface, theme.primary, .22f), dp(5));
        GradientDrawable fill = UiTheme.rounded(theme.primary, dp(5));
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{track, clip});
        layer.setId(0, android.R.id.background); layer.setId(1, android.R.id.progress);
        bar.setProgressTintList(null); bar.setProgressBackgroundTintList(null); bar.setProgressDrawable(layer);
        GradientDrawable thumb = new GradientDrawable(); thumb.setSize(0, 0);
        bar.setThumb(thumb); bar.setThumbOffset(0);
        bar.setPadding(dp(2), dp(4), dp(2), dp(4));
    }
    private void applyTheme() {
        theme = new UiTheme(this); mainRoot.setBackgroundColor(theme.background);
        if (progressPanel != null) progressPanel.setBackground(UiTheme.rounded(theme.surface, dp(14)));
        tint(mainRoot); piano.theme(theme); theme.window(this, true);
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
        if (request == LIBRARY) {
            if (result == RESULT_OK && data != null && data.getBooleanExtra("import", false)) { reloadLibrary(false); importScore(); }
            else { String id = data == null ? null : data.getStringExtra("playId"); if (id != null) library.select(id); reloadLibrary(id != null); }
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); setBusy(true);
        if (request == IMPORT) {
            files.execute(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException("無法開啟檔案");
                    Score imported = MusicXml.read(input); String id = library.add(imported, "匯入");
                    runOnUiThread(() -> { if (isDestroyed()) return; currentId = id; useScore(imported); setBusy(false); message("匯入摘要", imported.importSummary()); });
                } catch (Exception e) { runOnUiThread(() -> { setBusy(false); message("匯入失敗", e.getMessage()); }); }
            });
        } else if (request == EXPORT) {
            Score snapshot = score.copy(); boolean original = exportOriginal; int step = exportStep;
            files.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException("無法寫入檔案");
                    Score value = snapshot;
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
        audio.configure(this); syncPlayback(); applyTheme(); resumed = true; handler.post(refresh);
        if (pendingLibraryPlay) { pendingLibraryPlay = false; playback.play(); }
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(refresh); handler.removeCallbacks(beginRecording);
        if (recorder.active() || counting) finishRecording();
        piano.releaseAll(); setPedal(false); audio.silenceLive(); audio.metronome(false, bpm, beats); super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); playback.release(); files.shutdown(); super.onDestroy(); }
}
