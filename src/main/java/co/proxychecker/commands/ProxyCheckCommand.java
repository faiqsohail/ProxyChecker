package co.proxychecker.commands;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.application.Platform;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.Settings;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.core.ProxyCheckService;

/**
 * GUI entry point for checking proxies: runs {@link ProxyCheckService} with the current settings,
 * reports back on the JavaFX application thread and tracks whether a check is running.
 */
public final class ProxyCheckCommand {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private ProxyCheckCommand() {
    }

    /**
     * @return whether proxies are currently being checked
     */
    public static boolean isRunning() {
        return RUNNING.get();
    }

    /**
     * Starts checking the given proxies using the current settings.
     *
     * @param proxies    proxies to check
     * @param onResults  receives checked proxies, invoked on the JavaFX application thread
     * @param onComplete invoked once on the JavaFX application thread after every proxy has been reported
     * @return false if a check is already running and nothing was started
     */
    public static boolean check(List<String> proxies, Consumer<List<Proxy>> onResults, Runnable onComplete) {
        UserSettings settings = Settings.getConfig();
        return check(proxies, settings, ProxyCheckService.apiTester(new RequestAPI(settings)), Platform::runLater,
                onResults, onComplete);
    }

    static boolean check(List<String> proxies, UserSettings settings, ProxyCheckService.ProxyTester tester,
                         Executor uiExecutor, Consumer<List<Proxy>> onResults, Runnable onComplete) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        ProxyCheckService.check(ProxyCheckService.parse(proxies, settings.getProxyType()), settings.getThreads(),
                tester, uiExecutor, onResults, () -> {
                    RUNNING.set(false);
                    onComplete.run();
                });
        return true;
    }
}
