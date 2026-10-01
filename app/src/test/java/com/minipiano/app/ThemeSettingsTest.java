package com.minipiano.app;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageButton;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class ThemeSettingsTest {
    @Before public void resetPreferences() { UiTheme.preferences(RuntimeEnvironment.getApplication()).edit().clear().commit(); }
    private <T extends View> T find(android.app.Activity activity, String tag) { return activity.findViewById(android.R.id.content).findViewWithTag(tag); }
    @Test public void defaultSystemModeAndImmediateDarkLightSelection() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            assertEquals(0, new UiTheme(activity).mode);
            assertTrue(find(activity, "displayMode0").isSelected());
            Button dark = find(activity, "displayMode2"); dark.performClick();
            UiTheme theme = new UiTheme(activity); assertTrue(theme.dark); assertTrue(dark.isSelected());
            assertTrue("selected label: " + dark.getText(), dark.getText().toString().contains("✓"));
            assertEquals(android.graphics.Typeface.BOLD, dark.getTypeface().getStyle() & android.graphics.Typeface.BOLD);
            assertEquals(theme.primary, ((GradientDrawable) dark.getBackground()).getColor().getDefaultColor());
            assertEquals(theme.onPrimary, dark.getCurrentTextColor());
            Button light = find(activity, "displayMode1"); light.performClick(); assertFalse(new UiTheme(activity).dark); assertFalse(dark.isSelected());
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                int appearance = activity.getWindow().getDecorView().getWindowInsetsController().getSystemBarsAppearance();
                assertTrue((appearance & WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) != 0);
                assertTrue((appearance & WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) != 0);
            }
        }
    }
    @Test public void presetAndHueSliderAreLinkedAndCustomChoicePersists() {
        int hue;
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            Button purple = find(activity, "themeColor2"); purple.performClick();
            HueSlider slider = find(activity, "hueSlider"); assertEquals(265, slider.hue()); assertTrue(purple.isSelected());
            View swatch = find(activity, "colorSwatch"); assertEquals(UiTheme.color(265), ((GradientDrawable) swatch.getBackground()).getColor().getDefaultColor());
            slider.layout(0, 0, 720, 64);
            MotionEvent touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 360, 32, 0);
            slider.onTouchEvent(touch); touch.recycle();
            hue = slider.hue(); assertEquals(hue, new UiTheme(activity).hue);
            assertTrue(UiTheme.preferences(activity).getBoolean("customTheme", false)); assertFalse(purple.isSelected());
            assertEquals(UiTheme.color(hue), ((GradientDrawable) swatch.getBackground()).getColor().getDefaultColor());
        }
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            assertEquals(hue, new UiTheme(controller.get()).hue);
            assertEquals(hue, ((HueSlider) find(controller.get(), "hueSlider")).hue());
        }
    }
    @Test public void systemModeFollowsConfigurationAndSelectedTextHasContrast() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics()); activity.onConfigurationChanged(config);
            assertTrue(new UiTheme(activity).dark);
            ((Button) find(activity, "displayMode1")).performClick(); assertFalse(new UiTheme(activity).dark);
            for (int h = 0; h < 360; h++) assertTrue(UiTheme.contrast(UiTheme.color(h), UiTheme.foreground(UiTheme.color(h))) >= 4.5);
        }
    }
    @Test public void gearOpensSettingsAndMainRefreshesAppearanceOnReturn() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ImageButton gear = find(activity, "settingsGear"); assertEquals("設定", gear.getContentDescription()); gear.performClick();
            assertEquals(SettingsActivity.class.getName(), Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            controller.pause(); UiTheme.preferences(activity).edit().putInt("displayMode", 2).putInt("themeHue", 330).apply(); controller.resume();
            assertTrue(new UiTheme(activity).dark); assertEquals(330, new UiTheme(activity).hue);
            assertEquals(new UiTheme(activity).text, gear.getImageTintList().getDefaultColor());
        }
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get(); activity.onBackPressed(); assertTrue(activity.isFinishing());
        }
    }
    @Test public void soundSelectionAndVolumeAreSavedAndRestored() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            assertTrue(find(activity, "sampledPiano").isSelected());
            ((Button) find(activity, "syntheticPiano")).performClick();
            assertFalse(UiTheme.preferences(activity).getBoolean("sampledPiano", true));
            android.widget.SeekBar volume = find(activity, "pianoVolume"); assertEquals(100, volume.getProgress());
            android.os.Bundle args = new android.os.Bundle(); args.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 37);
            assertTrue(volume.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), args));
            assertEquals(37, UiTheme.preferences(activity).getInt("pianoVolume", -1));
        }
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            assertTrue(find(controller.get(), "syntheticPiano").isSelected());
            assertEquals(37, ((android.widget.SeekBar) find(controller.get(), "pianoVolume")).getProgress());
        }
    }
    @Test public void uprightSelectionIsExclusiveAndPersists() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            ((Button) find(activity, "uprightPiano")).performClick();
            assertEquals(PianoSound.UPRIGHT, PianoSound.selected(activity)); assertTrue(find(activity, "uprightPiano").isSelected());
            assertFalse(find(activity, "sampledPiano").isSelected()); assertFalse(find(activity, "syntheticPiano").isSelected());
        }
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            assertTrue(find(controller.get(), "uprightPiano").isSelected());
        }
    }
    @Test public void oldSyntheticPreferenceMigratesWithoutChangingUserChoice() {
        UiTheme.preferences(RuntimeEnvironment.getApplication()).edit().putBoolean("sampledPiano", false).apply();
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            assertEquals(PianoSound.SYNTHETIC, PianoSound.selected(controller.get()));
            assertTrue(find(controller.get(), "syntheticPiano").isSelected());
        }
    }
}
