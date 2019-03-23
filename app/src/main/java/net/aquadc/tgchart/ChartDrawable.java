package net.aquadc.tgchart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;


public final class ChartDrawable extends Drawable {

    private static final boolean DEBUG = false;

    private static final int Y_ANIM_DURATION = 250;
    private static final int X_ANIM_DURATION = 200;
    private static final TimeInterpolator INTERPOLATOR = new DecelerateInterpolator();

    final Chart data; // package-private shortcut for ChartExtrasView

    private Path[] paths;
    private Matrix matrix;

    private final float chartThickness;

    private float guidelineThickness;
    private float textIndent;
    private float textSize;
    private ValueFormatter xValueFormatter;
    private ValueFormatter yValueFormatter;

    private final Paint paint;
    private boolean dirtyBounds;

    private double minTop = -Double.MAX_VALUE;
    private double maxBottom = Double.MAX_VALUE;

    private static final int APPEARING = 1 << 16;
    private final int[] visibilities;
    private ValueAnimator[] visibilityAnimations;
    private ValueAnimator.AnimatorUpdateListener[] visibilityListeners;

    private int firstVisibleXPerMille;
    private int firstInvisibleXPerMille;

    @ColorInt private int guidelineColour = Color.TRANSPARENT;

    public ChartDrawable(Chart data, float chartThickness) { // TODO: sampling!
        this.data = data;
        this.chartThickness = chartThickness;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStyle(Paint.Style.STROKE);
        this.dirtyBounds = true;

        int length = data.columns.length;
        this.visibilities = new int[length];
        for (int i = 0; i < length; i++) {
            visibilities[i] = (byte) 255;
        }

        this.firstVisibleXPerMille = 0;
        this.firstInvisibleXPerMille = 1000;
    }
    public void configureGuidelines(float guidelineThickness, float textIndent, float textSize,
                                    ValueFormatter xValueFormatter, ValueFormatter yValueFormatter) {
        this.guidelineThickness = guidelineThickness;
        this.textIndent = textIndent;
        this.textSize = textSize;
        this.xValueFormatter = xValueFormatter;
        this.yValueFormatter = yValueFormatter;
        invalidateSelf();
    }
    /**
     * Makes chart to occupy vertical space even if nothing to draw there.
     * Useful to force '0' to be visible even if all Y values are positive:
     * {@code occupyEvenIfEmptyY(Double.MIN_VALUE, 0);}.
     * @param minTop    the Y position which must be fit into bounds even if all the graph if below it
     * @param maxBottom the Y position which must be fit into bounds even if all the graph is above it
     */
    public void occupyEvenIfEmptyY(double minTop, double maxBottom) {
        this.minTop = minTop;
        this.maxBottom = maxBottom;
        invalidateSelf();
    }

    private float[] normalized;

    private double prevYMin, prevYMax, targetYMin, targetYMax, yMin, yMax, yDiff = Double.NaN; // shared with Bubble overlay
    private int yAnimProgress = 0;
    private AnimatorSet yAnimator;
    private ValueAnimator.AnimatorUpdateListener updateYMin, updateYMax, updateYProgress;
    private void animYDiff(double toYMin, double toYMax) {
        if (targetYMin == toYMin && targetYMax == toYMax) {
            return;
        }
        targetYMin = toYMin;
        targetYMax = toYMax;

        if (yAnimator == null || !yAnimator.isRunning()) {
            prevYMin = yMin;
            prevYMax = yMax;
        } else {
            yAnimator.cancel();
        }
        if (updateYMin == null) {
            updateYMin = new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    yMin = (Double) animation.getAnimatedValue();
                    yDiff = yMax - yMin;
                    invalidateSelf();
                }
            };
            updateYMax = new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    yMax = (Double) animation.getAnimatedValue();
                    yDiff = yMax - yMin;
                    invalidateSelf();
                }
            };
            updateYProgress = new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    yAnimProgress = (Integer) animation.getAnimatedValue();
                }
            };
        }

        ValueAnimator yMinAnim = ObjectAnimator.ofObject(DoubleEvaluator.INSTANCE, yMin, toYMin);
        yMinAnim.addUpdateListener(updateYMin);

        ValueAnimator yMaxAnim = ObjectAnimator.ofObject(DoubleEvaluator.INSTANCE, yMax, toYMax);
        yMaxAnim.addUpdateListener(updateYMax);

        ValueAnimator yProgressAnim = ObjectAnimator.ofInt(0, 255);
        yProgressAnim.addUpdateListener(updateYProgress);

        yAnimator = new AnimatorSet().setDuration(Y_ANIM_DURATION);
        yAnimator.setInterpolator(INTERPOLATOR);
        yAnimator.playTogether(yMinAnim, yMaxAnim, yProgressAnim);
        yAnimator.start();
    }
    @Override public void draw(@NonNull Canvas canvas) {
        if (dirtyBounds) normalize();

        long nanos = 0;
        if (DEBUG) nanos = System.nanoTime();

        // common preparations
        int width = width();
        float[] normalized = this.normalized;

        int length = data.x.values.length;
        float xStart = width * firstVisibleXPerMille / 1000f; // [0; width] currently visible
        int firstVisibleIdx = indexOfClosest(normalized, 0, length, xStart);
        if (normalized[firstVisibleIdx] > xStart && firstVisibleIdx > 0) firstVisibleIdx--; // draw first point off-screen

        float xEnd = width * firstInvisibleXPerMille / 1000f;
        int lastVisibleIdx = indexOfClosest(normalized, 0, length, xEnd);

        Chart.Column[] columns = data.columns;
        int colCount = columns.length;

        /*/ figure out absolute limits — now unused since we use limits local to the visible window
        double yMin = maxBottom;
        double yMax = minTop;
        for (int ci = 0; ci < colCount; ci++) {
            if (visible.get(ci)) {
                Chart.Column column = columns[ci];
                double min = column.minValue;
                if (min < yMin) yMin = min;
                double max = column.maxValue;
                if (max > yMax) yMax = max;
            }
        }*/

        // let's find visible window limits first
        double _yMin = maxBottom;
        double _yMax = minTop;
        boolean visible = false;
        for (int ci = 0; ci < colCount; ci++) {
            int visibility = visibilities[ci];
            if (visibility != 0) {
                int yi = (ci + 1) * length;
                yi += firstVisibleIdx; // skip left invisible part
                // paths are cool & shit, but cannot be drawn partially, so let's fill 'em on demand
                // drawLine was OK but can't draw good line joins

                int xi = firstVisibleIdx;
                Path path = paths[ci];

                float firstX = normalized[xi++], firstY = normalized[yi++];
                float colMinY = firstY, colMaxY = firstY;
                int minYIdx = firstVisibleIdx, maxYIdx = firstVisibleIdx;
                path.moveTo(firstX, firstY);
                while (xi <= lastVisibleIdx) {
                    float x = normalized[xi], y = normalized[yi];
                    if (y < colMinY) {
                        colMinY = y; maxYIdx = xi;
                    } else if (y > colMaxY) {
                        colMaxY = y; minYIdx = xi;
                    } // min & max are mixed because 'normalized' values are inverted
                    path.lineTo(x, y);
                    xi++; yi++;
                }
                // don't mind right invisible part

                if ((visibility & APPEARING) != 0) {
                    Chart.Column column = columns[ci];
                    double dColMinY = column.values[minYIdx];
                    double dColMaxY = column.values[maxYIdx];
                    if (dColMinY < _yMin) _yMin = dColMinY;
                    if (dColMaxY > _yMax) _yMax = dColMaxY;
                    visible = true;
                }
            }
        }

        double _yDiff = _yMax - _yMin;
        if (visible) {
            if (Double.isNaN(yDiff)) {
                yMin = _yMin;
                yMax = _yMax;
                yDiff = _yDiff;
                prevYMin = _yMin;
                prevYMax = _yMax;
            } else if (yDiff != _yDiff || yMax != _yMax) {
                animYDiff(_yMin, _yMax);
            }
        } // else don't touch scale and let the paths disappear

        float xScale = width / (xEnd - xStart);
        float translateX = -xStart * xScale;
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.clipRect(0, 0, width, height());

        // draw guidelines first
        boolean drawGuidelines = guidelineThickness > 0 && (guidelineColour & 0xFF_000000) != 0;
        boolean drawNumbers = textSize > 0 && numberPaint != null;
        int bottomPadding = 0;
        if (drawNumbers && xValueFormatter != null) {
            drawXValues(canvas, xScale, translateX);
            bottomPadding = (int) (2 * textSize);
        }
        drawNumbers &= yValueFormatter != null;
        if (drawGuidelines || drawNumbers) {
            int progress = yAnimProgress;
            if (progress != 255) drawGuidelinesOrNumbers(canvas, drawGuidelines, drawNumbers, bottomPadding, prevYMin, prevYMax, 255 - progress);
            if (progress != 0) drawGuidelinesOrNumbers(canvas, drawGuidelines, drawNumbers, bottomPadding, targetYMin, targetYMax, progress);
        }

        // draw columns (over guidelines)
        Paint paint = this.paint;
        paint.setStrokeWidth(chartThickness);
        int height = height();
        int chartHeight = height - bottomPadding - (int) chartThickness;
        float heightFactor = (float) chartHeight / height;
        canvas.translate(translateX, 0);

        for (int ci = 0; ci < colCount; ci++) {
            Chart.Column column = columns[ci];
            paint.setColor(column.colour);
            paint.setAlpha(visibilities[ci] & 0xFF);

            // y values are normalized to [0; height] with no regard to other data sets, let's scale according to that
            float colYDiff = (float) (column.maxValue - column.minValue);
//            float yScale = (float) (colYDiff / yDiff) * heightFactor;
            float yScale = (float) (colYDiff / yDiff) * heightFactor;
//            float translateY = (float) ((yMax - column.maxValue) / yDiff * chartHeight);
            float translateY = (float) ((yMax - column.maxValue) / yDiff * chartHeight);

            Path path = paths[ci];
            matrix.setScale(xScale, yScale);
            matrix.postTranslate(0, translateY + (int) chartThickness);
            path.transform(matrix);
            canvas.drawPath(path, paint);
            path.rewind();
        }
        canvas.restore();

        if (DEBUG) {
            int pairs = lastVisibleIdx - firstVisibleIdx;
            nanos = System.nanoTime() - nanos;
            double sum = 0;
            for (int i = firstVisibleIdx; i < lastVisibleIdx;) {
                float v = normalized[i++];
                sum += normalized[i] - v;
            }
            TextPaint tp = new TextPaint();
            tp.setColor(0xFF_65B9AC);
            tp.setTextSize(36);
            canvas.drawText(String.format("avg. dx = %.2f", sum * xScale / pairs), 10, 30, tp);
            canvas.drawText(String.format("draw: %d μs", nanos / 1000), 10, 70, tp);
        }
    }

    private StringBuilder numberSb;
    private TextPaint numberPaint;
    private void drawGuidelinesOrNumbers(Canvas canvas, boolean drawGuidelines, boolean drawNumbers, int bottomPadding, double yMin, double yMax, int alpha) {
        int height = height() - bottomPadding;
        double yDiff = yMax - yMin;
        double step = yDiff / height * 3 * textSize; // control the density of these numbers
        if (step != 0) {
            int exp = 0;
            while (step >= 10) {
                step /= 10;
                exp++;
            }
            while (step < 1) {
                step *= 10;
                exp--;
            }
            // assert step ∈ [1; 10)
            // appropriate steps are like .1, .2, .5, 1, 2, 5, 10, ..., so let's choose between 1, 2, and 5
            double one = Math.abs(step - 1);
            double two = Math.abs(step - 2);
            double five = Math.abs(step - 5);
            double roundStep;
            if (one <= two) {
                roundStep = 1;
            } else if (two <= five) {
                roundStep = 2;
            } else {
                roundStep = 5;
            }

            roundStep *= Math.pow(10, exp);

            // finally, roundStep is user-visible step, let's draw it
            double guideline = roundStep * Math.ceil(yMin / roundStep); // first guideline

            int width = width();
            paint.setColor(guidelineColour);
            paint.setStrokeWidth(guidelineThickness);
            paint.setAlpha(paint.getAlpha() * alpha / 255);
            int numAlpha = numberPaint.getAlpha();
            numberPaint.setAlpha(numAlpha * alpha / 255);
            float lineOffset = guidelineThickness / 2; // ugly hack for line at y=0 (y=inclusiveHeight) to be fully visible
            while (guideline < yMax) {

                // translate into our coordinates
                double q = (guideline - yMin) / this.yDiff;
                float y = (float) ((1 - q) * height) - lineOffset;
                if (drawGuidelines) {
                    canvas.drawLine(0, y, width, y, paint);
                }
                if (drawNumbers) {
                    yValueFormatter.formatValueInto(numberSb, guideline);
                    canvas.drawText(numberSb, 0, numberSb.length(), textIndent, y - textIndent, numberPaint); // TODO: fade out off-screen values
                    numberSb.setLength(0);
                }

                guideline += roundStep;
            }
            numberPaint.setAlpha(numAlpha);
        }
    }
    private void drawXValues(Canvas canvas, float xScale, float translateX) {
        int length = data.x.values.length;

        // let's transform millis to [0; xValues.length]. For monotone Xes, this will give labels exactly under nodes;
        // for non-monotone Xes these values cannot be used as array indices — still using binary search instead.
        float firstVisibleX = firstVisibleXPerMille * length / 1000f; // [0; length)
        float firstInvisibleX = firstInvisibleXPerMille * length / 1000f;

        float visibleXValues = firstInvisibleX - firstVisibleX;
        float xWidthPx = (float) width() / visibleXValues;
        // heuristic: assume we don't want text more frequently than every 32 px
        int textLengthX = (int) (32f / xWidthPx); // how many X values are occupied by label horizontally
        if (textLengthX == 0) textLengthX = 1; // but it should be >= one
        int highest = Integer.highestOneBit(textLengthX);
        textLengthX = highest + (textLengthX > highest ? highest : 0); // and round it up to a power of two

        if (texts == null) texts = new StringBuilder();
        // find sample size where all texts can be fit
        while (true) { // try sample sizes until we find a suitable one
            float textWidthPx = (float) textLengthX * xWidthPx; // will be >= 32 for the first time
            texts.setLength(0);
            int requiredSpace = fitTexts(firstVisibleX, firstInvisibleX, textLengthX, textWidthPx, null, Float.NaN, Float.NaN, Float.NaN);
            if (requiredSpace == 1) {
                break;
            } else {
                textLengthX *= 2;
                // continue: we may encounter a longer text on the next pass
            }
        }
        textLengthX *= 2; // keep the distance!
        if (textLengthX == 0) textLengthX = 1;

        float textY = height() - textIndent;
        // now draw!
        fitTexts(firstVisibleX, firstInvisibleX, textLengthX, Float.NaN, canvas, translateX, xScale, textY);
    }

    private int prevTextLengthX = -1;
    int animatedTextAlpha = 255;
    ValueAnimator textAlphaAnimator;
    private int animDirection;
    private void animTextAlpha(int from, int to) {
        if (textAlphaAnimator == null) {
            if (nullOutTextAlphaAnim == null) {
                nullOutTextAlphaAnim = new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        textAlphaAnimator = null;
                    }
                };
                updateTextAlphaAnim = new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator animation) {
                        animatedTextAlpha = (Integer) animation.getAnimatedValue();
                        invalidateSelf();
                    }
                };
            }
            textAlphaAnimator = ObjectAnimator.ofInt(from, to).setDuration(X_ANIM_DURATION);
            textAlphaAnimator.addListener(nullOutTextAlphaAnim);
            textAlphaAnimator.addUpdateListener(updateTextAlphaAnim);
            textAlphaAnimator.start();
        }
    }
    private AnimatorListenerAdapter nullOutTextAlphaAnim;
    private ValueAnimator.AnimatorUpdateListener updateTextAlphaAnim;

    private StringBuilder texts;
    /**
     * @return how many times bigger {@param maxTextWidth} should be
     */
    private int fitTexts(float firstVisibleX, float firstInvisibleX, int textLengthX,
                             /*only for measuring*/ float maxTextWidth,
                             /*only for drawing*/ Canvas canvas, float translateX, float xScale, float y) {

        if (canvas != null) {
            if (prevTextLengthX != -1 && textAlphaAnimator == null) {
                if (prevTextLengthX > textLengthX) {
                    animTextAlpha(0, 255);
                    animDirection = 1;
                } else if (prevTextLengthX < textLengthX) {
                    animTextAlpha(255, 0);
                    animDirection = -1;
                }
            }
            prevTextLengthX = textLengthX;
            if (animDirection == -1) textLengthX = Math.max(1, textLengthX/2); // show disappearing values, not only stable ones
        }

        double[] xValues = data.x.values;
        int length = xValues.length;
        int width = width();

        int firstVisibleXRnd = (int) firstVisibleX / length * length;
        int firstInvisibleXRnd = (int) firstInvisibleX + textLengthX;
        int count = (int) Math.ceil((firstInvisibleXRnd - firstVisibleXRnd) / (float) textLengthX);

        int last = count - 1;
        for (int i = 0; i <= last; i++) {
            int x = firstVisibleXRnd + i * textLengthX;
            float xPos = width * x / length;
            int xIdx = indexOfClosest(normalized, 0, length, xPos);
//            xPos = normalized[xIdx]; // fixme: we seem to have a precision issue; try comparing xPos to normalized[xIdx]

            xValueFormatter.formatValueInto(texts, xValues[xIdx]);
            float textWidth = numberPaint.measureText(texts, 0, texts.length());
            if (canvas == null) { // dry run just for measurement
                texts.setLength(0);
                if (textWidth > maxTextWidth) {
                    return Math.max(2, Integer.highestOneBit((int) Math.ceil(textWidth / maxTextWidth)));
                }
            } else {
                // even here we can't reuse measured & formatted data from the previous pass because textLengthX may have changed which will move all text layouts
                float xOnScreen = xPos * xScale + translateX;
                float xq = -1.1f * xOnScreen / width + .05f; // [.05; -1.05]
                numberPaint.setAlpha(i%2 == 0 ? 255 : animatedTextAlpha);
                canvas.drawText(texts, 0, texts.length(), xOnScreen + xq * textWidth, y, numberPaint);
//                canvas.drawLine(xOnScreen, height() - 100, xOnScreen, height(), numberPaint); // debug number placements
                texts.setLength(0);
            }
        }
        numberPaint.setAlpha(255);
        return 1;
    }

    private void normalize() {
        // let's translate & scale values into the given coordinates —
        // translate & scale of Canvas can't work with so big values

        Chart data = this.data;
        Chart.Column[] columns = data.columns;
        Chart.Column xCol = data.x;
        double[] xValues = xCol.values;
        int length = xValues.length;
        int colCount = columns.length;
        if (normalized == null) {
            normalized = new float[length * (colCount + 1)];
            paths = new Path[colCount];
            for (int i = 0; i < colCount; i++) {
                paths[i] = new Path();
            }
            matrix = new Matrix();
        }

        int width = width();
        int height = height();
        float[] normalized = this.normalized;

        int i = 0;
        {
            double xMin = xCol.minValue;
            double xDiff = xCol.maxValue - xMin;
            for (; i < length; i++) {
                normalized[i] = (float) (((xValues[i] - xMin) / xDiff) * width);
            }
        }

        for (Chart.Column column : columns) {
            double yMin = column.minValue;
            double yDiff = column.maxValue - yMin;
            double[] values = column.values;
            for (int ci = 0; ci < length; ci++) { // normalize & also flip to our coordinates, where y=0 means 'top'
                float v = (float) ((1 - ((values[ci] - yMin) / yDiff)) * height);
                normalized[i++] = v;
            }
        }

        dirtyBounds = false;
    }

    // inclusive bounds
    private int width() {
        Rect bounds = getBounds();
        return bounds.right - bounds.left - 1;
    }
    private int height() {
        Rect bounds = getBounds();
        return bounds.bottom - bounds.top - 1;
    }

    @Override public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        if (numberPaint != null) numberPaint.setAlpha(alpha);
    }
    @Override public int getAlpha() {
        return paint.getAlpha();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        if (numberPaint != null) numberPaint.setColorFilter(colorFilter);
    }
    @Nullable @Override public ColorFilter getColorFilter() {
        return paint.getColorFilter();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        dirtyBounds = true;
        invalidateSelf();
    }

    public void setColumnVisibleAt(final int index, boolean whether) {
        if (index < 0 || index >= data.columns.length) {
            throw new IndexOutOfBoundsException(String.format("index must be in [0; %d), %d given", data.columns.length, index));
        }
        int alpha = visibilities[index] & 0xFF;
        int targetAlpha = whether ? 255 : 0;
        if (alpha != targetAlpha) {
            if (visibilityAnimations == null) {
                int length = visibilities.length;
                visibilityAnimations = new ValueAnimator[length];
                visibilityListeners = new ValueAnimator.AnimatorUpdateListener[length];
            }
            ValueAnimator anim = visibilityAnimations[index];
            if (anim != null) {
                anim.cancel();
            }
            if (visibilityListeners[index] == null) {
                visibilityListeners[index] = new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator animation) {
                        int prev = visibilities[index] & 0xFF;
                        int next = (byte) (int) (Integer) animation.getAnimatedValue();
                        if (next > prev) next |= APPEARING;
                        visibilities[index] = next;
                        invalidateSelf();
                    }
                };
            }

            anim = ObjectAnimator.ofInt(alpha, targetAlpha).setDuration(Y_ANIM_DURATION);
            anim.addUpdateListener(visibilityListeners[index]);
            anim.start();
            visibilityAnimations[index] = anim;

            invalidateSelf();
        }
    }

    public void setVisibleRange(int startPerMille, int endPerMille) {
        firstVisibleXPerMille = startPerMille;
        firstInvisibleXPerMille = endPerMille;

        invalidateSelf();
    }

    public void setGuidelineColour(@ColorInt int guidelineColour) {
        if (this.guidelineColour != guidelineColour) {
            this.guidelineColour = guidelineColour;
            invalidateSelf();
        }
    }

    public void setNumberColour(@ColorInt int numberColour) {
        if (numberPaint == null) {
            numberPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            numberPaint.setAlpha(paint.getAlpha()); // important: setting alpha BEFORE colour
            numberPaint.setColor(numberColour);
            numberPaint.setTextSize(textSize);
            numberPaint.setColorFilter(paint.getColorFilter());
            numberSb = new StringBuilder(5);
            invalidateSelf();
        } else if (numberPaint.getColor() != numberColour) {
            numberPaint.setColor(numberColour);
            invalidateSelf();
        }
    }

    private static int indexOfClosest(float[] haystack, int fromIndex, int toIndex, float needle) {
        int index = Arrays.binarySearch(haystack, fromIndex, toIndex, needle);
        if (index >= 0) {
            return index;
        } else {
            index = -index - 1;
            if (index == toIndex) index--; // insertion point == length
            return index;
        }
    }

    public interface ValueFormatter {
        void formatValueInto(StringBuilder sb, double value);
    }

    // shortcuts for ChartExtrasView

    int getIndexAt(float xPos) {
        if (dirtyBounds) normalize();

        float scaledX = (xPos - translateX()) / xScale();
        int length = data.x.values.length;
        return indexOfClosest(normalized, 0, length, scaledX);
    }
    float getXPositionAt(int index) {
        if (dirtyBounds) normalize();

        return normalized[index] * xScale() + translateX();
    }
    double getXValueAt(int index) {
        return data.x.values[index];
    }
    double[] getYValuesAt(int index, double[] dest) {
        Chart.Column[] cols = data.columns;
        int length = cols.length;
        if (dest == null || dest.length != length) {
            dest = new double[length];
        }
        for (int i = 0; i < length; i++) {
            dest[i] = visibilities[i] == (byte) 255 ? cols[i].values[index] : Double.NaN;
        }
        return dest;
    }

    // kinda copy-paste of draw() contents
    private float xScale() {
        int width = width();
        float xStart = width * firstVisibleXPerMille / 1000f;
        float xEnd = width * firstInvisibleXPerMille / 1000f;
        return width / (xEnd - xStart);
    }
    private float translateX() {
        int width = width();
        float xStart = width * firstVisibleXPerMille / 1000f; // [0; width] currently visible
        float xScale = xScale();
        return -xStart * xScale;
    }

    // serious copy-paste
    float[] getYPositionsAt(int index, float[] dest) {
        if (dirtyBounds) normalize();
        // fixme: assumes draw() was called!

        Chart.Column[] columns = data.columns;
        int colCnt = columns.length;
        if (dest == null || dest.length != colCnt) {
            dest = new float[colCnt];
        }
        int length = data.x.values.length;

        boolean drawNumbers = textSize > 0 && numberPaint != null;
        int bottomPadding = drawNumbers && xValueFormatter != null ? (int) (2 * textSize) : 0;

        int height = height();
        int chartHeight = height - bottomPadding - (int) chartThickness;
        float heightFactor = (float) chartHeight / height;
        for (int i = 0; i < colCnt; i++) {
            if (visibilities[i] == (byte) 255) {
                Chart.Column column = columns[i];
                double colYMax = column.maxValue;
                double colYDiff = colYMax - column.minValue;
                float yScale = (float) (colYDiff / yDiff) * heightFactor;
                float translateY = (float) ((yMax - colYMax) / yDiff * chartHeight);
                dest[i] = translateY + normalized[length + i * length + index] * yScale + (int) chartThickness;
            } else {
                dest[i] = Float.NaN;
            }
        }
        return dest;
    }

}
