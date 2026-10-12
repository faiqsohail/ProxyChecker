package co.proxychecker.ProxyChecker.components;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks the GitHub repository for a newer release of the application.
 */
public class UpdateChecker {

    private static final Logger LOG = Logger.getLogger(UpdateChecker.class.getName());
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String TAG_PATH = "/releases/tag/";

    private String repoVersion;

    /**
     * Checks whether a newer version is available. Never throws: any network or parsing problem
     * simply means no update is reported.
     */
    public boolean isUpdateAvailable() {
        try {
            return compareVersions(getRepoVersion(), Settings.getApplicationVersion()) > 0;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.INFO, "Unable to check for updates", e);
            return false;
        }
    }

    /**
     * Gets the latest version tagged on the repository by resolving the redirect of the
     * {@code releases/latest} page, using a HEAD request with timeouts so start up can never hang.
     */
    public String getRepoVersion() throws IOException {
        if (repoVersion == null) {
            HttpURLConnection connection = (HttpURLConnection)
                    new URL(Settings.getApplicationRepo() + "releases/latest").openConnection();
            try {
                connection.setRequestMethod("HEAD");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MILLIS);
                connection.setReadTimeout(TIMEOUT_MILLIS);
                connection.getResponseCode();
                repoVersion = extractTag(connection.getHeaderField("Location"));
            } finally {
                connection.disconnect();
            }
        }
        return repoVersion;
    }

    static String extractTag(String location) throws IOException {
        if (location == null || !location.contains(TAG_PATH)) {
            throw new IOException("Unexpected releases/latest redirect: " + location);
        }
        String tag = location.substring(location.lastIndexOf(TAG_PATH) + TAG_PATH.length());
        int end = tag.indexOf('?');
        return end >= 0 ? tag.substring(0, end) : tag;
    }

    /**
     * Compares two dotted version strings numerically, e.g. {@code 1.10 > 1.9} and {@code 2.0 > 1.10}.
     * A leading "v" is ignored and missing components count as zero.
     *
     * @return a negative number, zero, or a positive number as {@code a} is lower, equal or higher than {@code b}
     * @throws IllegalArgumentException if a version contains no numeric components
     */
    static int compareVersions(String a, String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        String[] parts = v.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("^(\\d+).*$", "$1");
            if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Not a version: " + version);
            }
            numbers[i] = Integer.parseInt(digits);
        }
        return numbers;
    }
}
