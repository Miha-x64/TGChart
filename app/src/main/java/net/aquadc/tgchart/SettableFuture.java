package net.aquadc.tgchart;


import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.webkit.ValueCallback;

import java.util.concurrent.atomic.AtomicReference;


@SuppressWarnings("unchecked") // fuck 'Generic array creation'
public final class SettableFuture<V> {

    private final AtomicReference<Pair<V, ValueCallback<V>[]>> ref = new AtomicReference<>();

    public SettableFuture() {
    }

    public boolean set(V value) {
        Pair<V, ValueCallback<V>[]> prev, next;
        do {
            prev = ref.get();
            if (prev == null) {
                next = new Pair<>(value, null);
            } else if (prev.first == null) {
                next = new Pair<>(value, prev.second);
            } else {
                return false;
            }
        } while (!ref.compareAndSet(prev, next));

        if (prev != null && prev.second != null) {
            notify(prev.second, value);
        }

        return true;
    }

    public void subscribe(ValueCallback<V> callback) {
        Pair<V, ValueCallback<V>[]> prev, next;
        do {
            prev = ref.get();
            if (prev == null) {
                next = new Pair<>(null, (ValueCallback<V>[]) new ValueCallback[]{ callback });
            } else if (prev.second == null) {
                next = new Pair<>(prev.first, (ValueCallback<V>[]) new ValueCallback[]{ callback });
            } else {
                next = new Pair<>(prev.first, glue(prev.second, callback));
            }
        } while (!ref.compareAndSet(prev, next));

        if (prev != null && prev.first != null) {
            notify(new ValueCallback[] { callback }, prev.first);
        }
    }

    public boolean unsubscribe(ValueCallback<V> victim) {
        Pair<V, ValueCallback<V>[]> prev, next;
        do {
            prev = ref.get();
            if (prev == null || prev.second == null) {
                return false; // nothing_to_do_here.jpg
            } else {
                ValueCallback<V>[] newCallbacks = without(prev.second, victim);
                if (prev.second == newCallbacks) return false; // not found
                next = new Pair<>(prev.first, newCallbacks);
            }
        } while (!ref.compareAndSet(prev, next));
        return true;
    }


    private static <V> void notify(final ValueCallback<V>[] callbacks, final V value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (ValueCallback<V> callback : callbacks) {
                callback.onReceiveValue(value);
            }
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    for (ValueCallback<V> callback : callbacks) {
                        callback.onReceiveValue(value);
                    }
                }
            });
        }
    }

    private static <V> ValueCallback<V>[] glue(ValueCallback<V>[] src, ValueCallback<V> newElement) {
        int len = src.length;
        ValueCallback[] dst = new ValueCallback[len + 1];
        System.arraycopy(src, 0, dst, 0, len);
        dst[len] = newElement;
        return dst;
    }

    private static <V> ValueCallback<V>[] without(ValueCallback<V>[] src, ValueCallback<V> victim) {
        int idx = -1;
        int len = src.length;
        for (int i = 0; i < len; i++) {
            if (src[i] == victim) {
                idx = i;
                break;
            }
        }
        if (idx == -1) return src;

        ValueCallback[] dst = new ValueCallback[len - 1];
        System.arraycopy(src, 0, dst, 0, idx);
        System.arraycopy(src, idx+1, dst, idx, len-idx-1);
        return dst;
    }

}
