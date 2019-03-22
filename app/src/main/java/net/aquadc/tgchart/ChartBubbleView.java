package net.aquadc.tgchart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.ColorInt;
import net.aquadc.tgchart.card.RoundRectDrawableWithShadow;


public final class ChartBubbleView extends View implements View.OnClickListener {

    private int currentXIndex = -1; // something is showing if != null

    final RoundRectDrawableWithShadow bg;

    private final Paint paint;
    private final TextPaint textPaint;
    private final int bubbleInsets; // distance between bound and usable inner space

    public ChartBubbleView(Context context) {
        super(context);
        float dp = getResources().getDisplayMetrics().density;
        bg = new RoundRectDrawableWithShadow(getResources(), Color.WHITE, 8 * dp, 3 * dp, 4 * dp); // TODO: maybe use RoundRectDrawable on 21+
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bubbleInsets = (int) (16 * dp);

        setAlpha(0f);
        setOnClickListener(this);
    }
    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        animate().alpha(pressed ? 1f : 0f).setDuration(150).setListener(pressed ? activate : clearXValue).start();
    }
    private final AnimatorListenerAdapter clearXValue = new AnimatorListenerAdapter() {
        @Override public void onAnimationEnd(Animator animation) {
            currentXIndex = -1;
            invalidate();
        }
    };
    private final AnimatorListenerAdapter activate = new AnimatorListenerAdapter() {
        @Override public void onAnimationEnd(Animator animation) {
            activated = true;
        }
    };

    boolean activated = false;
    @Override public void onClick(View v) {
        if (!activated) {
            Toast.makeText(getContext(), "Press and hold for details.", Toast.LENGTH_SHORT).show();
        }
    }

    private float xValueTextSize;
    private float yValueTextSize;
    private float yLabelTextSize;
    private float yValueHSpacing;

    public void setTextSizes(float xValue, float yValue, float yLabel, float yValueHSpacing) {
        this.xValueTextSize = xValue;
        this.yValueTextSize = yValue;
        this.yLabelTextSize = yLabel;
        this.yValueHSpacing = yValueHSpacing;
        invalidate();
    }

    /*public static final Property<ChartExtrasView, Integer> BG_COLOUR_PROPERTY = new Property<ChartExtrasView, Integer>(Integer.class, "bgColour") {
        @Override public Integer get(ChartExtrasView object) {
            return object.bg.getColor().getDefaultColor();
        }
        @Override public void set(ChartExtrasView object, Integer value) {
            object.bg.setColor(ColorStateList.valueOf(value));
            object.invalidate();
        }
    };*/
    @ColorInt private int xValueColour;
    /*public static final Property<ChartExtrasView, Integer> X_VALUE_COLOUR = new Property<ChartExtrasView, Integer>(Integer.class, "xValueColour") {
        @Override public Integer get(ChartExtrasView object) {
            return object.xValueColour;
        }
        @Override public void set(ChartExtrasView object, Integer value) {
            object.xValueColour = value;
        }
    };*/
    public void setColours(@ColorInt int bg, @ColorInt int xValue) {
        this.bg.setColor(bg);
        this.xValueColour = xValue;
        invalidate();
    }

    private ChartDrawable chart;
    public void setChart(ChartDrawable chart) {
        this.chart = chart;
    }

    private ChartDrawable.ValueFormatter xFormatter;
    private ChartDrawable.ValueFormatter yFormatter;
    public void setFormatters(ChartDrawable.ValueFormatter x, ChartDrawable.ValueFormatter y) {
        this.xFormatter = x;
        this.yFormatter = y;
    }

    @ColorInt private int guidelineColour;
    public void setGuidelineColour(@ColorInt int guidelineColour) {
        this.guidelineColour = guidelineColour;
        invalidate();
    }

    private float guidelineThickness;
    private float dotRadius;
    private float dotStrokeThickness;
    public void setSizes(float guidelineThickness, float dotRadius, float dotStrokeThickness) {
        this.guidelineThickness = guidelineThickness;
        this.dotRadius = dotRadius;
        this.dotStrokeThickness = dotStrokeThickness;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (chart == null) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                currentXIndex = chart.getIndexAt(event.getX());
                invalidate();
        }

        return super.onTouchEvent(event); // handle 'pressed' state
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chart != null && xFormatter != null && yFormatter != null && currentXIndex != -1) {
            drawBubble(canvas);
        }
    }

    private double[] yValues;
    private String[] formattedYValues; // TODO: use a single SB and slices instead
    private int[] lengths;
    private float[] yPositions;
    private void drawBubble(Canvas canvas) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

        StringBuilder sb = new StringBuilder();

        // prepare data
        double xValue = chart.getXValueAt(currentXIndex);
        xFormatter.formatValueInto(sb, xValue);
        String formattedXValue = sb.toString();
        sb.setLength(0);

        yValues = chart.getYValuesAt(currentXIndex, yValues);
        int yValueCount = yValues.length;
        if (formattedYValues == null || formattedYValues.length != yValueCount) {
            formattedYValues = new String[yValueCount];
            lengths = new int[yValueCount];
        }
        for (int i = 0; i < yValueCount; i++) {
            double yValue = yValues[i];
            if (Double.isNaN(yValue)) {
                formattedYValues[i] = null;
            } else {
                yFormatter.formatValueInto(sb, yValue);
                formattedYValues[i] = sb.toString();
                sb.setLength(0);
            }
        }
        yPositions = chart.getYPositionsAt(currentXIndex, yPositions);

        int xValueHeight = (int) (1.2f * xValueTextSize);
        int yValueHeight = (int) (1.5f * yValueTextSize);
        int yLabelHeight = (int) (1.5f * yLabelTextSize);

        int xPos = (int) chart.getXPositionAt(currentXIndex);
        int top = height / 16; // todo: could be placed in the most free chart part
        int bottom = top + /* balloonHeight: */ xValueHeight + yValueHeight + yLabelHeight;

        // draw guideline
        paint.setColor(guidelineColour);
        paint.setStrokeWidth(guidelineThickness);
        canvas.drawLine(xPos, bottom, xPos, height, paint);

        // measure
        setupTextPaint(xValueTextSize, xValueColour);
        int balloonWidth = 0;
        Chart.Column[] columns = chart.data.columns;
        for (int i = 0; i < columns.length; i++) {
            float yPosition = yPositions[i];
            if (!Float.isNaN(yPosition)) { // i. e. the col is visible
                Chart.Column column = columns[i];
                setupTextPaint(yValueTextSize, column.colour);
                int valueLen = (int) textPaint.measureText(formattedYValues[i]);
                setupTextPaint(yLabelTextSize, column.colour);
                int labelLen = (int) textPaint.measureText(column.name);
                balloonWidth += (lengths[i] = Math.max(valueLen, labelLen)) + yValueHSpacing;

                // draw dots
                paint.setColor(bg.getColor());
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(xPos, yPosition, dotRadius, paint);

                paint.setColor(column.colour);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dotStrokeThickness);
                canvas.drawCircle(xPos, yPosition, dotRadius, paint);
            }
        }
        balloonWidth -= yValueHSpacing; // last spacing is odd
        setupTextPaint(xValueTextSize, xValueColour);
        balloonWidth = Math.max(balloonWidth, (int) textPaint.measureText(formattedXValue));

        // layout
        int leftMin = bubbleInsets * 9 / 10;
        int rightMax = width - leftMin;

        int left = xPos;
        int right = left + balloonWidth;
        if (left < leftMin) {
            int diff = leftMin - left;
            left += diff;
            right += diff;
        } else if (right > rightMax) {
            int diff = right - rightMax;
            left -= diff;
            right -= diff;
        }

        // draw bubble
        bg.setBounds(left - bubbleInsets, top - bubbleInsets, right + bubbleInsets, bottom + bubbleInsets);
        bg.draw(canvas);

        setupTextPaint(xValueTextSize, xValueColour);
        canvas.drawText(formattedXValue, left, top + xValueHeight, textPaint);
        int currentLeft = left;
        for (int i = 0; i < columns.length; i++) {
            String formattedYValue = formattedYValues[i];
            if (formattedYValue != null) { // the col is visible
                Chart.Column column = columns[i];
                setupTextPaint(yValueTextSize, column.colour);
                canvas.drawText(formattedYValue, currentLeft, top + xValueHeight + yValueHeight, textPaint);
                setupTextPaint(yLabelTextSize, column.colour);
                canvas.drawText(column.name, currentLeft, top + xValueHeight + yValueHeight + yLabelHeight, textPaint);
                currentLeft += lengths[i] + yValueHSpacing;
            }
        }
    }
    private void setupTextPaint(float size, @ColorInt int colour) {
        textPaint.setTextSize(size);
        textPaint.setColor(colour);
    }
}
