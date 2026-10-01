package com.minipiano.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

final class PianoView extends View {
    interface Listener { void down(int pointer, int pitch); void up(int pointer); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Key> keys = new ArrayList<>();
    private final SparseIntArray fingers = new SparseIntArray();
    private final Listener listener;
    private final PianoAudio audio;
    private final RectF pulseBounds = new RectF();
    private static final long ATTACK_FLASH_MS = 140;
    private int base = 48, whites = 14;
    private int manualColor = 0xFF6CDFBE, whiteColor = 0xFFFFFDF5, blackColor = 0xFF22343C;
    void theme(UiTheme theme) { manualColor = theme.primary; whiteColor = theme.dark ? 0xFFD3DFDD : 0xFFFFFDF5; blackColor = theme.dark ? 0xFF101A22 : 0xFF22343C; invalidate(); }
    private static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11};
    private static final String[] NAMES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final class Key {
        final int pitch; final boolean black; final RectF bounds;
        Key(int pitch, boolean black, RectF bounds) { this.pitch = pitch; this.black = black; this.bounds = bounds; }
    }
    PianoView(Context context, Listener listener, PianoAudio audio) {
        super(context); this.listener = listener; this.audio = audio;
        setContentDescription("多指鋼琴鍵盤，可滑奏"); setFocusable(true);
    }
    int base() { return base; }
    void octave(int direction) { releaseAll(); base = Math.max(24, Math.min(108 - whites / 7 * 12, base + direction * 12)); rebuild(); invalidate(); }
    void width(int whiteCount) { releaseAll(); whites = whiteCount; base = Math.min(base, 108 - whites / 7 * 12); rebuild(); invalidate(); }
    void releaseAll() { for (int i = 0; i < fingers.size(); i++) listener.up(fingers.keyAt(i)); fingers.clear(); invalidate(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { rebuild(); }
    private void rebuild() {
        keys.clear(); float width = getWidth() / (float) whites;
        for (int i = 0; i < whites; i++) {
            int degree = i % 7, pitch = base + (i / 7) * 12 + WHITE[degree];
            keys.add(new Key(pitch, false, new RectF(i * width + 1, 1, (i + 1) * width - 1, getHeight() - 2)));
        }
        for (int i = 0; i < whites - 1; i++) {
            int degree = i % 7;
            if (degree == 2 || degree == 6) continue;
            int pitch = base + (i / 7) * 12 + WHITE[degree] + 1;
            keys.add(new Key(pitch, true, new RectF((i + 1) * width - width * .31f, 0, (i + 1) * width + width * .31f, getHeight() * .61f)));
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            setSystemGestureExclusionRects(java.util.List.of(new android.graphics.Rect(0, 0, getWidth(), getHeight())));
        }
    }
    private boolean pressed(int pitch) { for (int i = 0; i < fingers.size(); i++) if (fingers.valueAt(i) == pitch) return true; return false; }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(33, 51, 60));
        long now = SystemClock.uptimeMillis();
        for (Key key : keys) {
            long attackTime = audio.attackTime(key.pitch), age = now - attackTime;
            float pulse = attackTime > 0 && age >= 0 && age < ATTACK_FLASH_MS ? 1 - age / (float) ATTACK_FLASH_MS : 0;
            boolean pressed = pressed(key.pitch), lit = audio.lit(key.pitch) || pulse > 0;
            paint.setColor(pressed ? manualColor : lit ? 0xFFFFD17C : key.black ? blackColor : whiteColor);
            canvas.drawRoundRect(key.bounds, key.black ? 7 : 5, key.black ? 7 : 5, paint);
            if (key.black) {
                paint.setColor(pressed || lit ? 0x55334444 : 0xFF48616D);
                canvas.drawRoundRect(key.bounds.left + 5, 6, key.bounds.right - 5, key.bounds.bottom - 12, 4, 4, paint);
            }
            if (pulse > 0) {
                // Each onset restarts the flash/ripple, even when the key never goes dark.
                paint.setColor(Color.argb((int) (pulse * 170), 255, 248, 211));
                canvas.drawRoundRect(key.bounds, key.black ? 7 : 5, key.black ? 7 : 5, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
                paint.setColor(Color.argb((int) (pulse * 255), 242, 147, 35));
                pulseBounds.set(key.bounds); pulseBounds.inset(paint.getStrokeWidth(), paint.getStrokeWidth());
                canvas.drawRoundRect(pulseBounds, key.black ? 7 : 5, key.black ? 7 : 5, paint);
                float radius = Math.min(key.bounds.width() * .27f, 16 * getResources().getDisplayMetrics().density) * (1 - pulse * .7f);
                canvas.drawCircle(key.bounds.centerX(), key.bounds.bottom - (key.black ? 22 : 44) * getResources().getDisplayMetrics().density, radius, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (!key.black) {
                int pc = key.pitch % 12, degree = 0; for (int j = 0; j < 7; j++) if (WHITE[j] == pc) degree = j;
                paint.setColor(pressed ? UiTheme.foreground(manualColor) : 0xFF526963); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(Math.min(17 * getResources().getDisplayMetrics().scaledDensity, key.bounds.width() * .30f));
                canvas.drawText(NAMES[degree] + (key.pitch / 12 - 1), key.bounds.centerX(), key.bounds.bottom - 15, paint);
            }
        }
    }
    private int hit(float x, float y) {
        for (int i = keys.size() - 1; i >= 0; i--) if (keys.get(i).bounds.contains(x, y)) return keys.get(i).pitch;
        return -1;
    }
    private void move(int id, float x, float y) {
        int pitch = hit(x, y), old = fingers.get(id, -1);
        if (old == pitch) return;
        if (old >= 0) { listener.up(id); fingers.delete(id); }
        if (pitch >= 0) { fingers.put(id, pitch); listener.down(id, pitch); }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true); move(event.getPointerId(index), event.getX(index), event.getY(index));
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) move(event.getPointerId(i), event.getX(i), event.getY(i));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = event.getPointerId(index); listener.up(id); fingers.delete(id);
            if (action == MotionEvent.ACTION_UP) performClick();
        } else if (action == MotionEvent.ACTION_CANCEL) releaseAll();
        invalidate(); return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
