package co.proxychecker.commands;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final AtomicReference<ProxyCheckService.CheckHandle> CURRENT = new AtomicReference<>();

    private ProxyCheckCommand() {
    }

    /**
     * @return whether proxies are currently being checked; stays true until a stopped check has wound down
     */
    public static boolean isRunning() {
        return RUNNING.get();
    }

    /**
     * Stops the running check, if any. Its {@code onComplete} callback is then invoked with {@code true}.
     * Safe to call from any thread and when nothing is running.
     */
    public static void stop() {
        ProxyCheckService.CheckHandle handle = CURRENT.get();
        if (handle != null) {
            handle.stop();
        }
    }

    /**
     * Starts checking the given proxies using the current settings.
     *
     * @param proxies    proxies to check
     * @param onResults  receives checked proxies, invoked on the JavaFX application thread
     * @param onComplete invoked once on the JavaFX application thread after every proxy has been reported or
     *                   the check was stopped; receives whether it was stopped early
     * @return false if a check is already running and nothing was started
     */
    public static boolean check(List<String> proxies, Consumer<List<Proxy>> onResults, Consumer<Boolean> onComplete) {
        UserSettings settings = Settings.getConfig();
        return check(proxies, settings, ProxyCheckService.apiTester(new RequestAPI(settings)), Platform::runLater,
                onResults, onComplete);
    }

    static boolean check(List<String> proxies, UserSettings settings, ProxyCheckService.ProxyTester tester,
                         Executor uiExecutor, Consumer<List<Proxy>> onResults, Consumer<Boolean> onComplete) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        AtomicReference<ProxyCheckService.CheckHandle> self = new AtomicReference<>();
        ProxyCheckService.CheckHandle handle = ProxyCheckService.prepare(
                ProxyCheckService.parse(proxies, settings.getProxyType()), settings.getThreads(),
                tester, uiExecutor, onResults, () -> {
                    ProxyCheckService.CheckHandle finished = self.get();
                    CURRENT.compareAndSet(finished, null);
                    RUNNING.set(false);
                    onComplete.accept(finished.isStopped());
                });
        self.set(handle);
        CURRENT.set(handle);
        handle.start();
        return true;
    }
}
