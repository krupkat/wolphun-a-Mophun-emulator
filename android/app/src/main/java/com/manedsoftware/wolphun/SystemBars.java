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
        View content = activity.findViewById(android.R.id.content);
        View root = (View) content.getParent();
        if(activity instanceof androidx.appcompat.app.AppCompatActivity){
            // In edge-to-edge mode AppCompat sends the action bar's height to the
            // content as a legacy system-window inset, rather than a top margin.
            final int cl = content.getPaddingLeft(), ct = content.getPaddingTop();
            final int cr = content.getPaddingRight(), cb = content.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
                view.setPadding(cl + insets.getSystemWindowInsetLeft(),
                    ct + insets.getSystemWindowInsetTop(),
                    cr + insets.getSystemWindowInsetRight(),
                    cb + insets.getSystemWindowInsetBottom());
                return WindowInsetsCompat.CONSUMED;
            });
        }
        final int left = root.getPaddingLeft(), top = root.getPaddingTop();
        final int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            // Preserve AppCompat's own inset handling: it also reserves the action bar's
            // height above the content. Consuming everything here puts the first row under it.
            return ViewCompat.onApplyWindowInsets(view,
                windowInsets.inset(bars.left, bars.top, bars.right, bars.bottom));
        });
        ViewCompat.requestApplyInsets(root);
    }
}
