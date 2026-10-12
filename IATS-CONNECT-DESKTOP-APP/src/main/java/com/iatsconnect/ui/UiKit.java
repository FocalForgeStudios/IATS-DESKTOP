package com.iatsconnect.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Shared component factory for a consistent, premium application interface.
 */
public final class UiKit {
    private final Palette p;

    public UiKit(Palette palette) {
        this.p = palette;
    }

    public Palette palette() {
        return p;
    }

    public Label label(String text, int size, boolean bold) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(
            "-fx-text-fill:" + p.text() + ";" +
            "-fx-font-size:" + size + "px;" +
            (bold ? "-fx-font-weight:700;" : "")
        );
        return label;
    }

    /** Secondary text for explanations, status messages and helper text. */
    public Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(
            "-fx-text-fill:" + p.muted() + ";" +
            "-fx-font-size:12px;" +
            "-fx-line-spacing:3px;"
        );
        return label;
    }

    /** Creates a consistent primary or secondary action button. */
    public Button button(String text, boolean primary, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setCursor(Cursor.HAND);

        String background = primary ? p.red() : p.panel();
        String foreground = primary ? "#FFFFFF" : p.text();
        String border = primary ? p.red() : p.border();

        button.setStyle(
            "-fx-background-color:" + background + ";" +
            "-fx-text-fill:" + foreground + ";" +
            "-fx-font-size:13px;" +
            "-fx-font-weight:700;" +
            "-fx-background-radius:9px;" +
            "-fx-border-color:" + border + ";" +
            "-fx-border-radius:9px;" +
            "-fx-padding:11px 16px;"
        );

        button.setOnAction(event -> action.run());
        return button;
    }

    /** Creates a rounded, bordered surface for related content. */
    public VBox card(String title) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setFillWidth(true);

        card.setStyle(
            "-fx-background-color:" + p.panel() + ";" +
            "-fx-background-radius:14px;" +
            "-fx-border-color:" + p.border() + ";" +
            "-fx-border-radius:14px;"
        );

        if (title != null && !title.isBlank()) {
            card.getChildren().add(label(title, 16, true));
        }

        return card;
    }

    /** A notice for setup instructions, offline status or warnings. */
    public Label banner(String text, boolean warning) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);

        String accent = warning ? p.red() : p.blue();

        label.setStyle(
            "-fx-text-fill:" + p.text() + ";" +
            "-fx-font-size:12px;" +
            "-fx-padding:12px 14px;" +
            "-fx-background-radius:10px;" +
            "-fx-border-radius:10px;" +
            "-fx-border-color:" + accent + ";" +
            "-fx-background-color:" + p.panel() + ";"
        );

        return label;
    }

    private static final DateTimeFormatter WHEN =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    /** Notice displayed when a page is using cached data. */
    public Label offlineBanner(Instant fetchedAt) {
        return banner(
            "You're offline or the server is unreachable. " +
            "Showing saved data from " + WHEN.format(fetchedAt) +
            ". Reconnect and reopen this page to refresh.",
            true
        );
    }
}