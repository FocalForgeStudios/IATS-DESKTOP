package com.iatsconnect.ui;

import com.iatsconnect.ui.views.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ordered catalogue of every screen in the app. Registration order is the sidebar order. */
public final class ViewRegistry {
    private final Map<String, View> views = new LinkedHashMap<>();

    public ViewRegistry register(View view) {
        if (views.putIfAbsent(view.id(), view) != null) throw new IllegalArgumentException("Duplicate view id: " + view.id());
        return this;
    }

    public Optional<View> find(String id) { return Optional.ofNullable(views.get(id)); }

    /** Views that appear in the sidebar, in registration order. */
    public List<View> navigable() {
        List<View> out = new ArrayList<>();
        for (View v : views.values()) if (v.navLabel() != null) out.add(v);
        return out;
    }

    public static final String DASHBOARD = "dashboard";
    public static final String SIGN_IN = "sign-in";

    /** The app's built-in screens. */
    public static ViewRegistry defaults() {
        return new ViewRegistry()
            .register(new DashboardView())
            .register(new ContentView("news", "Latest News", "News", "▤", "News is published by IATS administrators only.",
                "news", "hub_posts?select=id,title,content,category,created_at&order=created_at.desc"))
            .register(new ContentView("events", "Upcoming Events", "Events", "▦", "Events are managed by IATS administrators only.",
                "events", "catchups?select=id,title,host,location,event_time,tag,created_at&order=created_at.desc"))
            .register(new GroupsView())
            .register(new MessagesView())
            .register(new StudyBuddiesView())
            .register(new ProfileView())
            .register(new SettingsView())
            .register(new AdminView())
            .register(new SessionView())
            .register(new SignInView())
            .register(new RegisterView())
            .register(new NoticeView("notice", "Notice", null, "", ""));
    }
}
