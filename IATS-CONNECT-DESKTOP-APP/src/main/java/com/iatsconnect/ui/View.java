package com.iatsconnect.ui;

import java.util.Map;
import javafx.scene.Node;

/**
 * A self-contained screen shown in the shell's central area.
 *
 * <p>To add a screen: implement this interface and register it in {@link ViewRegistry#defaults()}.
 * The shell takes care of the sidebar entry, access checks, scrolling, theming and error handling.
 */
public interface View {

    /** Who may open the view. The shell redirects to sign-in when the requirement is not met. */
    enum Access { PUBLIC, SIGNED_IN, ADMIN }

    /** Unique id used by {@link ViewContext#navigate(String)}. */
    String id();

    /** Heading used for the window/section; also the active-state key in the sidebar. */
    String title();

    /** Sidebar label, or null for views reached only through navigation (sign-in, notices, ...). */
    default String navLabel() { return null; }

    default String icon() { return ""; }

    default Access access() { return Access.PUBLIC; }

    /** Centre the content in the window (sign-in style) instead of top-aligning it. */
    default boolean centered() { return false; }

    /** Builds the content. Runs on the FX thread: return quickly and load data with {@link ViewContext#async}. */
    Node build(ViewContext ctx, Map<String, Object> args);

    /** Called when the view is replaced; release listeners or timers here. */
    default void dispose() {}
}
