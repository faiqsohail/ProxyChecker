package co.proxychecker.ProxyChecker.components;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class UpdateChecker {

    private String repoVersion = null;

    public boolean isUpdateAvailable() throws IOException {
        int repoVersion = Integer.parseInt(getRepoVersion().replaceAll("\\.",""));
        int localVersion = Integer.parseInt(Settings.getApplicationVersion().replaceAll("\\.", ""));
        return repoVersion > localVersion;
    }
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
