package co.proxychecker.startup;

/**
 * Entry point of the packaged jar. A main class that does not extend {@code Application} is required to
 * start JavaFX from a fat jar on the class path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ProxyChecker.main(args);
    }
}
