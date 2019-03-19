package net.aquadc.tgchart;


import java.text.DateFormat;
import java.util.Date;

public final class DateFormatter implements ChartDrawable.ValueFormatter {
    private final Date date;
    private final DateFormat format;

    public DateFormatter(Date sharedDate, DateFormat format) {
        this.date = sharedDate;
        this.format = format;
    }

    @Override
    public void formatValueInto(StringBuilder sb, double value) {
        date.setTime((long) value);
        sb.append(format.format(date));
    }
}
