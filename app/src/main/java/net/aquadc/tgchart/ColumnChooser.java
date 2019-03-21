package net.aquadc.tgchart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.Px;

@SuppressLint("ViewConstructor") // fuck you, I don't need LayoutInflater's dirty magic
final class ColumnChooser extends ListView {

    private final Drawable checkMark;
    private final int hPad;

    public ColumnChooser(Context context) {
        super(context);
        this.checkMark = Util.drawableFromTheme(context.getApplicationContext(), android.R.style.Theme_DeviceDefault_Light, android.R.attr.listChoiceIndicatorMultiple);
        this.hPad = Util.dp(context, 16);
        setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    }

    void setData(Chart.Column[] cols, OnItemClickListener listener) {
        setAdapter(new ChooserAdapter(getContext(), cols));
        for (int i = 0, size = cols.length; i < size; i++) {
            setItemChecked(i, true);
        }
        setOnItemClickListener(listener);
    }

    ColorDrawable divider;
    public void setDividerColour(@ColorInt int colour) {
        if (divider == null) {
            divider = new ColorDrawable(colour);
            setDivider(new DividerDrawable(divider, hPad + checkMark.getIntrinsicWidth() + hPad));
        } else {
            divider.setColor(colour);
            setDivider(getDivider());
        }
    }

    private static final class DividerDrawable extends InsetDrawable {
        DividerDrawable(Drawable actual, @Px int leftPad) {
            super(actual, leftPad, 0, 0, 0);
        }
        @Override public int getIntrinsicHeight() {
            return 1;
        }
    }

    public static final Property<ColumnChooser, Integer> DIVIDER_COLOUR_PROPERTY = new Property<ColumnChooser, Integer>(Integer.class, "dividerColour") {
        @Override public Integer get(ColumnChooser object) {
            Drawable divider = object.getDivider();
            return divider instanceof DividerDrawable ? object.divider.getColor() : Color.TRANSPARENT /* UNKNOWN */;
        }
        @Override public void set(ColumnChooser object, Integer value) {
            object.setDividerColour(value);
        }
    };

    public static final Property<ColumnChooser, Integer> TEXT_COLOUR = new Property<ColumnChooser, Integer>(Integer.class, "textColour") {
        @Override public Integer get(ColumnChooser object) {
            return ((ChooserAdapter) object.getAdapter()).textColor;
        }
        @Override public void set(ColumnChooser object, Integer value) {
            ListAdapter adapter = object.getAdapter();
            if (adapter != null) {
                int colour = value;
                for (int i = 0, size = object.getChildCount(); i < size; i++) {
                    ((TextView) object.getChildAt(i)).setTextColor(colour);
                }
                ((ChooserAdapter) adapter).textColor = colour;
            }
        }
    };

    private final /*inner*/ class ChooserAdapter extends BaseAdapter {

        private final Context context;
        private final Chart.Column[] items;
        @ColorInt int textColor;

        private ChooserAdapter(Context context, Chart.Column[] items) {
            this.context = context;
            this.items = items;
        }

        @Override public int getCount() {
            return items.length;
        }
        @Override public Object getItem(int position) {
            return items[position];
        }
        @Override public long getItemId(int position) {
            return position;
        }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = convertView == null ? createView() : (TextView) convertView;
            textView.setText(items[position].name);
            ((Drawable) textView.getTag()).setColorFilter(items[position].colour, PorterDuff.Mode.SRC_ATOP);
            return textView;
        }

        private TextView createView() {
            CheckedTextView v = new CheckedTextView(context);
            v.setLayoutParams(new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Util.dp(context, 54)));
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setCheckMarkDrawable(null);

            // get a non-shared drawable to mutate its state
            Drawable left = checkMark.getConstantState().newDrawable();
            v.setCompoundDrawablesWithIntrinsicBounds(left, null, null, null);
            v.setTag(left);

            v.setPadding(hPad, 0, hPad, 0);
            v.setCompoundDrawablePadding(hPad);

            v.setTextColor(textColor);

            return v;
        }

    }

}
