package com.iatsconnect.ui.views;

import com.iatsconnect.models.Session;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

/** Central panel for the signed-in {@link Session}: who is signed in and how to leave. Tokens are never shown. */
public final class SessionView implements View {
    @Override public String id() { return "session"; }
    @Override public String title() { return "Account"; }
    @Override public Access access() { return Access.SIGNED_IN; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        var u = ctx.user();
        Session s = new Session(u.accessToken(), u.refreshToken(), u.userId(), u.email());
        VBox card = ui.card("Signed-in account");
        card.getChildren().addAll(ui.label("Email: " + s.email(), 13, false),
            ui.label("Role: " + (u.admin() ? "Administrator" : "Student / member"), 13, false),
            ui.note("Your sign-in is renewed automatically while you use the app. If it can't be renewed you'll be asked to sign in again."),
            ui.button("Edit profile", false, () -> ctx.navigate("profile")),
            ui.button("Sign out", true, ctx::signOut));
        return new VBox(16, ui.label("Account", 26, true), card);
    }
}
