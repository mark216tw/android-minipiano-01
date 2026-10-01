package com.minipiano.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScoreLibraryActivity extends Activity {
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private ScoreLibrary library;
    private UiTheme theme;
    private LinearLayout body;
    private boolean busy;
    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme); super.onCreate(state);
        library = new ScoreLibrary(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(theme.background);
        UiTheme.insets(root, dp(16), false);
        LinearLayout header = new LinearLayout(this);
        button(header, "‹ 返回", this::finish); TextView title = text("音譜庫", 23); header.addView(title); root.addView(header);
        button(root, "↥ 匯入音譜", () -> { setResult(RESULT_OK, new Intent().putExtra("import", true)); finish(); });
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); theme.window(this, false); refresh();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(theme.text); view.setPadding(0, dp(8), 0, dp(8)); return view; }
    private void button(LinearLayout parent, String value, Runnable action) {
        Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setTextColor(theme.text);
        parent.addView(button); button.setOnClickListener(v -> { if (!busy) action.run(); });
    }
    private interface Work { void run() throws Exception; }
    private void perform(Work work, Runnable success) {
        if (busy) return; busy = true;
        files.execute(() -> {
            try { work.run(); runOnUiThread(() -> { if (isDestroyed()) return; busy = false; success.run(); }); }
            catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; busy = false; Toast.makeText(this, "操作失敗：" + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
        });
    }
    private void refresh() {
        busy = true;
        files.execute(() -> {
            try {
                var entries = library.list(); runOnUiThread(() -> {
                    if (isDestroyed()) return; busy = false; body.removeAllViews();
                    if (entries.isEmpty()) body.addView(text("尚無音譜。匯入音譜或返回主畫面錄製演奏。", 16));
                    for (ScoreLibrary.Entry entry : entries) {
                        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12), dp(8), dp(12), dp(12));
                        card.addView(text(entry.title, 18));
                        String date = entry.created == 0 ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).format(new Date(entry.created));
                        card.addView(text(entry.source + " · " + String.format(Locale.ROOT, "%d:%02d", (int) entry.seconds / 60, (int) entry.seconds % 60) + " · " + date, 12));
                        LinearLayout actions = new LinearLayout(this); card.addView(actions);
                        button(actions, "▶ 播放", () -> perform(() -> library.load(entry.id), () -> {
                            setResult(RESULT_OK, new Intent().putExtra("playId", entry.id)); finish();
                        }));
                        button(actions, "⋮ 更多", () -> new AlertDialog.Builder(this).setTitle(entry.title).setItems(new String[]{"改名", "刪除"}, (d, which) -> { if (which == 0) rename(entry); else delete(entry); }).show());
                        body.addView(card);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { busy = false; body.removeAllViews(); body.addView(text("無法讀取音譜庫：" + e.getMessage(), 16)); } }); }
        });
    }
    private void rename(ScoreLibrary.Entry entry) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(entry.title); input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("改名").setView(input).setNegativeButton("取消", null).setPositiveButton("儲存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = input.getText().toString().trim();
            if (title.isEmpty() || title.length() > 80) { input.setError("名稱需為 1 至 80 字"); return; }
            perform(() -> library.rename(entry.id, title), () -> {
                PlaybackController playback = PlaybackController.peek(); if (playback != null) playback.rename(entry.id, title);
                dialog.dismiss(); refresh();
            });
        })); dialog.show();
    }
    private void delete(ScoreLibrary.Entry entry) {
        new AlertDialog.Builder(this).setTitle("刪除音譜？").setMessage("要刪除「" + entry.title + "」嗎？刪除後無法復原。")
            .setNegativeButton("取消", null).setPositiveButton("刪除", (d, w) -> perform(() -> library.delete(entry.id), () -> {
                PlaybackController playback = PlaybackController.peek(); if (playback != null) playback.deleted(entry.id);
                refresh();
            })).show();
    }
    @Override protected void onDestroy() { files.shutdown(); super.onDestroy(); }
}
