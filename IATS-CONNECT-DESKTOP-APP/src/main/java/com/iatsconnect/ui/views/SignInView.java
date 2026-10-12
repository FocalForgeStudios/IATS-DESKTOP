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

public final class SignInView implements View {
    @Override public String id() { return ViewRegistry.SIGN_IN; }
    @Override public String title() { return "Sign in"; }
    @Override public boolean centered() { return true; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        VBox box = ui.card("");
        box.setMaxWidth(460);
        box.setPadding(new Insets(28));
        TextField email = new TextField(); email.setPromptText("Email address");
        PasswordField pass = new PasswordField(); pass.setPromptText("Password");
        Label status = ui.note("Use your registered email and password.");
        Button[] submit = new Button[1];

        Runnable signIn = () -> {
            if (email.getText().isBlank() || pass.getText().isBlank()) { status.setText("Enter both your email and password."); return; }
            status.setText("Signing in…");
            submit[0].setDisable(true);
            ctx.async(() -> ctx.auth().signIn(email.getText(), pass.getText()), session -> {
                ctx.setUser(session);
                ctx.navigate(ViewRegistry.DASHBOARD);
            }, e -> {
                submit[0].setDisable(false);
                ApiException api = ApiException.wrap(e);
                status.setText(api.userMessage());
                // Wrong password/typo is explained inline; connectivity and server problems deserve a dialog.
                if (api.kind() != ApiException.Kind.UNAUTHORIZED && api.kind() != ApiException.Kind.VALIDATION) ctx.showError("Could not sign in", api);
            });
        };
        submit[0] = ui.button("Sign in", true, signIn);
        pass.setOnAction(e -> signIn.run());

        box.getChildren().addAll(ui.label("Welcome back", 27, true), ui.label("Sign in to continue to IATS Connect", 13, false),
            email, pass, submit[0],
            ui.button("Forgot password?", false, () -> resetPassword(ctx, email.getText(), status)),
            ui.button("Create account", false, () -> ctx.navigate("register")), status,
            ui.button("Continue browsing without signing in", false, () -> ctx.navigate(ViewRegistry.DASHBOARD)));
        return box;
    }

    private void resetPassword(ViewContext ctx, String email, Label status) {
        if (email == null || email.isBlank()) { status.setText("Enter your email address first, then select Forgot password."); return; }
        status.setText("Requesting reset email…");
        ctx.async(() -> { ctx.auth().requestPasswordReset(email); return Boolean.TRUE; },
            ok -> status.setText("If that email belongs to an account, Supabase will send password-reset instructions. Check your email."),
            e -> { status.setText("Could not request reset. " + ApiException.wrap(e).userMessage()); });
    }
}
