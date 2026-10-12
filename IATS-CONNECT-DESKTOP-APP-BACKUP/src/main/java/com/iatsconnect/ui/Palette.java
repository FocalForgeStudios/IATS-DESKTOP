package com.iatsconnect.ui;

/** Colour tokens for the light and dark themes. */
public record Palette(String bg, String panel, String text, String muted, String border, String blue, String red) {
    public static final Palette LIGHT = new Palette("#f3f7fc", "#ffffff", "#132b4d", "#6b7d96", "#dce6f2", "#0757b8", "#ef2638");
    public static final Palette DARK = new Palette("#07182e", "#0c2442", "#f2f6ff", "#a7bad4", "#1e3d61", "#0757b8", "#ef2638");

    public static Palette of(boolean dark) { return dark ? DARK : LIGHT; }
}
