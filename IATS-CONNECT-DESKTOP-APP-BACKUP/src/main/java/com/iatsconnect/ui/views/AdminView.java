package com.iatsconnect.ui.views;

import com.iatsconnect.services.ApiException;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Administrator workspace for publishing news and events. Database RLS remains the real permission check. */
public final class AdminView implements View {
    @Override public String id() { return "admin"; }
    @Override public String title() { return "Administrator"; }
    @Override public String navLabel() { return "Administrator"; }
    @Override public String icon() { return "⚑"; }
    @Override public Access access() { return Access.ADMIN; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        var user = ctx.user();
        VBox page = new VBox(16, ui.label("Administrator workspace", 26, true),
            ui.label("Only the configured administrator can publish official news and events. Database RLS remains the final permission check.", 13, false));
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(14);

        VBox news = ui.card("Publish official news");
        TextField newsTitle = new TextField(); newsTitle.setPromptText("News headline");
        TextArea newsBody = new TextArea(); newsBody.setPromptText("Announcement details"); newsBody.setPrefRowCount(4);
        Label newsStatus = ui.label("Only the administrator can publish.", 11, false);
        news.getChildren().addAll(newsTitle, newsBody, ui.button("Publish news", true, () -> {
            if (newsTitle.getText().isBlank() || newsBody.getText().isBlank()) { newsStatus.setText("Enter a headline and announcement details."); return; }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("category", "news");
            payload.put("author", user.email());
            payload.put("author_role", "admin");
            payload.put("author_id", user.userId());
            payload.put("title", newsTitle.getText().trim());
            payload.put("content", newsBody.getText().trim());
            payload.put("upvotes", 0);
            payload.put("comments_count", 0);
            payload.put("tags", List.of("official"));
            newsStatus.setText("Publishing…");
            ctx.async(() -> ctx.api().rest("POST", "hub_posts", ctx.api().json(payload)),
                ok -> { newsStatus.setText("News published successfully."); newsTitle.clear(); newsBody.clear(); },
                e -> { newsStatus.setText("Not published. " + ApiException.wrap(e).userMessage()); ctx.showError("Could not publish the news item", e); });
        }), newsStatus);

        VBox events = ui.card("Create an official event");
        TextField eventTitle = new TextField(); eventTitle.setPromptText("Event title");
        TextField eventTime = new TextField(); eventTime.setPromptText("Date and time (e.g. 2026-11-02 10:00 AM)");
        TextField location = new TextField(); location.setPromptText("Venue / location");
        TextField tag = new TextField(); tag.setPromptText("Category (e.g. Workshop)");
        Label eventStatus = ui.label("Published events are visible to visitors.", 11, false);
        events.getChildren().addAll(eventTitle, eventTime, location, tag, ui.button("Publish event", true, () -> {
            if (eventTitle.getText().isBlank() || eventTime.getText().isBlank()) { eventStatus.setText("Event title and date/time are required."); return; }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", eventTitle.getText().trim());
            payload.put("host", user.email());
            payload.put("host_id", user.userId());
            payload.put("location", location.getText().trim());
            payload.put("event_time", eventTime.getText().trim());
            payload.put("tag", tag.getText().isBlank() ? "Event" : tag.getText().trim());
            payload.put("attendees", 0);
            payload.put("max_attendees", 100);
            payload.put("attendee_ids", List.of());
            eventStatus.setText("Publishing…");
            ctx.async(() -> ctx.api().rest("POST", "catchups", ctx.api().json(payload)),
                ok -> { eventStatus.setText("Event published successfully."); eventTitle.clear(); eventTime.clear(); location.clear(); tag.clear(); },
                e -> { eventStatus.setText("Not published. " + ApiException.wrap(e).userMessage()); ctx.showError("Could not publish the event", e); });
        }), eventStatus);

        g.add(news, 0, 0);
        g.add(events, 1, 0);
        for (int i = 0; i < 2; i++) { ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(50); g.getColumnConstraints().add(c); }
        page.getChildren().add(g);
        return page;
    }
}
