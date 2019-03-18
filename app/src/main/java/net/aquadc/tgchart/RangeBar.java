package net.aquadc.tgchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.Px;


public final class RangeBar extends View {

    private final Paint paint = new Paint();

    public RangeBar(Context context) {
        super(context);
    }

    // interactions

    private int max = 1000;
    public void setMax(int max) {
        if (max < 1) throw new IllegalArgumentException("max must be > 0, given " + max);
        this.max = max;
        setSelectedRangeInternal(0, max - 1);
    }

    private int selectionStart = 0;
    private int selectionEnd = max;

    private SelectionChangeListener listener;

    public void setSelectedRange(int selectionStart, int selectionEnd) {
        if (selectionStart < 0 || selectionStart >= max || selectionEnd < 0 || selectionEnd >= max) {
            throw new IllegalArgumentException("selection must be in [0; " + selectionEnd + "), given " + selectionStart + "; " + selectionEnd);
        }
        if (selectionStart > selectionEnd) {
            throw new IllegalArgumentException("selectionStart must be <= selectionEnd, given [" + selectionStart + "; " + selectionEnd + ")");
        }
        setSelectedRangeInternal(selectionStart, selectionEnd);
    }
    private void setSelectedRangeInternal(int selectionStart, int selectionEnd) {
        if (this.selectionStart != selectionStart || this.selectionEnd != selectionEnd) {
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            invalidate();
            if (listener != null) {
                listener.onSelectedRangeChanged(selectionStart, selectionEnd);
            }
        }
    }

    @Override protected Parcelable onSaveInstanceState() {
        Bundle ss = new Bundle();
        ss.putParcelable("p", super.onSaveInstanceState());
        ss.putInt("s", selectionStart);
        ss.putInt("e", selectionEnd);
        return ss;
    }
    @Override protected void onRestoreInstanceState(Parcelable state) {
        Bundle ss = (Bundle) state;
        super.onRestoreInstanceState(ss.getParcelable("p"));
        setSelectedRangeInternal(Math.min(ss.getInt("s"), max), Math.min(ss.getInt("e"), max));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            touchAt(event.getX());
            return true;
        } else {
            return false;
        }
    }

    private void touchAt(float x) { // TODO: dragging the whole window without size changes
        float dStart = Math.abs(selectionToPx(selectionStart) - x);
        float dEnd = Math.abs(selectionToPx(selectionEnd) - x);
        boolean movingLeft = dStart < dEnd;
        Rect borders = this.windowBorders;
        int minDistance = 2 * borders.left + 2 * borders.right;
        int width = getWidth();

        float normalX = movingLeft ? Math.min(x, width - minDistance) : Math.max(x, minDistance);
        normalX = Math.min(width, Math.max(0, normalX));

        int newSelection = (int) (normalX / width * max);
        if (movingLeft) {
            if (selectionStart != newSelection) {
                setSelectedRangeInternal(newSelection, Math.max(newSelection + minDistance, selectionEnd));
            }
        } else {
            if (selectionEnd != newSelection) {
                setSelectedRangeInternal(Math.min(newSelection - minDistance, selectionStart), newSelection);
            }
        }
    }

    public interface SelectionChangeListener {
        void onSelectedRangeChanged(int selectionStart, int selectionEnd);
    }

    public void setSelectionChangeListener(SelectionChangeListener listener) {
        this.listener = listener;
    }

    // graphics

    private final Rect windowBorders = new Rect();
    public void setWindowBorders(@Px int left, @Px int top, @Px int right, @Px int bottom) {
        windowBorders.set(left, top, right, bottom);
        invalidate();
    }

    @ColorInt private int dimColour = Color.TRANSPARENT;
    @ColorInt public int getDimColour() {
        return dimColour;
    }
    public void setDimColour(int dimColour) {
        this.dimColour = dimColour;
        invalidate();
    }
    public static final Property<RangeBar, Integer> DIM_COLOUR_PROP = new Property<RangeBar, Integer>(Integer.class, "dimColour") {
        @Override public Integer get(RangeBar object) {
            return object.getDimColour();
        }
        @Override public void set(RangeBar object, Integer value) {
            object.setDimColour(value);
        }
    };

    @ColorInt private int windowBorderColour = Color.TRANSPARENT;
    @ColorInt public int getWindowBorderColour() {
        return windowBorderColour;
    }
    public void setWindowBorderColour(int windowBorderColour) {
        this.windowBorderColour = windowBorderColour;
        invalidate();
    }
    public static final Property<RangeBar, Integer> WINDOW_BORDER_COLOUR_PROP = new Property<RangeBar, Integer>(Integer.class, "windowBorderColour") {
        @Override public Integer get(RangeBar object) {
            return object.getWindowBorderColour();
        }
        @Override public void set(RangeBar object, Integer value) {
            object.setWindowBorderColour(value);
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int height = getHeight();
        paint.setColor(dimColour);
        float leftDimEnd = selectionToPx(selectionStart);
        if (selectionStart > 0) {
            canvas.drawRect(0, 0, leftDimEnd, height, paint);
        }
        float rightDimStart = selectionToPx(selectionEnd);
        if (selectionEnd < max - 1) {
            canvas.drawRect(rightDimStart, 0, getWidth(), height, paint);
        }

        paint.setColor(windowBorderColour);
        Rect windowBorders = this.windowBorders;
        float leftWindowBorderEnd = leftDimEnd + windowBorders.left;
        canvas.drawRect(leftDimEnd, 0, leftWindowBorderEnd, height, paint);
        float rightWindowBorderStart = rightDimStart - windowBorders.right;
        canvas.drawRect(rightWindowBorderStart, 0, rightDimStart, height, paint);
        canvas.drawRect(leftWindowBorderEnd, 0, rightWindowBorderStart, windowBorders.top, paint);
        canvas.drawRect(leftWindowBorderEnd, height - windowBorders.bottom, rightWindowBorderStart, height, paint);
    }

    // common util

    private float selectionToPx(int selectionPerMille) {
        return getWidth() * selectionPerMille / max;
    }

}