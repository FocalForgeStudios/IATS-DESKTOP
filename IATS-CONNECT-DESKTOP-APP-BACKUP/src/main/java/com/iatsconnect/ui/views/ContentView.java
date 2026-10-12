package com.iatsconnect.ui.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.iatsconnect.cache.DataRepository;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import com.iatsconnect.services.ApiException;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Generic read-only list backed by one REST query (used for News and Events). Works offline from the cache. */
public final class ContentView implements View {
    private final String id, title, navLabel, icon, description, cacheKey, path;

    public ContentView(String id, String title, String navLabel, String icon, String description, String cacheKey, String path) {
        this.id = id; this.title = title; this.navLabel = navLabel; this.icon = icon;
        this.description = description; this.cacheKey = cacheKey; this.path = path;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public String navLabel() { return navLabel; }
    @Override public String icon() { return icon; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        VBox page = new VBox(16, ui.label(title, 26, true), ui.label(description, 13, false));
        VBox results = ui.card("Published items");
        page.getChildren().add(results);
        load(ctx, results);
        return page;
    }

    private void load(ViewContext ctx, VBox results) {
        UiKit ui = ctx.ui();
        results.getChildren().setAll(ui.label("Published items", 16, true), ui.label("Loading content…", 12, false));
        ctx.async(() -> ctx.data().feed(cacheKey, path), r -> show(ctx, results, r), e -> {
            ApiException api = ApiException.wrap(e);
            Label msg = ui.banner("Content could not be loaded. " + api.userMessage(), true);
            results.getChildren().setAll(ui.label("Published items", 16, true), msg, ui.button("Try again", false, () -> load(ctx, results)));
        });
    }

    private void show(ViewContext ctx, VBox results, DataRepository.Result<JsonNode> r) {
        UiKit ui = ctx.ui();
        results.getChildren().setAll(ui.label("Published items", 16, true));
        if (r.fromCache()) results.getChildren().add(ui.offlineBanner(r.fetchedAt()));
        JsonNode n = r.data();
        if (n.isArray() && n.size() > 0) {
            for (JsonNode item : n) {
                String heading = item.path("title").asText("Untitled");
                String body = item.path("content").asText(item.path("event_time").asText(item.path("location").asText("")));
                VBox row = ui.card(heading);
                Label text = ui.label(body, 12, false);
                text.setWrapText(true);
                row.getChildren().add(text);
                results.getChildren().add(row);
            }
        } else {
            results.getChildren().add(ui.label("No published items yet.", 12, false));
        }
    }
}
