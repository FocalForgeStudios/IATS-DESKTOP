package com.iatsconnect.ui;

/** Shared colour tokens for the light and premium charcoal themes. */
public record Palette(
    String bg,
    String panel,
    String text,
    String muted,
    String border,
    String blue,
    String red
) {
    public static final Palette LIGHT = new Palette(
        "#F5F4F1",
        "#FFFFFF",
        "#20242A",
        "#737983",
        "#E4E1DB",
        "#536B86",
        "#C94E59"
    );

    public static final Palette DARK = new Palette(
        "#15171B",
        "#1E2126",
        "#F3F1EC",
        "#A8ADB5",
        "#30343B",
        "#7F91A7",
        "#DF626B"
    );

    public static Palette of(boolean dark) {
        return dark ? DARK : LIGHT;
    }
}