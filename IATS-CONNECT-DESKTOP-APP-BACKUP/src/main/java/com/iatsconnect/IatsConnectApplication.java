package com.iatsconnect;

import com.iatsconnect.cache.DataRepository;
import com.iatsconnect.cache.LocalCache;
import com.iatsconnect.config.AppConfig;
import com.iatsconnect.services.AuthService;
import com.iatsconnect.services.SupabaseRestClient;
import com.iatsconnect.ui.AppShell;
import com.iatsconnect.ui.ErrorDialogs;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class IatsConnectApplication extends Application {
    @Override public void start(Stage stage) {
        // Last line of defence: an unexpected exception shows a dialog instead of silently killing a thread.
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> ErrorDialogs.show("An unexpected error occurred", e));

        AppConfig config = new AppConfig();
        SupabaseRestClient client = new SupabaseRestClient(config);
        AuthService auth = new AuthService(client, config);
        LocalCache cache = new LocalCache(config.cacheDir(), config.cacheEnabled());
        DataRepository data = new DataRepository(client, cache);

        AppShell shell = new AppShell(config, client, auth, data);
        Scene scene = new Scene(shell.getRoot(), 1440, 900);
        stage.setTitle("IATS Connect — Institute of Advanced Technical Studies");
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
