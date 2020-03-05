package net.aquadc.tgchart;

import android.animation.TypeEvaluator;

public final class DoubleEvaluator implements TypeEvaluator<Double> {

    public static final DoubleEvaluator INSTANCE = new DoubleEvaluator();

    private DoubleEvaluator() {
    }

    @Override public Double evaluate(float fraction, Double startValue, Double endValue) {
        if (startValue.isNaN() || startValue.isInfinite() || endValue.isNaN() || endValue.isInfinite()) {
            throw new AssertionError("can't evaluate [" + startValue + "; " + endValue + "], got invalid values");
        }
        double diff = endValue - startValue;
        return startValue + fraction * diff;
    }

}
