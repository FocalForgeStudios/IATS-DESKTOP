package com.iatsconnect.ui.views;

import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import com.iatsconnect.ui.ViewRegistry;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Simple message screen. Registered once per "coming soon" sidebar entry with fixed text, and once as the
 * generic {@code "notice"} view that reads {@code title} and {@code message} from the navigation arguments.
 */
public final class NoticeView implements View {
    private final String id, title, navLabel, icon, message;

    public NoticeView(String id, String title, String navLabel, String icon, String message) {
        this.id = id; this.title = title; this.navLabel = navLabel; this.icon = icon; this.message = message;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public String navLabel() { return navLabel; }
    @Override public String icon() { return icon; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        String heading = String.valueOf(args.getOrDefault("title", title));
        String body = String.valueOf(args.getOrDefault("message", message));
        VBox box = ui.card(heading);
        Label l = ui.label(body, 13, false);
        l.setWrapText(true);
        box.getChildren().addAll(l, ui.button("Back to dashboard", true, () -> ctx.navigate(ViewRegistry.DASHBOARD)));
        return box;
    }
}
