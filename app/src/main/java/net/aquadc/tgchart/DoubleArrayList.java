package net.aquadc.tgchart;

import java.util.Arrays;


public final class DoubleArrayList {

    private double[] elementData;

    private int size;

    public DoubleArrayList() {
        elementData = new double[10];
        size = 0;
    }

    public void add(double e) {
        ensureCapacityInternal(size + 1);
        elementData[size++] = e;
    }

    public double[] toArray() {
        return elementData.length == size
                ? elementData
                : Arrays.copyOf(elementData, size);
    }

    private void ensureCapacityInternal(int minCapacity) {
        if (minCapacity - elementData.length > 0) {
            // overflow-conscious code
            int oldCapacity = elementData.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1);
            if (newCapacity - minCapacity < 0)
                newCapacity = minCapacity;
            elementData = Arrays.copyOf(elementData, newCapacity);
        }
    }

}
