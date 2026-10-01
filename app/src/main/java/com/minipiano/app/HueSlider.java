package com.minipiano.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

final class HueSlider extends View {
    interface Listener { void changed(int hue); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private Shader gradient;
    private int hue = 160, border = Color.BLACK;
    HueSlider(Context context, Listener listener) {
        super(context); this.listener = listener; setFocusable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); describe();
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    void hue(int value) { hue = Math.max(0, Math.min(359, value)); describe(); invalidate(); }
    int hue() { return hue; }
    void border(int value) { border = value; invalidate(); }
    private void describe() { setContentDescription("自訂主題色相 " + hue + " 度"); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int[] colors = new int[7]; for (int i = 0; i < colors.length; i++) colors[i] = UiTheme.color(i * 60);
        gradient = new LinearGradient(dp(16), 0, Math.max(dp(17), w - dp(16)), 0, colors, null, Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas) {
        float center = getHeight() / 2f, start = dp(16), end = Math.max(start + 1, getWidth() - start);
        paint.setShader(gradient); canvas.drawRoundRect(start, center - dp(9), end, center + dp(9), dp(9), dp(9), paint); paint.setShader(null);
        float x = start + (end - start) * hue / 359f;
        paint.setColor(border); canvas.drawCircle(x, center, dp(14), paint);
        paint.setColor(UiTheme.color(hue)); canvas.drawCircle(x, center, dp(10), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(UiTheme.foreground(UiTheme.color(hue))); canvas.drawCircle(x, center, dp(10), paint); paint.setStyle(Paint.Style.FILL);
    }
    private void select(float x) {
        float start = dp(16), span = Math.max(1, getWidth() - 2 * start);
        hue(Math.round(Math.max(0, Math.min(1, (x - start) / span)) * 359)); listener.changed(hue);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: getParent().requestDisallowInterceptTouchEvent(true); select(event.getX()); return true;
            case MotionEvent.ACTION_MOVE: select(event.getX()); return true;
            case MotionEvent.ACTION_UP: select(event.getX()); getParent().requestDisallowInterceptTouchEvent(false); performClick(); return true;
            case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return false;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) { hue(hue + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)); listener.changed(hue); return true; }
        return super.onKeyDown(code, event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, 359, hue));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS); info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action, android.os.Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            hue(hue + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 10 : -10)); listener.changed(hue); return true;
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
            hue(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE))); listener.changed(hue); return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
