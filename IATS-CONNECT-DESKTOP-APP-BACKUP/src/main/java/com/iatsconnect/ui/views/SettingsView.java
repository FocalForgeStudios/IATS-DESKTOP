package com.iatsconnect.ui.views;

import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

public final class SettingsView implements View {
    @Override public String id() { return "settings"; }
    @Override public String title() { return "Settings"; }
    @Override public String navLabel() { return "Settings"; }
    @Override public String icon() { return "⚙"; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        VBox page = ui.card("Settings");
        page.getChildren().addAll(ui.label("Appearance", 16, true),
            ui.button("Switch to " + (ctx.dark() ? "light" : "dark") + " theme", false, ctx::toggleTheme),
            ui.label("Supabase connection", 16, true),
            ui.note(ctx.config().isSupabaseConfigured() ? "Publishable/anon key is configured."
                : "Supabase key not configured. Copy config.properties.example to config.properties and add the publishable/anon key."),
            ui.label("Offline data", 16, true),
            ui.note(ctx.config().cacheEnabled()
                ? "Previously loaded news, events, groups and your profile are saved in " + ctx.config().cacheDir()
                    + " and shown when you are offline. Private data is removed when you sign out."
                : "Offline caching is turned off (cache.enabled=false)."));
        return page;
    }
}
