package co.proxychecker.ProxyChecker.components;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Manages and facilities methods pertaining to checking for updates for the current application from the repository
 */
public class UpdateChecker {

    private String repoVersion = null;

    /**
     * Checks whether or not an update is available
     * @return boolean
     */
    public boolean isUpdateAvailable() {
        try {
            int repoVersion = Integer.parseInt(getRepoVersion().replaceAll("\\.",""));
            int localVersion = Integer.parseInt(Settings.getApplicationVersion().replaceAll("\\.", ""));
            return repoVersion > localVersion;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the latest version tagged on the repository
     * @return String - version
     * @throws IOException
     */
    public String getRepoVersion() throws IOException {
        if(repoVersion == null) {
            URLConnection connection = new URL(Settings.getApplicationRepo() + "releases/latest").openConnection();
            connection.connect();
            connection.getInputStream().close();
            String url = connection.getURL().toString();
            this.repoVersion = url.substring(url.lastIndexOf('/') + 1);
        }
        return this.repoVersion;
    }
}
