package net.aquadc.tgchart;

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
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.BitSet;


public final class ChartDrawable extends Drawable {

    private static final boolean DEBUG = false;

    final Chart data; // package-private shortcut for ChartExtrasView

    private Path path;
    private Matrix matrix;

    private final float chartThickness;

    private float guidelineThickness;
    private float textIndent;
    private float textSize;
    private ValueFormatter xValueFormatter;
    private ValueFormatter yValueFormatter;

    private final Paint paint;
    private boolean dirty;

    private double minTop = Double.MIN_VALUE;
    private double maxBottom = Double.MAX_VALUE;
    private final BitSet visible;
    private int firstVisibleXPerMille;
    private int firstInvisibleXPerMille;

    @ColorInt private int guidelineColour = Color.TRANSPARENT;

    public ChartDrawable(Chart data, float chartThickness) { // TODO: sampling!
        this.data = data;
        this.chartThickness = chartThickness;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStyle(Paint.Style.STROKE);
        this.dirty = true;

        int length = data.columns.length;
        this.visible = new BitSet(length);
        this.visible.set(0, length);

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
    @Override public void draw(@NonNull Canvas canvas) {
        if (dirty) normalize();

        long nanos = 0;
        if (DEBUG) nanos = System.nanoTime();

        // preparations
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
        }
        double yDiff = yMax - yMin;

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
            drawGuidelinesOrNumbers(canvas, yMin, yMax, drawGuidelines, drawNumbers, bottomPadding);
        }

        // draw columns (over guidelines)
        Paint paint = this.paint;
        paint.setStrokeWidth(chartThickness);
        int height = height();
        int chartHeight = height - bottomPadding;
        float heightFactor = (float) chartHeight / height;
        for (int ci = 0; ci < colCount; ci++) {
            if (visible.get(ci)) {
                Chart.Column column = columns[ci];
                paint.setColor(column.colour);
                canvas.save();

                // y values are normalized to [0; height] with no regard to other data sets, let's scale according to that
                double colYMax = column.maxValue;
                double colYDiff = colYMax - column.minValue;
                float yScale = (float) (colYDiff / yDiff) * heightFactor;

                float translateY = (float) ((yMax - colYMax) / yDiff * chartHeight);
                canvas.translate(translateX, translateY);


                int yi = (ci + 1) * length;
                yi += firstVisibleIdx; // skip left invisible part
                // paths are cool & shit, but cannot be drawn partially, so let's fill 'em on demand
                // drawLine is OK but can't draw good line joins

                int xi = firstVisibleIdx;
                path.moveTo(normalized[xi++], normalized[yi++]);
                while (xi <= lastVisibleIdx) {
                    path.lineTo(normalized[xi++], normalized[yi++]);
                }
                // don't mind right invisible part
                matrix.setScale(xScale, yScale);
                path.transform(matrix);
                canvas.drawPath(path, paint);
                path.rewind();

                canvas.restore();
            }
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
    private void drawGuidelinesOrNumbers(Canvas canvas, double yMin, double yMax, boolean drawGuidelines, boolean drawNumbers, int bottomPadding) {
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
            float lineOffset = guidelineThickness / 2; // ugly hack for line at y=0 (y=inclusiveHeight) to be fully visible
            while (guideline < yMax) {

                // translate into our coordinates
                double q = (guideline - yMin) / yDiff;
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
        }
    }
    private void drawXValues(Canvas canvas, float xScale, float translateX) { // todo: animate (dis)appearance
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

        // find sample size where all texts can be fit
        while (true) { // try sample sizes until we find a suitable one
            float textWidthPx = (float) textLengthX * xWidthPx; // will be >= 32 for the first time
            int requiredSpace = fitTexts(null, firstVisibleX, firstInvisibleX, textLengthX, textWidthPx, Float.NaN, Float.NaN, Float.NaN);
            if (requiredSpace == 1) {
                break;
            } else {
                textLengthX *= 2;
                // continue: we may encounter a longer text on the next pass
            }
        }
        textLengthX *= 2; // keep the distance!

        float textY = height() - textIndent;
        // now draw!
        // fixme: measuring and allocating formatted data for two times
        fitTexts(canvas, firstVisibleX, firstInvisibleX, textLengthX, Float.NaN, translateX, xScale, textY);
    }

    /**
     * @return how many times bigger {@param maxTextWidth} should be
     */
    private int fitTexts(Canvas canvas, float firstVisibleX, float firstInvisibleX, int textLengthX,
                             /*only for measuring*/ float maxTextWidth,
                             /*only for drawing*/ float translateX, float xScale, float y) {
        double[] xValues = data.x.values;
        int length = xValues.length;
        int width = width();

        int firstVisibleXRnd = (int) firstVisibleX / length * length;
        int firstInvisibleXRnd = (int) firstInvisibleX + textLengthX;
        int last = (int) Math.ceil((firstInvisibleXRnd - firstVisibleXRnd) / (float) textLengthX) - 1;
        for (int i = 0; i <= last; i++) {
            int x = firstVisibleXRnd + i * textLengthX;
            float xPos = width * x / length;
            int xIdx = indexOfClosest(normalized, 0, length, xPos);
//            xPos = normalized[xIdx]; // fixme: we seem to have a precision issue; try comparing xPos with normalized[xIdx]

            xValueFormatter.formatValueInto(numberSb, xValues[xIdx]);
            int numberSbLen = numberSb.length();
            float textWidth = numberPaint.measureText(numberSb, 0, numberSbLen);
            if (canvas == null) { // dry run just for measurement
                numberSb.setLength(0);
                if (textWidth > maxTextWidth) {
                    return Math.max(2, Integer.highestOneBit((int) Math.ceil(textWidth / maxTextWidth)));
                }
            } else {
                float xOnScreen = xPos * xScale + translateX;
                float xq = -1.1f * xOnScreen / width + .05f; // [.05; -1.05]
                canvas.drawText(numberSb, 0, numberSbLen, xOnScreen + xq * textWidth, y, numberPaint);
//                canvas.drawLine(xOnScreen, height() - 100, xOnScreen, height(), numberPaint); // debug number placements
                numberSb.setLength(0);
            }
        }
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
            path = new Path();
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

        dirty = false;
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
        dirty = true;
        invalidateSelf();
    }

    public void setColumnVisibleAt(int index, boolean whether) { // TODO: animate
        if (index < 0 || index >= data.columns.length) {
            throw new IndexOutOfBoundsException(String.format("index must be in [0; %d), %d given", data.columns.length, index));
        }
        if (visible.get(index) != whether) {
            visible.set(index, whether);
            invalidateSelf();
        }
    }

    public void setVisibleRange(int startPerMille, int endPerMille) { // TODO: change Y scale depending on visible window
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
            numberPaint.setColor(numberColour);
            numberPaint.setTextSize(textSize);
            numberPaint.setAlpha(paint.getAlpha());
            numberPaint.setColorFilter(paint.getColorFilter());
            numberSb = new StringBuilder(5);
            invalidateSelf();
        } else if (numberPaint.getColor() != numberColour) {
            numberPaint.setColor(numberColour);
            invalidateSelf();
        }
    }

    private static int indexOfClosest(double[] haystack, double needle) {
        int index = Arrays.binarySearch(haystack, needle);
        return index >= 0 ? index : -index - 1;
    }

    private static int indexOfClosest(float[] haystack, float needle) {
        int index = Arrays.binarySearch(haystack, needle);
        return index >= 0 ? index : -index - 1;
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
        if (dirty) normalize();

        float scaledX = (xPos - translateX()) / xScale();
        int length = data.x.values.length;
        return indexOfClosest(normalized, 0, length, scaledX);
    }
    float getXPositionAt(int index) {
        if (dirty) normalize();

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
            dest[i] = visible.get(i) ? cols[i].values[index] : Double.NaN;
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
        if (dirty) normalize();
        Chart.Column[] columns = data.columns;
        int colCnt = columns.length;
        if (dest == null || dest.length != colCnt) {
            dest = new float[colCnt];
        }
        int length = data.x.values.length;

        double yMin = maxBottom;
        double yMax = minTop;
        for (int ci = 0; ci < colCnt; ci++) {
            if (visible.get(ci)) {
                Chart.Column column = columns[ci];
                double min = column.minValue;
                if (min < yMin) yMin = min;
                double max = column.maxValue;
                if (max > yMax) yMax = max;
            }
        }
        double yDiff = yMax - yMin;

        boolean drawNumbers = textSize > 0 && numberPaint != null;
        int bottomPadding = drawNumbers && xValueFormatter != null ? (int) (2 * textSize) : 0;

        int height = height();
        int chartHeight = height - bottomPadding;
        float heightFactor = (float) chartHeight / height;
        for (int i = 0; i < colCnt; i++) {
            if (visible.get(i)) {
                Chart.Column column = columns[i];
                double colYMax = column.maxValue;
                double colYDiff = colYMax - column.minValue;
                float yScale = (float) (colYDiff / yDiff) * heightFactor;
                float translateY = (float) ((yMax - colYMax) / yDiff * chartHeight);
                dest[i] = translateY + normalized[length + i * length + index] * yScale;
            } else {
                dest[i] = Float.NaN;
            }
        }
        return dest;
    }

}
