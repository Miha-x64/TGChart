package net.aquadc.tgchart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Property;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.ColorInt;
import androidx.core.view.ViewCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import com.codemonkeylabs.fpslibrary.TinyDancer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;


public final class MainActivity extends Activity
        implements ValueCallback<Chart>, RangeBar.SelectionChangeListener, AdapterView.OnItemClickListener {

    private static SettableFuture<Chart> chart;

    private static final String K_COLOUR_MODE = "colourMode";
    private ColourMode colourMode = ColourMode.LIGHT;

    private TextView titleView;

    // put charts and extras to different displayLists so extras redraw won't trigger chart redraw
    private FrameLayout chartView;
    private ChartBubbleView chartBubbleView; // balloon, etc

    private FrameLayout rangeBarChartView;
    private RangeBar rangeBar;

    private ColumnChooser columnChooser;

    private View sheetShadow;
    private View windowBackground;

    private ChartDrawable bigChart;
    private ChartDrawable smallChart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        if (chart == null) {
            chart = new SettableFuture<>();
            new Thread() {
                @Override public void run() {
                    chart.set(Chart.readTestChart(getApplicationContext()));
                }
            }.start();
        }

        super.onCreate(savedInstanceState);
        setContentView(createContentView());

        TinyDancer.create().show(this);

        if (savedInstanceState != null) {
            colourMode = (ColourMode) savedInstanceState.getSerializable(K_COLOUR_MODE);
        }
        applyColours(colourMode, false);
        chart.subscribe(this);
    }
    @Override public void onReceiveValue(Chart value) {
        Locale locale = getResources().getConfiguration().locale;
        Date sharedDate = new Date();
        ChartDrawable.ValueFormatter shortFormat = new DateFormatter(sharedDate, new SimpleDateFormat("MMM d", locale));
        ChartDrawable.ValueFormatter longFormat = new DateFormatter(sharedDate, new SimpleDateFormat("E, MMM d", locale));

        float dp = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        bigChart = new ChartDrawable(value, 2.5f * dp);
        bigChart.configureGuidelines(1.5f * dp, 4 * dp, 14 * sp, shortFormat, countFormatter);
        bigChart.occupyEvenIfEmptyY(-Double.MAX_VALUE, 0);
        ViewCompat.setBackground(chartView, bigChart);

        chartBubbleView.setChart(bigChart);
        chartBubbleView.setPadding(0, 0, 0, /* textSize * 2 */ (int) (28 * sp));
        chartBubbleView.setFormatters(longFormat, countFormatter);

        smallChart = new ChartDrawable(value, Math.max(1, dp));
        ViewCompat.setBackground(rangeBarChartView, new InsetDrawable(smallChart, rangeBar.getPaddingLeft(), 0, rangeBar.getPaddingRight(), 0));
        rangeBarChartView.setPadding(0, 0, 0, 0);
        rangeBar.setSelectionChangeListener(this); // split up chart and bar, so invalidate() will trigger only a single redraw

        sheetShadow.setOnClickListener(new View.OnClickListener() {
            private int clicks = 0;
            private final String[] data = {
                    "\uD83D\uDC4B hello Telegram people!",
                    "App by Mike Gorünov \uD83D\uDC81\u200D♂️ for Telegram Contest",
                    "App by @Harmonizr \uD83C\uDF75 for Telegram Contest",
                    "github.com/Miha-x64 \uD83D\uDC68\u200D\uD83D\uDCBB"
            };
            private Random random;
            @Override public void onClick(View v) {
                if (clicks++ % 8 == 0) {
                    if (random == null) random = new Random();
                    Toast.makeText(v.getContext(), data[random.nextInt(data.length)], Toast.LENGTH_LONG).show();
                }
            }
        });

        columnChooser.setData(value.columns, this);
        applyColours(colourMode, false);
    }
    @Override public void onSelectedRangeChanged(int selectionStart, int selectionEnd) {
        bigChart.setVisibleRange(selectionStart, selectionEnd);
    }
    @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        boolean checked = columnChooser.isItemChecked(position);
        bigChart.setColumnVisibleAt(position, checked);
        smallChart.setColumnVisibleAt(position, checked);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem lightDark = menu.add(R.string.menu_light_dark);
        lightDark.setIcon(VectorDrawableCompat.create(getResources(), R.drawable.ic_visibility_white_24dp, null));
        lightDark.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // assert item is menu_light_dark
        applyColours(colourMode.next(), true);

        return true;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(K_COLOUR_MODE, colourMode);
    }

    @Override
    protected void onDestroy() {
        chart.unsubscribe(this);
        super.onDestroy();
    }

    private View createContentView() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        {
            float dp = getResources().getDisplayMetrics().density;
            float sp = getResources().getDisplayMetrics().scaledDensity;
            int margins = (int) (16 * dp);

            titleView = new TextView(this);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.setMargins(margins, margins, margins, margins);
            titleView.setLayoutParams(titleLp);
            titleView.setText("Followers");
            titleView.setTextSize(16f);
            ll.addView(titleView);

            chartView = new FrameLayout(this);
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 900f);
            chartLp.setMargins(margins, 0, margins, 0);
            chartView.setLayoutParams(chartLp);
            {
                chartBubbleView = new ChartBubbleView(this);
                chartBubbleView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                chartBubbleView.setSizes(2f * dp, 3.5f * dp, 2 * dp);
                chartBubbleView.setTextSizes(14 * sp, 16 * sp, 12 * sp, 8 * sp);
                chartView.addView(chartBubbleView);
            }
            ll.addView(chartView);

            rangeBarChartView = new FrameLayout(this);
            LinearLayout.LayoutParams rangeBarChartLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * dp));
            rangeBarChartLp.setMargins(0, margins, 0, margins);
            rangeBarChartView.setLayoutParams(rangeBarChartLp);
            {
                rangeBar = new RangeBar(this);
                rangeBar.setId(R.id.range_chooser);
                rangeBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                int hwBorder = (int) (4 * dp);
                int vwBorder = (int) (2 * dp);
                rangeBar.setWindowBorders(hwBorder, vwBorder, hwBorder, vwBorder);
                rangeBar.setPadding(margins, 0, margins, 0); // it's easier to touch a view when it handles touches on its paddings
                rangeBarChartView.addView(rangeBar);
            }
            ll.addView(rangeBarChartView);

            LinearLayout chooserWrapper = new LinearLayout(this);
            chooserWrapper.setOrientation(LinearLayout.VERTICAL);
            chooserWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 600f));
            {
                columnChooser = new ColumnChooser(this);
                columnChooser.setId(R.id.data_set_chooser);
                columnChooser.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                chooserWrapper.addView(columnChooser);

                sheetShadow = new View(this);
                sheetShadow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (3 * dp)));
                chooserWrapper.addView(sheetShadow);

                windowBackground = new View(this);
                windowBackground.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                chooserWrapper.addView(windowBackground);
            }
            ll.addView(chooserWrapper);
        }
        return ll;
    }

    @SuppressWarnings("unchecked")
    private static final TypeEvaluator<Integer> ARGB_EVAL = new ArgbEvaluator();
    private void applyColours(final ColourMode colourMode, boolean anim) {
        AnimatorSet set = anim ? new AnimatorSet() : null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addArgbAnim(set, getWindow(), Util.Lollipop.STATUS_BAR_COLOR, this.colourMode.statusBar, colourMode.statusBar);
        }

        getActionBar().setBackgroundDrawable(addColorDrawableTransit(set, null, this.colourMode.toolbar, colourMode.toolbar));

        getWindow().setBackgroundDrawable(addColorDrawableTransit(set, null, this.colourMode.sheet, colourMode.sheet));

        final LayerDrawable shadowDrawable; {
            GradientDrawable toShadow = createShadow(colourMode);
            Drawable bg;
            if (anim) {
                shadowDrawable = new LayerDrawable(new Drawable[]{ createShadow(this.colourMode), toShadow });
                bg = shadowDrawable;
                set.playTogether(ObjectAnimator.ofInt(toShadow, "alpha", 0, 255));
            } else {
                shadowDrawable = null;
                bg = toShadow;
            }
            ViewCompat.setBackground(sheetShadow, bg);
        }

        ViewCompat.setBackground(windowBackground, addColorDrawableTransit(set, windowBackground.getBackground(), this.colourMode.window, colourMode.window));

        addArgbAnim(set, titleView, Util.TEXT_COLOR, this.colourMode.titleText, colourMode.titleText);

        addArgbAnim(set, columnChooser, ColumnChooser.TEXT_COLOUR, this.colourMode.itemText, colourMode.itemText);

        addArgbAnim(set, columnChooser, ColumnChooser.DIVIDER_COLOUR_PROPERTY, this.colourMode.itemDivider, colourMode.itemDivider);

        addArgbAnim(set, rangeBar, RangeBar.DIM_COLOUR_PROP, this.colourMode.rangeDim, colourMode.rangeDim);

        addArgbAnim(set, rangeBar, RangeBar.WINDOW_BORDER_COLOUR_PROP, this.colourMode.rangeWindowBorder, colourMode.rangeWindowBorder);

        this.colourMode = colourMode;
        if (set == null) {
            applyTransparentColours();
        } else {
            set.setDuration(200);

            ValueAnimator progress = ObjectAnimator.ofInt(0, 255);
            progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                private boolean applied = false;
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    if (!applied && (Integer) animation.getAnimatedValue() > 100) {
                        applied = true;
                        applyTransparentColours();
                    }
                }
            });
            set.playTogether(progress);

            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    ViewCompat.setBackground(sheetShadow, shadowDrawable.getDrawable(1));
                }
            });
            set.start();
        }
    }
    private GradientDrawable createShadow(ColourMode colourMode) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{colourMode.shadow, colourMode.window});
    }
    private void applyTransparentColours() {
        columnChooser.setSelector(Util.drawableFromTheme(getApplicationContext(), colourMode.baseTheme, android.R.attr.selectableItemBackground));
        if (bigChart != null) {
            bigChart.setGuidelineColour(colourMode.hGuideline);
            bigChart.setNumberColour(colourMode.numbers);
        }
        chartBubbleView.setGuidelineColour(colourMode.vGuideline);
        chartBubbleView.setColours(colourMode.cardBg, colourMode. cardText);
    }
    private static <T> void addArgbAnim(AnimatorSet set,
                                        T target, Property<T, Integer> property, @ColorInt int from, @ColorInt int to) {
        if (set == null) property.set(target, to);
        else set.playTogether(ObjectAnimator.ofObject(target, property, ARGB_EVAL, from, to));
    }
    private static Drawable addColorDrawableTransit(AnimatorSet set, Drawable prev, @ColorInt int from, @ColorInt int to) {
        ColorDrawable c = prev instanceof ColorDrawable ? (ColorDrawable) prev : new ColorDrawable();
        if (set == null) {
            c.setColor(to);
        } else {
            set.playTogether(ObjectAnimator.ofObject(c, Util.COLOR, ARGB_EVAL, from, to));
        }
        return c;
    }
    private static TransitionDrawable colourTransition(Drawable prevDrawable, @ColorInt int from, @ColorInt int to) {
        if (prevDrawable instanceof TransitionDrawable) {
            TransitionDrawable trans = (TransitionDrawable) prevDrawable;
            prevDrawable = trans.getDrawable(trans.getNumberOfLayers() - 1);
        }

        Drawable a = prevDrawable instanceof ColorDrawable && ((ColorDrawable) prevDrawable).getColor() == from
                ? prevDrawable : new ColorDrawable(from);
        return new TransitionDrawable(new Drawable[] { a, new ColorDrawable(to) });
    }

    private static final ChartDrawable.ValueFormatter countFormatter = new ChartDrawable.ValueFormatter() {
        private final double[] multipliers = { 1_000_000_000, 1_000_000, 1_000, 1, .001, .000_001 /* intentionally omitted .000_000_001 */ };
        private final char[] units = { 'G', 'M', 'k', '\0', 'm', 'μ', 'n' };
        @Override public void formatValueInto(StringBuilder sb, double value) {
            int unitIdx = 0;
            char unit;
            if (value == 0.0 || Double.isInfinite(value) || Double.isNaN(value)) {
                unit = '\0';
            } else {
                for (; unitIdx < multipliers.length; unitIdx++) {
                    double mul = multipliers[unitIdx];
                    if (value >= mul) {
                        value /= mul;
                        break;
                    }
                }
                unit = units[unitIdx];
            }
            if (unit != '\0') { // round value to a single digit after comma
                value = Math.round(10 * value) / 10.0d;
            }
            sb.append(value);
            int i = sb.lastIndexOf(".0");
            if (i > 0) sb.setLength(i);
            if (unit != '\0') sb.append(unit);
        }
    };

}
