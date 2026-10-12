package co.proxychecker.startup;

import co.proxychecker.cli.Cli;
import co.proxychecker.cli.CliOptions;

/**
 * Entry point of the packaged jar. Starts the command line mode for {@code --cli} (without
 * initialising JavaFX at all), otherwise the GUI. A main class that does not extend
 * {@code Application} is also required to start JavaFX from a fat jar on the class path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        if (CliOptions.isCliInvocation(args)) {
            Cli.main(args);
        } else {
            ProxyChecker.main(args);
        }
    }
}
