package com.minipiano.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private UiTheme theme;
    private LinearLayout root, customRow;
    private final List<TextView> labels = new ArrayList<>();
    private final List<Button> ordinary = new ArrayList<>();
    private final Button[] modes = new Button[3], colors = new Button[6];
    private TextView swatch, hueLabel, customLabel;
    private HueSlider hueSlider;
    private boolean custom;

    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        custom = UiTheme.preferences(this).getBoolean("customTheme", false);
        createUi();
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

        setContentView(root);
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
        customLabel.setText(custom ? "✓ 自訂色彩" : "自訂色彩"); customLabel.setTypeface(Typeface.create("sans-serif", custom ? Typeface.BOLD : Typeface.NORMAL));
        customRow.setBackground(UiTheme.rounded(custom ? theme.primary : theme.surface, dp(14))); hueLabel.setTextColor(custom ? theme.onPrimary : theme.text);
        hueLabel.setText(theme.hue + "°"); hueSlider.hue(theme.hue); hueSlider.border(custom ? theme.onPrimary : theme.text);
        swatch.setBackground(UiTheme.rounded(theme.primary, dp(18))); swatch.setContentDescription("目前色相 " + theme.hue + " 度");
        // Keep the round swatch visible even when the custom row uses the same primary.
        ((android.graphics.drawable.GradientDrawable) swatch.getBackground()).setStroke(dp(2), custom ? theme.onPrimary : theme.text);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && theme != null) theme.window(this, false); }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); applyTheme(); }
    @Override protected void onResume() {
        super.onResume();
        if (root != null) applyTheme();
    }
}
