package com.manedsoftware.wolphun;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keep controls clear of system bars/cutouts with Android 15+ edge-to-edge. */
final class SystemBars {
    private SystemBars() {}

    static void apply(Activity activity) {
        if (Build.VERSION.SDK_INT < 35) return;
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        // Include AppCompat's action bar as well as the activity's content.
        View root = (View) activity.findViewById(android.R.id.content).getParent();
        final int left = root.getPaddingLeft(), top = root.getPaddingTop();
        final int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
