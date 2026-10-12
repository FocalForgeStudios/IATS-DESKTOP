package com.iatsconnect.ui;

import com.iatsconnect.cache.DataRepository;
import com.iatsconnect.config.AppConfig;
import com.iatsconnect.services.AuthService;
import com.iatsconnect.services.SupabaseRestClient;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Everything a view may use. Implemented by {@link AppShell}; views never see the shell itself. */
public interface ViewContext {
    AppConfig config();
    SupabaseRestClient api();
    AuthService auth();
    DataRepository data();

    /** Current signed-in user, or null for visitors. */
    AuthService.UserSession user();
    void setUser(AuthService.UserSession user);

    UiKit ui();
    boolean dark();
    void toggleTheme();

    void navigate(String viewId);
    void navigate(String viewId, Map<String, Object> args);
    /** Signs out locally, wipes this user's cached data and returns to the dashboard. */
    void signOut();

    /** Runs {@code work} on a background thread, then delivers the result (or error) on the FX thread. */
    <T> void async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError);

    /** Shows a friendly error dialog for a failed user action. */
    void showError(String action, Throwable error);
}
