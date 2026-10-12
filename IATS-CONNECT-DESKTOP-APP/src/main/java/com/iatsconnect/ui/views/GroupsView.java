
package com.iatsconnect.ui.views;

import com.iatsconnect.cache.DataRepository;
import com.iatsconnect.models.Group;
import com.iatsconnect.services.ApiException;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Live group browsing, group creation and membership actions backed by Supabase. */
public final class GroupsView implements View {

    @Override
    public String id() {
        return "groups";
    }

    @Override
    public String title() {
        return "Groups";
    }

    @Override
    public String navLabel() {
        return "Groups";
    }

    @Override
    public String icon() {
        return "♧";
    }

    @Override
    public Node build(ViewContext ctx, Map<String, Object> args) {
        UiKit ui = ctx.ui();

        VBox page = new VBox(
            16,
            ui.label("Course communities", 26, true),
            ui.note(
                "Browse course and cohort communities. Joining and creating groups requires a signed-in account."
            )
        );

        // Declare the group list before any callback references it.
        VBox list = ui.card("Available groups");

        if (ctx.user() != null) {
            VBox create = ui.card("Create a group");

            TextField name = new TextField();
            name.setPromptText("Group name (e.g. ICT Year 2)");

            TextField course = new TextField();
            course.setPromptText("Course");

            TextField cohort = new TextField();
            cohort.setPromptText("Cohort / intake (optional)");

            TextField description = new TextField();
            description.setPromptText("What is this group for? (optional)");

            ComboBox<String> visibility = new ComboBox<>();
            visibility.getItems().addAll("public", "private");
            visibility.setValue("public");

            Label status = ui.note(
                "Group owners can manage their own groups."
            );

            create.getChildren().addAll(
                name,
                course,
                cohort,
                description,
                visibility,
                ui.button("Create group", true, () -> {
                    if (name.getText().isBlank()
                            || course.getText().isBlank()) {
                        status.setText(
                            "Enter a group name and course."
                        );
                        return;
                    }

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("name", name.getText().trim());
                    payload.put("course", course.getText().trim());
                    payload.put("cohort", cohort.getText().trim());
                    payload.put(
                        "description",
                        description.getText().trim()
                    );
                    payload.put("visibility", visibility.getValue());
                    payload.put(
                        "created_by",
                        ctx.user().userId()
                    );

                    status.setText("Creating group…");

                    ctx.async(
                        () -> ctx.api().rest(
                            "POST",
                            "groups",
                            ctx.api().json(payload)
                        ),
                        ok -> {
                            status.setText(
                                "Group created. Refreshing the list…"
                            );
                            name.clear();
                            course.clear();
                            cohort.clear();
                            description.clear();
                            load(ctx, list);
                        },
                        err -> {
                            status.setText(
                                "Could not create group: "
                                    + ApiException.wrap(err).userMessage()
                            );
                            ctx.showError("Create group", err);
                        }
                    );
                }),
                status
            );

            page.getChildren().add(create);
        }

        // Add the group list exactly once, for signed-in and guest users.
        page.getChildren().add(list);
        load(ctx, list);

        return page;
    }

    private void load(ViewContext ctx, VBox list) {
        UiKit ui = ctx.ui();

        list.getChildren().setAll(
            ui.label("Available groups", 16, true),
            ui.note("Loading groups…")
        );

        String userId = ctx.user() == null
            ? null
            : ctx.user().userId();

        ctx.async(
            () -> ctx.data().groups(userId),
            result -> show(ctx, list, result),
            error -> {
                list.getChildren().setAll(
                    ui.label("Available groups", 16, true),
                    ui.banner(
                        "Groups could not be loaded. "
                            + ApiException.wrap(error).userMessage(),
                        true
                    ),
                    ui.button(
                        "Try again",
                        false,
                        () -> load(ctx, list)
                    )
                );
            }
        );
    }

    private void show(
            ViewContext ctx,
            VBox list,
            DataRepository.Result<List<Group>> result) {

        UiKit ui = ctx.ui();

        list.getChildren().setAll(
            ui.label("Available groups", 16, true)
        );

        if (result.fromCache()) {
            list.getChildren().add(
                ui.offlineBanner(result.fetchedAt())
            );
        }

        if (result.data().isEmpty()) {
            list.getChildren().add(
                ui.note(
                    "No groups yet. Create the first course community above."
                )
            );
            return;
        }

        for (Group group : result.data()) {
            VBox card = ui.card(group.name());

            String detail = String.join(
                " · ",
                java.util.stream.Stream.of(
                    group.course(),
                    group.cohort() == null
                            || group.cohort().isBlank()
                        ? null
                        : "Cohort " + group.cohort(),
                    group.visibility()
                )
                .filter(value ->
                    value != null && !value.isBlank()
                )
                .toList()
            );

            card.getChildren().add(ui.note(detail));

            if (group.description() != null
                    && !group.description().isBlank()) {
                card.getChildren().add(
                    ui.note(group.description())
                );
            }

            if (ctx.user() != null) {
                HBox actions = new HBox(8);
                actions.setAlignment(Pos.CENTER_LEFT);

                actions.getChildren().add(
                    ui.button(
                        "Join group",
                        true,
                        () -> join(ctx, group, card)
                    )
                );

                if (ctx.user().userId().equals(group.createdBy())) {
                    actions.getChildren().add(
                        ui.button("Delete group", false, () -> {
                            ctx.async(
                                () -> ctx.api().rest(
                                    "DELETE",
                                    "groups?id=eq." + group.id()
                                        + "&created_by=eq."
                                        + ctx.user().userId(),
                                    null
                                ),
                                ok -> load(ctx, list),
                                error -> ctx.showError(
                                    "Delete group",
                                    error
                                )
                            );
                        })
                    );
                }

                card.getChildren().add(actions);
            } else {
                card.getChildren().add(
                    ui.button(
                        "Sign in to join",
                        false,
                        () -> ctx.navigate(
                            com.iatsconnect.ui.ViewRegistry.SIGN_IN
                        )
                    )
                );
            }

            list.getChildren().add(card);
        }
    }

    private void join(
            ViewContext ctx,
            Group group,
            VBox card) {

        UiKit ui = ctx.ui();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", group.id());
        payload.put("user_id", ctx.user().userId());
        payload.put("role", "member");

        ctx.async(
            () -> ctx.api().rest(
                "POST",
                "group_memberships?on_conflict=group_id,user_id",
                ctx.api().json(payload)
            ),
            ok -> {
                card.getChildren().add(
                    ui.banner("You joined this group.", false)
                );
            },
            error -> {
                String message = ApiException.wrap(error)
                    .userMessage();

                String lowerMessage = message.toLowerCase();

                if (lowerMessage.contains("duplicate")
                        || lowerMessage.contains("conflict")) {
                    card.getChildren().add(
                        ui.note("You are already a member.")
                    );
                } else {
                    card.getChildren().add(
                        ui.banner(
                            "Could not join group: " + message,
                            true
                        )
                    );
                    ctx.showError("Join group", error);
                }
            }
        );
    }
}