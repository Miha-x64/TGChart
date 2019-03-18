package net.aquadc.tgchart;

import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;

enum ColourMode {
    LIGHT(android.R.style.Theme_DeviceDefault_Light,
            0xFF_426382, 0xFF_517DA2, 0xFF_F0F0F0, 0xFF_FFFFFF, 0xFF_E3E3E3,
            0xFF_3896D4, 0xFF_222222, 0x1E_000000,
            0x88_ECF2F4, 0x88_B7CFE1, 0x0E_000000, 0xFF_96A2AA,
            0xFF_FFFFFF, 0xFF_222222),
    DARK(android.R.style.Theme_DeviceDefault,
            0xFF_1A242E, 0xFF_212D3B, 0xFF_151E27, 0xFF_1D2733, 0xFF_141C25,
            0xFF_7BC4FB, 0xFF_FFFFFF, 0x76_000000,
            0x88_16202A, 0x88_395D79, 0xFF_161F2B, 0xFF_506372,
            0xFF_202B38, 0xFF_E5EFF5);

    @StyleRes final int baseTheme;
    @ColorInt final int statusBar;
    @ColorInt final int toolbar;
    @ColorInt final int window;
    @ColorInt final int sheet;
    @ColorInt final int shadow;
    @ColorInt final int titleText;
    @ColorInt final int itemText;
    @ColorInt final int itemDivider;
    @ColorInt final int rangeDim; // TODO: find more transparent colours
    @ColorInt final int rangeWindowBorder;
    @ColorInt final int guideline;
    @ColorInt final int numbers;
    @ColorInt final int cardBg;
    @ColorInt final int cardText;


    ColourMode(@StyleRes int baseTheme,
               @ColorInt int statusBar, @ColorInt int toolbar, @ColorInt int window,
               int sheet, int shadow, @ColorInt int titleText, @ColorInt int itemText, @ColorInt int itemDivider,
               @ColorInt int rangeDim, int rangeWindowBorder, int guideline, int numbers, int cardBg, int cardText) {
        this.baseTheme = baseTheme;
        this.statusBar = statusBar;
        this.toolbar = toolbar;
        this.window = window;
        this.sheet = sheet;
        this.shadow = shadow;
        this.titleText = titleText;
        this.itemText = itemText;
        this.itemDivider = itemDivider;
        this.rangeDim = rangeDim;
        this.rangeWindowBorder = rangeWindowBorder;
        this.guideline = guideline;
        this.numbers = numbers;
        this.cardBg = cardBg;
        this.cardText = cardText;
    }

    private static final ColourMode[] VALUES = values();
    public ColourMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

}
