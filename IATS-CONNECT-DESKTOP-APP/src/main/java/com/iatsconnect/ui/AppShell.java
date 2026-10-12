package com.iatsconnect.ui;

import com.iatsconnect.cache.DataRepository;
import com.iatsconnect.config.AppConfig;
import com.iatsconnect.services.AuthService;
import com.iatsconnect.services.SupabaseRestClient;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Application frame: sidebar, top bar and a central area that hosts whichever {@link View} is active.
 *
 * <p>The shell knows nothing about individual screens. Screens are looked up by id in the
 * {@link ViewRegistry}, so adding or replacing a screen never requires editing this class.
 */
public final class AppShell implements ViewContext {
    private final AppConfig config;
    private final SupabaseRestClient api;
    private final AuthService auth;
    private final DataRepository data;
    private final ViewRegistry registry;
    private final BorderPane root = new BorderPane();
    private boolean dark = false;
    private AuthService.UserSession user;

    private View currentView;
    private Map<String, Object> currentArgs = Map.of();

    public AppShell(AppConfig config, SupabaseRestClient api, AuthService auth, DataRepository data) {
        this(config, api, auth, data, ViewRegistry.defaults());
    }

    public AppShell(AppConfig config, SupabaseRestClient api, AuthService auth, DataRepository data, ViewRegistry registry) {
        this.config = config;
        this.api = api;
        this.auth = auth;
        this.data = data;
        this.registry = registry;
        auth.setSessionExpiredListener(() -> Platform.runLater(this::onSessionExpired));
        navigate(ViewRegistry.DASHBOARD);
    }

    public BorderPane getRoot() { return root; }

    // ------------------------------------------------------------------ ViewContext

    @Override public AppConfig config() { return config; }
    @Override public SupabaseRestClient api() { return api; }
    @Override public AuthService auth() { return auth; }
    @Override public DataRepository data() { return data; }
    @Override public AuthService.UserSession user() { return user; }
    @Override public void setUser(AuthService.UserSession user) { this.user = user; }
    @Override public UiKit ui() { return new UiKit(Palette.of(dark)); }
    @Override public boolean dark() { return dark; }

    @Override public void toggleTheme() {
        dark = !dark;
        render(currentView, currentArgs);
    }

    @Override public void navigate(String viewId) { navigate(viewId, Map.of()); }

    @Override public void navigate(String viewId, Map<String, Object> args) {
        View view = registry.find(viewId).orElse(null);
        if (view == null) {
            ErrorDialogs.show("Could not open that page", new IllegalArgumentException("Unknown view: " + viewId));
            return;
        }
        boolean allowed = switch (view.access()) {
            case PUBLIC -> true;
            case SIGNED_IN -> user != null;
            case ADMIN -> user != null && user.admin();
        };
        if (!allowed) { view = registry.find(ViewRegistry.SIGN_IN).orElseThrow(); args = Map.of(); }
        render(view, args == null ? Map.of() : args);
    }

    @Override public void signOut() {
        String userId = user == null ? null : user.userId();
        auth.signOut();
        data.forgetUser(userId);
        user = null;
        navigate(ViewRegistry.DASHBOARD);
    }

    @Override public <T> void async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Async.run(work, onSuccess, onError);
    }

    @Override public void showError(String action, Throwable error) { ErrorDialogs.show(action, error); }

    // ------------------------------------------------------------------ rendering

    private void onSessionExpired() {
        if (user == null) return;
        String userId = user.userId();
        user = null;
        data.forgetUser(userId);
        ErrorDialogs.show("You have been signed out", new com.iatsconnect.services.ApiException(
            com.iatsconnect.services.ApiException.Kind.UNAUTHORIZED, "Your session has expired. Please sign in again."));
        navigate(ViewRegistry.SIGN_IN);
    }

    private void render(View view, Map<String, Object> args) {
        if (view == null) return;
        if (currentView != null && currentView != view) {
            try { currentView.dispose(); } catch (RuntimeException ignored) { /* a faulty view must not block navigation */ }
        }
        currentView = view;
        currentArgs = args;
        UiKit ui = ui();
        Palette p = ui.palette();

        Node content;
        try {
            content = view.build(this, args);
        } catch (RuntimeException e) {
            ErrorDialogs.show("This page could not be displayed", e);
            VBox failed = ui.card("Something went wrong");
            failed.getChildren().addAll(ui.note("This page failed to load. You can go back to the dashboard and try again."),
                ui.button("Back to dashboard", true, () -> navigate(ViewRegistry.DASHBOARD)));
            content = failed;
        }

        VBox page = new VBox(content);
        page.setPadding(new Insets(24));
        page.setFillWidth(true);
        Node center = page;
        if (view.centered()) {
            StackPane box = new StackPane(page);
            StackPane.setAlignment(page, Pos.CENTER);
            page.setMaxWidth(560);
            center = box;
        }
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(view.centered());
        scroll.setStyle("-fx-background:" + p.bg() + ";-fx-background-color:transparent;");
        String cssPath = dark ? "/css/dark.css" : "/css/light.css";
var cssResource = getClass().getResource(cssPath);

if (cssResource != null) {
    root.getStylesheets().setAll(cssResource.toExternalForm());
}

        root.setStyle("-fx-background-color:" + p.bg() + ";-fx-font-family:'Segoe UI';");
        BorderPane frame = new BorderPane();
        frame.setLeft(sidebar(ui, view));
        frame.setTop(topBar(ui));
        frame.setCenter(scroll);
        root.setCenter(frame);
    }

    private VBox sidebar(UiKit ui, View active) {
        Palette p = ui.palette();
        VBox side = new VBox(10);
        side.setPadding(new Insets(22, 14, 18, 14));
        side.setPrefWidth(230);
        side.setStyle("-fx-background-color:" + p.panel() + ";-fx-border-color:transparent " + p.border() + " transparent transparent;");
        Label brand = ui.label("⬟  IATS Connect", 20, true);
        Label subtitle = new Label("Institute of Advanced\nTechnical Studies");
        subtitle.setStyle("-fx-text-fill:" + p.muted() + ";-fx-font-size:11px;-fx-padding:0 0 14 38;");
        side.getChildren().addAll(brand, subtitle, new Separator());

        boolean adminDivider = false;
        for (View v : registry.navigable()) {
            if (v.access() == View.Access.ADMIN) {
                if (user == null || !user.admin()) continue;
                if (!adminDivider) { side.getChildren().add(new Separator()); adminDivider = true; }
            }
            boolean on = v == active;
            Button b = new Button(v.icon() + "    " + v.navLabel());
            b.setAlignment(Pos.CENTER_LEFT);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setCursor(Cursor.HAND);
            b.setStyle("-fx-background-color:" + (on ? p.red() : "transparent") + ";-fx-text-fill:" + (on ? "white" : p.text())
                + ";-fx-font-size:13px;-fx-font-weight:600;-fx-background-radius:8;-fx-padding:12 13;");
            b.setOnAction(e -> navigate(v.id()));
            side.getChildren().add(b);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        side.getChildren().addAll(spacer,
            ui.button((dark ? "☀" : "☾") + "   " + (dark ? "Light theme" : "Dark theme"), false, this::toggleTheme));
        return side;
    }

    private HBox topBar(UiKit ui) {
        Palette p = ui.palette();
        HBox top = new HBox(16);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(14, 24, 14, 24));
        top.setStyle("-fx-background-color:" + p.panel() + ";-fx-border-color:transparent transparent " + p.border() + " transparent;");
        Label title = ui.label("Institute of Advanced Technical Studies (IATS)", 18, true);
        HBox.setHgrow(title, Priority.ALWAYS);
        TextField search = new TextField();
        search.setPromptText("Search IATS Connect...");
        search.setPrefWidth(250);
        Button account = ui.button(user == null ? "Public visitor" : user.email(), false, () -> { if (user != null) navigate("session"); });
        account.setMaxWidth(Region.USE_PREF_SIZE);
        account.setDisable(user == null);
        top.getChildren().addAll(title, search, account);
        top.getChildren().add(user == null
            ? ui.button("Sign in", true, () -> navigate(ViewRegistry.SIGN_IN))
            : ui.button("Sign out", false, this::signOut));
        return top;
    }
}
