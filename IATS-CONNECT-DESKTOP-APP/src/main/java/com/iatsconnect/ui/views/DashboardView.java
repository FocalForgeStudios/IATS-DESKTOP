package com.iatsconnect.ui.views;

import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class DashboardView implements View {
    @Override
    public String id() {
        return "dashboard";
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String navLabel() {
        return "Dashboard";
    }

    @Override
    public String icon() {
        return "⌂";
    }

    @Override
    public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();

        VBox page = new VBox(22);
        page.setPadding(new Insets(2, 2, 24, 2));
        page.setFillWidth(true);

        // Hero section
        StackPane hero = new StackPane();
        hero.setMinHeight(235);
        hero.setPrefHeight(235);
        hero.setMaxWidth(Double.MAX_VALUE);
        hero.setStyle(
            "-fx-background-color:#25282E;" +
            "-fx-background-radius:16px;"
        );

        String photoUrl =
            "https://images.unsplash.com/photo-1523050854058-8df90110c9f1" +
            "?auto=format&fit=crop&w=1600&q=85";

        ImageView photo = new ImageView();
        photo.setPreserveRatio(false);
        photo.setSmooth(true);
        photo.setFitHeight(235);

        try {
            Image image = new Image(photoUrl, true);
            photo.setImage(image);
            hero.widthProperty().addListener((obs, oldWidth, newWidth) ->
                photo.setFitWidth(newWidth.doubleValue())
            );
            photo.setFitWidth(900);
        } catch (RuntimeException ignored) {
            // The styled background remains visible if image loading fails.
        }

        Region shade = new Region();
        shade.setStyle(
            "-fx-background-color:rgba(15,17,21,0.72);" +
            "-fx-background-radius:16px;"
        );

        VBox heroText = new VBox(11);
        heroText.setPadding(new Insets(26));
        heroText.setMaxWidth(Double.MAX_VALUE);
        heroText.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = new Label("INSTITUTE OF ADVANCED TECHNICAL STUDIES");
        eyebrow.setStyle(
            "-fx-text-fill:#E7A1A6;" +
            "-fx-font-size:10px;" +
            "-fx-font-weight:700;"
        );

        Label heading = new Label("Learn. Connect. Grow.");
        heading.setWrapText(true);
        heading.setStyle(
            "-fx-text-fill:#FFFFFF;" +
            "-fx-font-size:29px;" +
            "-fx-font-weight:800;"
        );

        Label description = new Label(
            "Your IATS community, all in one place."
        );
        description.setWrapText(true);
        description.setStyle(
            "-fx-text-fill:#E0E1E4;" +
            "-fx-font-size:14px;"
        );

        HBox heroActions = new HBox(10);
        heroActions.setAlignment(Pos.CENTER_LEFT);
        heroActions.getChildren().addAll(
            ui.button(
                "Explore groups",
                true,
                () -> ctx.navigate("groups")
            ),
            ui.button(
                "Latest announcements",
                false,
                () -> ctx.navigate("news")
            )
        );

        heroText.getChildren().addAll(
            eyebrow,
            heading,
            description,
            heroActions
        );

        hero.getChildren().addAll(photo, shade, heroText);
        StackPane.setAlignment(heroText, Pos.CENTER_LEFT);
        page.getChildren().add(hero);

        // Welcome heading
        VBox intro = new VBox(5);
        intro.getChildren().addAll(
            ui.label("Your community at a glance", 21, true),
            ui.note("Discover campus updates, connect with other students, and keep up with IATS activities.")
        );
        page.getChildren().add(intro);

        // Quick-link cards: these are navigation shortcuts, not fabricated counts.
        GridPane shortcuts = new GridPane();
        shortcuts.setHgap(12);
        shortcuts.setVgap(12);

        String[][] items = {
            {"♧", "Groups", "Find your learning community", "groups"},
            {"◉", "Study buddies", "Connect with fellow learners", "study-buddies"},
            {"▤", "Announcements", "Read official IATS updates", "news"},
            {"▦", "Events", "Explore campus activities", "events"}
        };

        for (int i = 0; i < items.length; i++) {
            final String destination = items[i][3];

            VBox card = ui.card("");
            card.setSpacing(9);
            card.setMinHeight(125);

            Label symbol = ui.label(items[i][0], 22, true);
            Label title = ui.label(items[i][1], 15, true);
            Label detail = ui.note(items[i][2]);

            card.getChildren().addAll(symbol, title, detail);
            card.setOnMouseClicked(event -> ctx.navigate(destination));
            card.setStyle(
                card.getStyle() +
                "-fx-cursor:hand;"
            );

            shortcuts.add(card, i % 2, i / 2);
        }

        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setHgrow(Priority.ALWAYS);
            shortcuts.getColumnConstraints().add(column);
        }

        page.getChildren().add(shortcuts);

        // Helpful next steps
        GridPane lower = new GridPane();
        lower.setHgap(14);
        lower.setVgap(14);

        VBox community = ui.card("Stay connected");
        community.getChildren().addAll(
            ui.note("Find people with shared interests and take part in your learning community."),
            ui.button(
                "Discover the community",
                false,
                () -> ctx.navigate("groups")
            ),
            ui.button(
                "Find study buddies",
                false,
                () -> ctx.navigate("study-buddies")
            ),
            ui.button(
                "Open messages",
                false,
                () -> ctx.navigate("messages")
            )
        );

        VBox updates = ui.card("Campus updates");
        updates.getChildren().addAll(
            ui.note("Keep up with announcements and activities published on IATS Connect."),
            ui.button(
                "Read announcements",
                false,
                () -> ctx.navigate("news")
            ),
            ui.button(
                "Browse events",
                false,
                () -> ctx.navigate("events")
            )
        );

        lower.add(community, 0, 0);
        lower.add(updates, 1, 0);

        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setHgrow(Priority.ALWAYS);
            lower.getColumnConstraints().add(column);
        }

        page.getChildren().add(lower);

        if (!ctx.config().isSupabaseConfigured()) {
            page.getChildren().add(
                ui.banner(
                    "Supabase setup needed: add your publishable/anon key to config.properties to connect live data.",
                    true
                )
            );
        }

        return page;
    }
}