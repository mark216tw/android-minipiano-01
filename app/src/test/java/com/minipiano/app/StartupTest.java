package com.minipiano.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.WindowInsetsController;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 29, 36})
public class StartupTest {
    @Test public void activityCanLaunchLayoutAndDrawKeyboard() {
        try (var controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            activity.onWindowFocusChanged(true);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                    activity.getWindow().getDecorView().getWindowInsetsController().getSystemBarsBehavior());
            } else {
                assertTrue((activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
            }
            View content = activity.findViewById(android.R.id.content);
            content.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
            content.layout(0, 0, 1280, 720);
            Bitmap bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            content.draw(new Canvas(bitmap));
            assertNotNull(content);
            bitmap.recycle();
            controller.pause().resume();
            activity.onWindowFocusChanged(true);
        }
    }
}
