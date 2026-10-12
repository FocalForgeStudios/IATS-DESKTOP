package com.iatsconnect.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Background work for blocking service calls, keeping the JavaFX thread free. */
public final class Async {
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "iats-worker-" + COUNT.incrementAndGet());
        t.setDaemon(true); // never keep the JVM alive after the window closes
        return t;
    });

    private Async() {}

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        POOL.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Throwable t) {
                deliver(() -> onError.accept(t));
                return;
            }
            deliver(() -> onSuccess.accept(result));
        });
    }

    private static void deliver(Runnable r) {
        Runnable safe = () -> {
            try { r.run(); }
            catch (RuntimeException e) { ErrorDialogs.show("Something went wrong", e); }
        };
        try { Platform.runLater(safe); } catch (IllegalStateException toolkitStopped) { /* app is closing */ }
    }
}
