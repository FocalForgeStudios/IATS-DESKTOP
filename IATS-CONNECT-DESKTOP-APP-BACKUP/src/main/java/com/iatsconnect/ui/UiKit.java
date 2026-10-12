package com.iatsconnect.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Themed widget factory shared by every view, so screens look consistent without extending a base class. */
public final class UiKit {
    private final Palette p;

    public UiKit(Palette palette) { this.p = palette; }

    public Palette palette() { return p; }

    public Label label(String text, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + p.text() + ";-fx-font-size:" + size + "px;" + (bold ? "-fx-font-weight:700;" : ""));
        return l;
    }

    /** Wrapping, muted-colour label for secondary text and status lines. */
    public Label note(String text) {
        Label l = label(text, 12, false);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill:" + p.muted() + ";-fx-font-size:12px;");
        return l;
    }

    public Button button(String text, boolean primary, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setCursor(Cursor.HAND);
        b.setStyle("-fx-background-color:" + (primary ? p.red() : p.panel()) + ";-fx-text-fill:" + (primary ? "white" : p.text())
            + ";-fx-font-weight:700;-fx-background-radius:9;-fx-padding:11 14;-fx-border-color:" + p.border() + ";-fx-border-radius:9;");
        b.setOnAction(e -> action.run());
        return b;
    }

    public VBox card(String title) {
        VBox b = new VBox(12);
        b.setPadding(new Insets(16));
        b.setStyle("-fx-background-color:" + p.panel() + ";-fx-background-radius:13;-fx-border-color:" + p.border() + ";-fx-border-radius:13;");
        if (title != null && !title.isBlank()) b.getChildren().add(label(title, 16, true));
        return b;
    }

    /** Inline notice, e.g. "Showing saved data from 3 Oct". Warning style uses the red accent. */
    public Label banner(String text, boolean warning) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        String accent = warning ? p.red() : p.blue();
        l.setStyle("-fx-text-fill:" + p.text() + ";-fx-font-size:12px;-fx-padding:10 12;-fx-background-radius:9;-fx-border-radius:9;"
            + "-fx-border-color:" + accent + ";-fx-background-color:" + p.panel() + ";");
        return l;
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    /** Shown above data that came from the offline cache instead of the server. */
    public Label offlineBanner(Instant fetchedAt) {
        return banner("You're offline or the server is unreachable. Showing saved data from " + WHEN.format(fetchedAt)
            + ". Reconnect and reopen this page to refresh.", true);
    }
}
