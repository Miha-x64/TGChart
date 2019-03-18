package net.aquadc.tgchart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Property;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.core.view.ViewCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import com.codemonkeylabs.fpslibrary.TinyDancer;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public final class MainActivity extends Activity
        implements ChartDrawable.ValueFormatter, RangeBar.SelectionChangeListener, AdapterView.OnItemClickListener {

    private static Chart chart;

    private static final String K_COLOUR_MODE = "colourMode";
    private ColourMode colourMode = ColourMode.LIGHT;

    private TextView titleView;

    // put charts and extras to different displayLists so extras redraw won't trigger chart redraw
    private FrameLayout chartView;
//    private ChartExtrasView chartExtrasView; // balloon, etc

    private FrameLayout rangeBarChartView;
    private RangeBar rangeBar;

    private ColumnChooser columnChooser;

    private View sheetShadow;
    private View windowBackground;

    private ChartDrawable bigChart;
    private ChartDrawable smallChart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());

        TinyDancer.create().show(this);

        if (chart == null) {
            chart = Chart.readTestChart(this); // TODO: mv to bg
        }
        format = new SimpleDateFormat("MMM d", getResources().getConfiguration().locale);
        float dp = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        bigChart = new ChartDrawable(chart, (int) (2.5f * dp));
        bigChart.configureGuidelines((int) (1.5f * dp), (int) (4 * dp), (int) (14 * sp), this, countFormatter);
        bigChart.occupyEvenIfEmptyY(Double.MIN_VALUE, 0);
        ViewCompat.setBackground(chartView, bigChart);

//        chartExtrasView.setChart(bigChart);
//        chartExtrasView.setGuidelineThickness((int) (1.5f * dp));

        smallChart = new ChartDrawable(chart, Math.max(1, (int) dp));
        ViewCompat.setBackground(rangeBarChartView, smallChart); // split up chart and bar, so invalidate()
        rangeBar.setSelectionChangeListener(this);               // will trigger only a single redraw

        columnChooser.setData(chart.columns, this);

        if (savedInstanceState != null) {
            colourMode = (ColourMode) savedInstanceState.getSerializable(K_COLOUR_MODE);
        }
        applyColours(colourMode);
    }
    private final Date date = new Date();
    private DateFormat format;
    @Override public void formatValueInto(StringBuilder sb, double value) {
        date.setTime((long) value);
        sb.append(format.format(date)); // DateFormat supports appending only into StringBuffer and thus sucks
    }
    @Override public void onSelectedRangeChanged(int selectionStart, int selectionEnd) {
        bigChart.setVisibleRange(selectionStart, selectionEnd);
//        chartExtrasView.setVisibleRange(selectionStart, selectionEnd);
    }
    @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        boolean checked = columnChooser.isItemChecked(position);
        bigChart.setColumnVisibleAt(position, checked);
        smallChart.setColumnVisibleAt(position, checked);
//        chartExtrasView.setColumnVisibleAt(position, checked);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem lightDark = menu.add(R.string.menu_light_dark);
        lightDark.setIcon(VectorDrawableCompat.create(getResources(), R.drawable.ic_visibility_white_24dp, null));
        lightDark.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // assert item is menu_light_dark
        applyColours(colourMode.next());

        return true;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(K_COLOUR_MODE, colourMode);
    }

    private View createContentView() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        {
            float dp = getResources().getDisplayMetrics().density;
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
//                chartExtrasView = new ChartExtrasView(this);
//                chartExtrasView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//                chartView.addView(chartExtrasView);
            }
            ll.addView(chartView);

            rangeBarChartView = new FrameLayout(this);
            LinearLayout.LayoutParams rangeBarChartLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * dp));
            rangeBarChartLp.setMargins(margins, margins, margins, margins);
            rangeBarChartView.setLayoutParams(rangeBarChartLp);
            {
                rangeBar = new RangeBar(this);
                rangeBar.setId(R.id.range_chooser);
                rangeBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                int hwBorder = (int) (4 * dp);
                int vwBorder = (int) (2 * dp);
                rangeBar.setWindowBorders(hwBorder, vwBorder, hwBorder, vwBorder);
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
    private void applyColours(final ColourMode colourMode) { // TODO: apply first state instantaneously
        AnimatorSet set = new AnimatorSet();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // yeah, I'm too damn lazy for making a special IntProperty for this
            set.playTogether(ObjectAnimator.ofArgb(getWindow(), "statusBarColor", this.colourMode.statusBar, colourMode.statusBar));
        }

        final TransitionDrawable actionBarBackground = colourTransition(null, this.colourMode.toolbar, colourMode.toolbar);
        getActionBar().setBackgroundDrawable(actionBarBackground);

        final TransitionDrawable sheetBackground = colourTransition(null, this.colourMode.sheet, colourMode.sheet);
        getWindow().setBackgroundDrawable(sheetBackground);

        final TransitionDrawable shadowDrawable = new TransitionDrawable(new Drawable[] {
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { this.colourMode.shadow, this.colourMode.window }),
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { colourMode.shadow, colourMode.window }),
        });
        ViewCompat.setBackground(sheetShadow, shadowDrawable);

        final TransitionDrawable windowBackgroundDrawable =
                colourTransition(windowBackground.getBackground(), this.colourMode.window, colourMode.window);
        ViewCompat.setBackground(windowBackground, windowBackgroundDrawable);

        set.playTogether(ObjectAnimator.ofObject(
                titleView, "textColor", ARGB_EVAL, this.colourMode.titleText, colourMode.titleText
        ));

        set.playTogether(columnChooser.animateTextColour(this.colourMode.itemText, colourMode.itemText));

        addArgbAnim(set, columnChooser, ColumnChooser.DIVIDER_COLOUR_PROPERTY, this.colourMode.itemDivider, colourMode.itemDivider);

        addArgbAnim(set, rangeBar, RangeBar.DIM_COLOUR_PROP, this.colourMode.rangeDim, colourMode.rangeDim);

        addArgbAnim(set, rangeBar, RangeBar.WINDOW_BORDER_COLOUR_PROP, this.colourMode.rangeWindowBorder, colourMode.rangeWindowBorder);

        set.setDuration(300);
        actionBarBackground.startTransition(300);
        sheetBackground.startTransition(300);
        shadowDrawable.startTransition(300);
        windowBackgroundDrawable.startTransition(300);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                getActionBar().setBackgroundDrawable(actionBarBackground.getDrawable(1));
                getWindow().setBackgroundDrawable(sheetBackground.getDrawable(1));
                columnChooser.setSelector(Res.drawableFromTheme(getApplicationContext(), colourMode.baseTheme, android.R.attr.selectableItemBackground));
                ViewCompat.setBackground(sheetShadow, shadowDrawable.getDrawable(1));
                ViewCompat.setBackground(windowBackground, windowBackgroundDrawable.getDrawable(1));
                bigChart.setGuidelineColour(colourMode.guideline);
//                chartExtrasView.setGuidelineColour(colourMode.guideline);
                bigChart.setNumberColour(colourMode.numbers);
            }
        });
        set.start();
        this.colourMode = colourMode;
    }
    private static <T> void addArgbAnim(AnimatorSet set,
                                        T target, Property<T, Integer> property, @ColorInt int from, @ColorInt int to) {
        set.playTogether(ObjectAnimator.ofObject(target, property, ARGB_EVAL, from, to));
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
            sb.append(value);
            int i = sb.lastIndexOf(".0");
            if (i > 0) sb.setLength(i);
            if (unit != '\0') sb.append(unit);
        }
    };

}
