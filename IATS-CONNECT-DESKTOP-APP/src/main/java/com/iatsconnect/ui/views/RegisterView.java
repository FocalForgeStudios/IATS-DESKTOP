package com.iatsconnect.ui.views;

import com.iatsconnect.services.ApiException;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import com.iatsconnect.ui.ViewRegistry;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class RegisterView implements View {
    @Override public String id() { return "register"; }
    @Override public String title() { return "Create account"; }
    @Override public boolean centered() { return true; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        VBox box = ui.card("");
        box.setMaxWidth(480);
        box.setPadding(new Insets(28));
        TextField name = new TextField(); name.setPromptText("Full name");
        TextField email = new TextField(); email.setPromptText("Real email address");
        PasswordField pass = new PasswordField(); pass.setPromptText("Create password (at least 8 characters)");
        PasswordField confirm = new PasswordField(); confirm.setPromptText("Confirm password");
        Label status = ui.note("Use an email address you can access.");
        Button[] create = new Button[1];
        create[0] = ui.button("Create account", true, () -> {
            if (name.getText().isBlank() || email.getText().isBlank() || pass.getText().length() < 8) {
                status.setText("Enter your name, email, and a password of at least 8 characters."); return;
            }
            if (!pass.getText().equals(confirm.getText())) { status.setText("Passwords do not match."); return; }
            status.setText("Creating account…");
            create[0].setDisable(true);
            ctx.async(() -> ctx.auth().signUp(name.getText(), email.getText(), pass.getText()), result -> {
                create[0].setDisable(false);
                if (result.emailConfirmationRequired()) {
                    status.setText("Account request received. Check your email for the Supabase confirmation message, then sign in.");
                } else {
                    ctx.setUser(result);
                    ctx.navigate("profile");
                }
            }, e -> {
                create[0].setDisable(false);
                ApiException api = ApiException.wrap(e);
                status.setText(api.userMessage());
                if (api.kind() != ApiException.Kind.CONFLICT && api.kind() != ApiException.Kind.VALIDATION) ctx.showError("Could not create your account", api);
            });
        });
        box.getChildren().addAll(ui.label("Create your account", 26, true), ui.label("Join the IATS community", 13, false),
            name, email, pass, confirm, create[0], status,
            ui.button("Back to sign in", false, () -> ctx.navigate(ViewRegistry.SIGN_IN)),
            ui.button("Cancel / return to dashboard", false, () -> ctx.navigate(ViewRegistry.DASHBOARD)));
        return box;
    }
}
