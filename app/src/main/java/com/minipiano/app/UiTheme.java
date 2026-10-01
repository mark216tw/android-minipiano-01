package com.minipiano.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

final class UiTheme {
    static final String[] MODE_NAMES = {"系統", "淺色", "深色"};
    static final String[] COLOR_NAMES = {"薄荷綠", "晴空藍", "葡萄紫", "莓果粉", "活力橘", "陽光黃"};
    static final int[] HUES = {160, 205, 265, 330, 25, 48};
    final boolean dark;
    final int mode, hue, primary, onPrimary, background, surface, text, muted;
    UiTheme(Context context) {
        SharedPreferences preferences = preferences(context);
        mode = Math.max(0, Math.min(2, preferences.getInt("displayMode", 0)));
        hue = Math.max(0, Math.min(359, preferences.getInt("themeHue", HUES[0])));
        dark = mode == 2 || (mode == 0 && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        primary = color(hue); onPrimary = foreground(primary);
        background = dark ? 0xFF121B22 : blend(Color.WHITE, primary, .045f);
        surface = dark ? blend(0xFF202D36, primary, .10f) : blend(Color.WHITE, primary, .13f);
        text = dark ? 0xFFF0F6F6 : 0xFF1E3038;
        muted = dark ? 0xFFB2C6CC : 0xFF52696D;
    }
    static SharedPreferences preferences(Context context) { return context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE); }
    static int color(int hue) { return Color.HSVToColor(new float[]{hue, .70f, .90f}); }
    static int foreground(int color) { return contrast(color, Color.BLACK) >= contrast(color, Color.WHITE) ? Color.BLACK : Color.WHITE; }
    static double contrast(int a, int b) { double x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); }
    private static double luminance(int color) { return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color)); }
    private static double linear(int value) { double x = value / 255.0; return x <= .04045 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4); }
    static int blend(int a, int b, float ratio) { return Color.rgb(Math.round(Color.red(a) * (1 - ratio) + Color.red(b) * ratio), Math.round(Color.green(a) * (1 - ratio) + Color.green(b) * ratio), Math.round(Color.blue(a) * (1 - ratio) + Color.blue(b) * ratio)); }
    static GradientDrawable rounded(int color, float radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(radius); return bg; }
    void window(Activity activity, boolean fullscreen) {
        View decor = activity.getWindow().getDecorView();
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 29) {
            activity.getWindow().setStatusBarContrastEnforced(false);
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (fullscreen) controller.hide(WindowInsets.Type.systemBars()); else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (fullscreen) flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            if (!dark) { flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if (Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; }
            decor.setSystemUiVisibility(flags);
        }
    }
    static void insets(View root, int padding, boolean fullscreen) {
        root.setPadding(padding, padding, padding, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = 0, right = 0, top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                int types = WindowInsets.Type.displayCutout();
                if (!fullscreen) types |= WindowInsets.Type.systemBars() | WindowInsets.Type.ime();
                android.graphics.Insets i = insets.getInsets(types); left = i.left; right = i.right; top = i.top; bottom = i.bottom;
            } else {
                if (!fullscreen) { left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight(); top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    android.view.DisplayCutout c = insets.getDisplayCutout(); left = Math.max(left, c.getSafeInsetLeft()); right = Math.max(right, c.getSafeInsetRight()); top = Math.max(top, c.getSafeInsetTop()); bottom = Math.max(bottom, c.getSafeInsetBottom());
                }
            }
            view.setPadding(padding + left, padding + top, padding + right, padding + bottom); return insets;
        });
        root.requestApplyInsets();
    }
}
