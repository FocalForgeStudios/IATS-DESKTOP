package com.iatsconnect.ui.views;

import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public final class DashboardView implements View {
    @Override public String id() { return "dashboard"; }
    @Override public String title() { return "Dashboard"; }
    @Override public String navLabel() { return "Dashboard"; }
    @Override public String icon() { return "⌂"; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        VBox page = new VBox(18);

        StackPane hero = new StackPane();
        hero.setMinHeight(165);
        hero.setPadding(new Insets(24));
        hero.setStyle("-fx-background-color:linear-gradient(to right,#06366f,#0a66bd);-fx-background-radius:15;");
        VBox text = new VBox(10);
        Label welcome = new Label("WELCOME TO");
        Label name = new Label("Institute of Advanced Technical Studies (IATS)");
        Label tagline = new Label("Learn  •  Connect  •  Grow Together");
        welcome.setStyle("-fx-text-fill:white;-fx-font-size:13px;");
        name.setStyle("-fx-text-fill:white;-fx-font-size:24px;-fx-font-weight:700;");
        tagline.setStyle("-fx-text-fill:white;-fx-font-size:13px;");
        HBox actions = new HBox(10, ui.button("Explore Groups", true, () -> ctx.navigate("groups")),
            ui.button("View Announcements", false, () -> ctx.navigate("news")));
        text.getChildren().addAll(welcome, name, tagline, actions);
        hero.getChildren().add(text);
        StackPane.setAlignment(text, Pos.CENTER_LEFT);
        page.getChildren().add(hero);

        GridPane stats = new GridPane();
        stats.setHgap(12);
        stats.setVgap(12);
        String[][] data = {{"♙", "Students", "Community members"}, {"♧", "Groups", "Course communities"},
            {"▦", "Events", "Upcoming activities"}, {"▤", "News", "Official updates"}};
        for (int i = 0; i < data.length; i++) {
            VBox s = ui.card("");
            s.getChildren().addAll(ui.label(data[i][0] + "   " + data[i][1], 16, true), ui.label(data[i][2], 11, false));
            stats.add(s, i, 0);
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            stats.getColumnConstraints().add(cc);
        }
        page.getChildren().add(stats);

        GridPane columns = new GridPane();
        columns.setHgap(14);
        columns.setVgap(14);
        VBox quick = ui.card("Quick Access");
        quick.getChildren().addAll(ui.button("♧  Join a Group", false, () -> ctx.navigate("groups")),
            ui.button("▤  Browse News", false, () -> ctx.navigate("news")),
            ui.button("▦  View Events", false, () -> ctx.navigate("events")));
        VBox events = ui.card("Upcoming Events");
        events.getChildren().addAll(ui.label("Events are published by administrators.", 12, false),
            ui.button("Browse Events", false, () -> ctx.navigate("events")));
        columns.add(quick, 0, 0);
        columns.add(events, 1, 0);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            columns.getColumnConstraints().add(cc);
        }
        page.getChildren().add(columns);

        VBox news = ui.card("Latest News");
        news.getChildren().addAll(ui.label("Official announcements from IATS", 13, false),
            ui.button("Read Latest News", false, () -> ctx.navigate("news")));
        page.getChildren().add(news);

        if (!ctx.config().isSupabaseConfigured()) {
            page.getChildren().add(ui.banner("Supabase setup needed: add your publishable/anon key to config.properties to connect live data.", true));
        }
        return page;
    }
}
