package net.aquadc.tgchart;


import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import androidx.annotation.AttrRes;
import androidx.annotation.MainThread;
import androidx.annotation.StyleRes;

final class Res {

    private Res() {}

    static int dp(Context context, int dips) {
        return (int) (context.getResources().getDisplayMetrics().density * dips);
    }

    private static Resources.Theme tmpTheme;
    private static final int[] TMP_1INT = new int[1];
    @MainThread static Drawable drawableFromTheme(Context context, @StyleRes int theme, @AttrRes int attr) {
        if (tmpTheme == null) tmpTheme = context.getApplicationContext().getResources().newTheme();
        tmpTheme.applyStyle(theme, true);
        TMP_1INT[0] = attr;
        TypedArray ta = tmpTheme.obtainStyledAttributes(TMP_1INT);
        Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }

}
