package net.aquadc.tgchart;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Property;
import android.view.Window;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.MainThread;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;


final class Util {

    private Util() {}

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

    public static final Property<TextView, Integer> TEXT_COLOR = new Property<TextView, Integer>(Integer.class, "textColor") {
        @Override public Integer get(TextView object) {
            return object.getTextColors().getDefaultColor();
        }
        @Override public void set(TextView object, Integer value) {
            object.setTextColor(value);
        }
    };

    public static final Property<ColorDrawable, Integer> COLOR = new Property<ColorDrawable, Integer>(Integer.class, "color") {
        @Override public Integer get(ColorDrawable object) {
            return object.getColor();
        }
        @Override public void set(ColorDrawable object, Integer value) {
            object.setColor(value);
        }
    };

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP) static final class Lollipop {
        public static final Property<Window, Integer> STATUS_BAR_COLOR = new Property<Window, Integer>(Integer.class, "statusBarColor") {
            @Override public Integer get(Window object) {
                return object.getStatusBarColor();
            }
            @Override public void set(Window object, Integer value) {
                object.setStatusBarColor(value);
            }
        };
    }

}
