package net.aquadc.tgchart.card;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public final class RoundRectHelper {

    private RoundRectHelper() {}

    // contents are copied from CardViewBaseImpl

    private static final RectF mCornerRect = new RectF(); // changed: moved mCornerRect from CardViewBaseImpl

    public static void drawRoundRect(Canvas canvas, RectF bounds, float cornerRadius,
                              Paint paint) {
        final float twoRadius = cornerRadius * 2;
        final float innerWidth = bounds.width() - twoRadius - 1;
        final float innerHeight = bounds.height() - twoRadius - 1;
        if (cornerRadius >= 1f) {
            // increment corner radius to account for half pixels.
            float roundedCornerRadius = cornerRadius + .5f;
            mCornerRect.set(-roundedCornerRadius, -roundedCornerRadius, roundedCornerRadius,
                    roundedCornerRadius);
            int saved = canvas.save();
            canvas.translate(bounds.left + roundedCornerRadius,
                    bounds.top + roundedCornerRadius);
            canvas.drawArc(mCornerRect, 180, 90, true, paint);
            canvas.translate(innerWidth, 0);
            canvas.rotate(90);
            canvas.drawArc(mCornerRect, 180, 90, true, paint);
            canvas.translate(innerHeight, 0);
            canvas.rotate(90);
            canvas.drawArc(mCornerRect, 180, 90, true, paint);
            canvas.translate(innerWidth, 0);
            canvas.rotate(90);
            canvas.drawArc(mCornerRect, 180, 90, true, paint);
            canvas.restoreToCount(saved);
            //draw top and bottom pieces
            canvas.drawRect(bounds.left + roundedCornerRadius - 1f, bounds.top,
                    bounds.right - roundedCornerRadius + 1f,
                    bounds.top + roundedCornerRadius, paint);

            canvas.drawRect(bounds.left + roundedCornerRadius - 1f,
                    bounds.bottom - roundedCornerRadius,
                    bounds.right - roundedCornerRadius + 1f, bounds.bottom, paint);
        }
        // center
        canvas.drawRect(bounds.left, bounds.top + cornerRadius,
                bounds.right, bounds.bottom - cornerRadius , paint);
    }

}
