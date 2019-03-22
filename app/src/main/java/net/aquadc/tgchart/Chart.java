package net.aquadc.tgchart;


import android.content.Context;
import android.graphics.Color;
import android.util.JsonReader;
import android.util.Pair;
import androidx.annotation.ColorInt;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

public final class Chart {

    public final Column x;
    public final Column[] columns;

    public Chart(Column x, Column[] columns) {
        this.x = x;
        this.columns = columns;
    }

    public static final class Column {
        public final String name;
        @ColorInt public final int colour;
        public final double[] values;
        public final double minValue;
        public final double maxValue;

        public Column(String name, int colour, double[] values, double minValue, double maxValue) {
            this.name = name;
            this.colour = colour;
            this.values = values;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
    }

    public static Chart readTestChart(Context context) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(context.getAssets().open("chart_data.json")));
            ArrayList<Chart> ch = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
                ch.add(readChart(reader));
            }
            reader.endArray();

            return ch.get(new Random().nextInt(ch.size()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Chart readChart(JsonReader reader) throws IOException {
        /* format:
        {
            "columns": [
                [ "id", ints... ], ...
            ],
            "types": {
                "id": "line" | "x"
            },
            "names": {
                "id": "name", ...
            },
            "colors": {
                "id": "#rrggbb", ...
            }
        }
         */

        HashMap<String, Pair<double[], double[]>> columns = null;
        String xColId = null;
        HashMap<String, String> names = null;
        HashMap<String, String> colours = null;

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "columns":
                    columns = readColumns(reader);
                    break;

                case "types":
                    xColId = readXColId(reader);
                    break;

                case "names":
                    names = readMap(reader);
                    break;

                case "colors":
                    colours = readMap(reader);
                    break;

                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (columns == null) throw new NoSuchElementException("'columns' object was not provided.");
        if (xColId == null) throw new NoSuchElementException("'types' object was not provided.");
        if (names == null) throw new NoSuchElementException("'names' object was not provided.");
        if (colours == null) throw new NoSuchElementException("'colours' object was not provided.");

        Column xCol = null;
        Column[] cols = new Column[columns.size() - 1];
        int colIdx = 0;
        for (Map.Entry<String, Pair<double[], double[]>> col : columns.entrySet()) {
            String id = col.getKey();
            Pair<double[], double[]> pair = col.getValue();
            double[] value = pair.first;
            double[] minMax = pair.second;
            double min = minMax[0];
            double max = minMax[1];
            if (xColId.equals(id)) {
                xCol = new Column("x", Color.TRANSPARENT, value, min, max);
            } else {
                String name = names.get(id);
                if (name == null) throw new NoSuchElementException("no name provided for the column " + id);
                int colour = Color.parseColor(colours.get(id)); // throws NPE for null colour
                cols[colIdx++] = new Column(name, colour, value, min, max);
            }
        }

        if (colIdx != columns.size() - 1) throw new AssertionError();
        return new Chart(xCol, cols);
    }

    private static HashMap<String, Pair<double[], double[]>> readColumns(JsonReader reader) throws IOException {
        HashMap<String, Pair<double[], double[]>> columns = new HashMap<>();

        reader.beginArray();

        if (reader.hasNext()) {
            // read 1st data set
            reader.beginArray();
            String name = reader.nextString();
            DoubleArrayList data = new DoubleArrayList();
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            while (reader.hasNext()) {
                double d = reader.nextDouble();
                data.add(d);
                if (d < min) min = d;
                if (d > max) max = d;
            }
            reader.endArray();

            double[] values = data.toArray();
            columns.put(name, new Pair<>(values, new double[] { min, max }));

            // read remaining data sets, assuming they have the same length
            int size = values.length;
            while (reader.hasNext()) {
                reader.beginArray();

                name = reader.nextString();
                values = new double[size];
                min = Float.MAX_VALUE;
                max = Float.MIN_VALUE;
                for (int i = 0; i < size; i++) {
                    double v = reader.nextDouble();
                    values[i] = v;
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
                reader.endArray();

                columns.put(name, new Pair<>(values, new double[]{ min, max }));
            }
        }

        reader.endArray();

        return columns;
    }

    private static String readXColId(JsonReader reader) throws IOException {
        String xColId = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            String type = reader.nextString();
            switch (type) {
                case "x":
                    xColId = name;
                    break;

                case "line":
                    // no-op
                    break;

                default:
                    throw new IllegalArgumentException("unsupported column type: " + type);
            }
        }
        reader.endObject();

        if (xColId == null) {
            throw new NoSuchElementException("'x' column is not provided.");
        }

        return xColId;
    }

    private static HashMap<String, String> readMap(JsonReader reader) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            map.put(reader.nextName(), reader.nextString());
        }
        reader.endObject();
        return map;
    }

}
