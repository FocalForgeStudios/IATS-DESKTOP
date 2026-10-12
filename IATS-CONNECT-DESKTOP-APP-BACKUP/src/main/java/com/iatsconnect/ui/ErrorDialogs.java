package com.iatsconnect.ui;

import com.iatsconnect.services.ApiException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/** User-friendly error dialogs. Safe to call from any thread. */
public final class ErrorDialogs {
    private static boolean showing;

    private ErrorDialogs() {}

    /** @param action what the user was trying to do, e.g. "Could not save your profile" */
    public static void show(String action, Throwable error) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> show(action, error)); return; }
        if (showing) return; // avoid a stack of identical dialogs when several requests fail together
        ApiException api = ApiException.wrap(error);
        System.err.println("[IATS] " + action + ": " + api.kind() + " - " + api.getMessage()
            + (api.getCause() != null ? " (cause: " + api.getCause() + ")" : ""));

        Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
        alert.setTitle(api.title());
        alert.setHeaderText(action);
        alert.setContentText(api.userMessage() + hint(api));

        String technical = api.getMessage() + (api.getCause() != null ? "\n" + api.getCause() : "");
        TextArea details = new TextArea(technical);
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(4);
        alert.getDialogPane().setExpandableContent(new VBox(6, new Label("Technical details (for support):"), details));

        showing = true;
        alert.setOnHidden(e -> showing = false);
        alert.show();
    }

    private static String hint(ApiException e) {
        return switch (e.kind()) {
            case OFFLINE, TIMEOUT -> "\n\nYour changes were not saved. Previously loaded data may still be available.";
            case SERVER -> "\n\nThis is usually temporary.";
            default -> "";
        };
    }
}
