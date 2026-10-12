package com.iatsconnect.ui.views;

import com.iatsconnect.models.StudentProfile;
import com.iatsconnect.services.ApiException;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Central panel for the {@link StudentProfile} model. Loads the saved profile (cache-backed) and saves edits. */
public final class ProfileView implements View {
    @Override public String id() { return "profile"; }
    @Override public String title() { return "Profile"; }
    @Override public String navLabel() { return "Profile"; }
    @Override public String icon() { return "◉"; }
    @Override public Access access() { return Access.SIGNED_IN; }

    @Override public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();
        var user = ctx.user();
        VBox form = ui.card("Student profile");
        TextField name = new TextField(); name.setPromptText("Name");
        ComboBox<String> course = new ComboBox<>();
        course.getItems().addAll("Software Engineering", "ICT", "Business Administration", "Catering", "Other");
        course.setPromptText("Course");
        TextField cohort = new TextField(); cohort.setPromptText("Cohort / intake (e.g. 1.2)");
        TextField schedule = new TextField(); schedule.setPromptText("Study schedule (e.g. evenings, Mon–Fri)");
        TextField emoji = new TextField(); emoji.setPromptText("Optional emoji");
        Label status = ui.label("Only basic student details are needed.", 12, false);
        VBox notice = new VBox();

        form.getChildren().addAll(notice, name, course, cohort, schedule, emoji, ui.button("Save profile", true, () -> {
            if (name.getText().isBlank() || course.getValue() == null) { status.setText("Name and course are required."); return; }
            StudentProfile p = new StudentProfile(user.userId(), name.getText().trim(), course.getValue(), cohort.getText(),
                schedule.getText(), null, emoji.getText());
            status.setText("Saving…");
            ctx.async(() -> { ctx.data().saveProfile(p); return Boolean.TRUE; },
                ok -> status.setText("Profile saved."),
                e -> { status.setText("Not saved. " + ApiException.wrap(e).userMessage()); ctx.showError("Could not save your profile", e); });
        }), status);

        // Pre-fill from the server, or the offline cache when there is no connection.
        ctx.async(() -> ctx.data().profile(user.userId()), r -> {
            if (r.fromCache()) notice.getChildren().setAll(ui.offlineBanner(r.fetchedAt()));
            r.data().ifPresent(p -> {
                name.setText(nz(p.name()));
                if (p.course() != null) { if (!course.getItems().contains(p.course())) course.getItems().add(p.course()); course.setValue(p.course()); }
                cohort.setText(nz(p.cohort()));
                schedule.setText(nz(p.studySchedule()));
                emoji.setText(nz(p.emoji()));
            });
        }, e -> notice.getChildren().setAll(ui.banner("Your saved profile could not be loaded. " + ApiException.wrap(e).userMessage(), true)));

        return new VBox(16, ui.label("Your profile", 26, true), form);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
