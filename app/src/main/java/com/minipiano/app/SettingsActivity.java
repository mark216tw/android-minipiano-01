package com.minipiano.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private UiTheme theme;
    private LinearLayout root, customRow;
    private final List<TextView> labels = new ArrayList<>();
    private final List<Button> ordinary = new ArrayList<>();
    private final List<CheckBox> checks = new ArrayList<>();
    private final Button[] modes = new Button[3], colors = new Button[6], meters = new Button[3];
    private final Button[] sounds = new Button[3];
    private TextView swatch, hueLabel, customLabel;
    private TextView soundStatus, volumeLabel;
    private SeekBar pianoVolume;
    private HueSlider hueSlider;
    private EditText bpmInput;
    private boolean custom;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable soundRefresh = new Runnable() {
        @Override public void run() { updateSoundStatus(); handler.postDelayed(this, 250); }
    };

    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        custom = UiTheme.preferences(this).getBoolean("customTheme", false);
        createUi();
        if (state != null && state.containsKey("draftBpm")) bpmInput.setText(state.getString("draftBpm"));
        applyTheme();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private TextView text(String value, int size) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(size); labels.add(label); return label;
    }
    private void heading(LinearLayout parent, String name, String description) {
        TextView heading = text(name, 19); heading.setTypeface(null, Typeface.BOLD); heading.setPadding(0, dp(22), 0, dp(8)); parent.addView(heading);
        if (description != null) { TextView detail = text(description, 12); detail.setTag("muted"); detail.setPadding(0, 0, 0, dp(12)); parent.addView(detail); }
    }
    private Button button(LinearLayout row, String title, Runnable action, boolean weighted) {
        Button button = new Button(this); button.setText(title); button.setTextSize(14); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(weighted ? 0 : -2, dp(48), weighted ? 1 : 0); params.setMargins(0, dp(3), dp(8), dp(3)); row.addView(button, params);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private void createUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); UiTheme.insets(root, dp(16), false);
        LinearLayout header = row();
        Button back = button(header, "‹", this::finish, false); back.setContentDescription("返回主畫面"); back.setTextSize(30); back.setTag("settingsBack"); ordinary.add(back);
        TextView title = text("設定", 23); title.setTypeface(null, Typeface.BOLD); header.addView(title); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(4), 0, dp(4), dp(24)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        heading(body, "顯示模式", "選擇後立即套用；系統模式會跟隨手機的明暗設定。");
        LinearLayout modeRow = row(); body.addView(modeRow);
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            modes[i] = button(modeRow, UiTheme.MODE_NAMES[i], () -> { UiTheme.preferences(this).edit().putInt("displayMode", mode).apply(); applyTheme(); }, true);
            modes[i].setTag("displayMode" + i);
        }
        heading(body, "主題色彩", "六款活潑配色，或拖動下方 Hue 滑桿自訂色相。");
        for (int r = 0; r < 2; r++) {
            LinearLayout colorRow = row(); body.addView(colorRow);
            for (int c = 0; c < 3; c++) {
                int index = r * 3 + c;
                colors[index] = button(colorRow, UiTheme.COLOR_NAMES[index], () -> chooseHue(UiTheme.HUES[index], false), true);
                colors[index].setTag("themeColor" + index);
            }
        }
        customLabel = text("自訂色彩", 14); customLabel.setPadding(0, dp(14), 0, dp(5)); body.addView(customLabel);
        customRow = row(); customRow.setPadding(dp(8), dp(2), dp(8), dp(2)); body.addView(customRow);
        swatch = new TextView(this); swatch.setTag("colorSwatch"); swatch.setContentDescription("目前自訂色彩"); customRow.addView(swatch, new LinearLayout.LayoutParams(dp(36), dp(36)));
        hueSlider = new HueSlider(this, hue -> chooseHue(hue, true)); hueSlider.setTag("hueSlider"); customRow.addView(hueSlider, new LinearLayout.LayoutParams(0, dp(52), 1));
        hueLabel = text("", 12); hueLabel.setGravity(Gravity.CENTER); customRow.addView(hueLabel, new LinearLayout.LayoutParams(dp(60), -2));

        heading(body, "鋼琴音色", "三種音色已平衡響度；選用後按需載入，音量滑桿統一調整。");
        LinearLayout soundRow = row(); body.addView(soundRow);
        String[] tags = {"sampledPiano", "uprightPiano", "syntheticPiano"};
        for (int i = 0; i < sounds.length; i++) {
            final int sound = i;
            sounds[i] = button(soundRow, PianoSound.NAMES[i], () -> {
                PianoSound.select(this, sound); SampleBankStore.start(this, sound); applyTheme();
            }, true);
            sounds[i].setTag(tags[i]);
        }
        soundStatus = text("", 12); soundStatus.setTag("sampleLoadStatus"); soundStatus.setPadding(0, dp(8), 0, dp(12)); body.addView(soundStatus);
        volumeLabel = text("鋼琴音量", 14); body.addView(volumeLabel);
        pianoVolume = new SeekBar(this); pianoVolume.setTag("pianoVolume"); pianoVolume.setMax(100); pianoVolume.setProgress(Math.max(0, Math.min(100, UiTheme.preferences(this).getInt("pianoVolume", 100))));
        body.addView(pianoVolume, new LinearLayout.LayoutParams(-1, dp(48)));
        pianoVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) UiTheme.preferences(SettingsActivity.this).edit().putInt("pianoVolume", value).apply(); volumeLabel.setText("鋼琴音量 " + value + "%");
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout creditsRow = row(); body.addView(creditsRow);
        ordinary.add(button(creditsRow, "音色來源與授權", () -> showCredits(false), false));

        heading(body, "錄製設定", "調整新錄製的速度與拍號，播放中的樂曲仍使用自己的速度。");
        TextView speed = text("速度（20–300 BPM）", 14); body.addView(speed);
        LinearLayout tempoRow = row(); body.addView(tempoRow);
        bpmInput = new EditText(this); bpmInput.setTag("recordingBpm"); bpmInput.setSingleLine(true); bpmInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        bpmInput.setImeOptions(EditorInfo.IME_ACTION_DONE); bpmInput.setText(Integer.toString(UiTheme.preferences(this).getInt("bpm", 120)));
        tempoRow.addView(bpmInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button save = button(tempoRow, "套用速度", this::saveBpm, false); ordinary.add(save);
        bpmInput.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_DONE) { saveBpm(); return true; } return false; });
        bpmInput.setOnFocusChangeListener((v, focused) -> { if (!focused) saveBpm(); });
        LinearLayout meterRow = row(); body.addView(meterRow); String[] names = {"4/4", "3/4", "6/8"};
        for (int i = 0; i < 3; i++) {
            final int meter = i;
            meters[i] = button(meterRow, names[i], () -> {
                UiTheme.preferences(this).edit().putInt("beats", meter == 1 ? 3 : meter == 2 ? 6 : 4).putInt("beatType", meter == 2 ? 8 : 4).apply(); applyTheme();
            }, true);
        }
        checkbox(body, "錄製前倒數一小節", "countIn", true);
        checkbox(body, "錄製時播放節拍器", "metro", false);
        setContentView(root);
    }
    private void checkbox(LinearLayout parent, String label, String key, boolean fallback) {
        CheckBox check = new CheckBox(this); check.setText(label); check.setMinHeight(dp(48)); check.setChecked(UiTheme.preferences(this).getBoolean(key, fallback));
        check.setOnCheckedChangeListener((button, enabled) -> { UiTheme.preferences(this).edit().putBoolean(key, enabled).apply(); applyTheme(); });
        checks.add(check); parent.addView(check);
    }
    private void saveBpm() {
        int value;
        try { value = Integer.parseInt(bpmInput.getText().toString()); } catch (Exception e) { bpmInput.setError("請輸入 20–300"); return; }
        if (value < 20 || value > 300) { bpmInput.setError("請輸入 20–300"); return; }
        UiTheme.preferences(this).edit().putInt("bpm", value).apply(); bpmInput.setError(null);
    }
    private void chooseHue(int hue, boolean custom) {
        this.custom = custom;
        UiTheme.preferences(this).edit().putInt("themeHue", hue).putBoolean("customTheme", custom).apply(); applyTheme();
    }
    private void selection(Button button, String text, boolean selected, int color) {
        button.setSelected(selected); button.setText((selected ? "✓ " : "") + text);
        button.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextColor(selected ? UiTheme.foreground(color) : theme.text);
        android.graphics.drawable.GradientDrawable bg = UiTheme.rounded(selected ? color : theme.surface, dp(12));
        if (!selected) bg.setStroke(dp(2), color);
        button.setBackground(bg);
    }
    private void applyTheme() {
        theme = new UiTheme(this); root.setBackgroundColor(theme.background); theme.window(this, false);
        for (TextView label : labels) label.setTextColor("muted".equals(label.getTag()) ? theme.muted : theme.text);
        for (Button button : ordinary) { button.setTextColor(theme.text); button.setBackground(UiTheme.rounded(theme.surface, dp(12))); }
        for (int i = 0; i < modes.length; i++) selection(modes[i], UiTheme.MODE_NAMES[i], theme.mode == i, theme.primary);
        for (int i = 0; i < colors.length; i++) selection(colors[i], UiTheme.COLOR_NAMES[i], !custom && theme.hue == UiTheme.HUES[i], UiTheme.color(UiTheme.HUES[i]));
        int sound = PianoSound.selected(this);
        for (int i = 0; i < sounds.length; i++) selection(sounds[i], PianoSound.NAMES[i], sound == i, theme.primary);
        pianoVolume.setThumbTintList(ColorStateList.valueOf(theme.primary)); pianoVolume.setProgressTintList(ColorStateList.valueOf(theme.primary)); pianoVolume.setProgressBackgroundTintList(ColorStateList.valueOf(theme.surface));
        volumeLabel.setText("鋼琴音量 " + pianoVolume.getProgress() + "%"); updateSoundStatus();
        customLabel.setText(custom ? "✓ 自訂色彩" : "自訂色彩"); customLabel.setTypeface(Typeface.create("sans-serif", custom ? Typeface.BOLD : Typeface.NORMAL));
        customRow.setBackground(UiTheme.rounded(custom ? theme.primary : theme.surface, dp(14))); hueLabel.setTextColor(custom ? theme.onPrimary : theme.text);
        hueLabel.setText(theme.hue + "°"); hueSlider.hue(theme.hue); hueSlider.border(custom ? theme.onPrimary : theme.text);
        swatch.setBackground(UiTheme.rounded(theme.primary, dp(18))); swatch.setContentDescription("目前色相 " + theme.hue + " 度");
        // Keep the round swatch visible even when the custom row uses the same primary.
        ((android.graphics.drawable.GradientDrawable) swatch.getBackground()).setStroke(dp(2), custom ? theme.onPrimary : theme.text);
        int beats = UiTheme.preferences(this).getInt("beats", 4), type = UiTheme.preferences(this).getInt("beatType", 4);
        int meter = type == 8 ? 2 : beats == 3 ? 1 : 0;
        String[] names = {"4/4", "3/4", "6/8"}; for (int i = 0; i < meters.length; i++) selection(meters[i], names[i], meter == i, theme.primary);
        bpmInput.setTextColor(theme.text); bpmInput.setBackgroundTintList(ColorStateList.valueOf(theme.primary));
        int checkboxBackground = theme.dark ? 0xFF202D36 : 0xFFFFFFFF;
        for (CheckBox check : checks) {
            check.setTextColor(theme.text); check.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            check.setPadding(dp(8), 0, dp(8), 0); check.setBackground(UiTheme.rounded(checkboxBackground, dp(10)));
            android.graphics.drawable.GradientDrawable selected = UiTheme.rounded(theme.primary, dp(5)); selected.setSize(dp(24), dp(24));
            android.graphics.drawable.Drawable mark = getDrawable(R.drawable.ic_check).mutate(); mark.setTint(theme.onPrimary);
            android.graphics.drawable.LayerDrawable checked = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{selected, mark});
            android.graphics.drawable.GradientDrawable unchecked = UiTheme.rounded(checkboxBackground, dp(5)); unchecked.setSize(dp(24), dp(24)); unchecked.setStroke(dp(2), theme.muted);
            android.graphics.drawable.StateListDrawable indicator = new android.graphics.drawable.StateListDrawable();
            indicator.addState(new int[]{android.R.attr.state_checked}, checked); indicator.addState(new int[]{}, unchecked);
            check.setButtonTintList(null); check.setButtonDrawable(indicator); check.setCompoundDrawablePadding(dp(8));
        }
    }
    private void updateSoundStatus() {
        if (soundStatus == null) return;
        int sound = PianoSound.selected(this);
        soundStatus.setText(PianoSound.NAMES[sound] + " · " + SampleBankStore.status(sound)); soundStatus.setTextColor(theme.muted);
    }
    private void showCredits(boolean fullLicense) {
        int sound = PianoSound.selected(this);
        if (sound == PianoSound.SYNTHETIC) {
            new android.app.AlertDialog.Builder(new android.view.ContextThemeWrapper(this, theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme))
                .setTitle("合成鋼琴").setMessage("由 APP 的正弦查表、泛音與衰減包絡產生，沒有使用外部鋼琴採樣。").setPositiveButton("關閉", null).show(); return;
        }
        String directory = PianoSound.DIRECTORIES[sound];
        String content;
        try (java.io.InputStream input = getAssets().open(directory + (fullLicense ? "/LICENSE.txt" : "/ATTRIBUTION.txt"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            content = new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { content = "無法讀取音色授權：" + e.getMessage(); }
        TextView text = new TextView(this); text.setText(content); text.setTextColor(theme.text); text.setTextSize(14); text.setPadding(dp(20), dp(12), dp(20), dp(12)); text.setTextIsSelectable(true);
        text.setLinkTextColor(theme.dark ? UiTheme.blend(theme.primary, android.graphics.Color.WHITE, .35f) : 0xFF175A98);
        android.text.util.Linkify.addLinks(text, android.text.util.Linkify.WEB_URLS);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(theme.background); scroll.addView(text);
        android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(new android.view.ContextThemeWrapper(this, theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme));
        dialog.setTitle(fullLicense ? (sound == PianoSound.UPRIGHT ? "CC0 1.0 完整授權" : "CC BY 3.0 完整授權") : "音色來源與授權").setView(scroll).setPositiveButton("關閉", null);
        if (!fullLicense) dialog.setNeutralButton("完整授權", (d, which) -> showCredits(true)); dialog.show();
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && theme != null) theme.window(this, false); }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); applyTheme(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("draftBpm", bpmInput.getText().toString()); }
    @Override protected void onResume() {
        super.onResume(); SampleBankStore.start(this);
        if (root != null) applyTheme(); handler.removeCallbacks(soundRefresh); handler.post(soundRefresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(soundRefresh); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
