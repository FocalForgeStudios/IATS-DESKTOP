package com.iatsconnect.ui;
import javafx.scene.Scene; import java.util.Objects;
public final class ThemeManager { private static boolean dark; public static void toggle(Scene s){dark=!dark;apply(s);} public static void apply(Scene s){String css=dark?"/css/dark.css":"/css/light.css";s.getStylesheets().clear();s.getStylesheets().add(Objects.requireNonNull(ThemeManager.class.getResource(css)).toExternalForm());} }
