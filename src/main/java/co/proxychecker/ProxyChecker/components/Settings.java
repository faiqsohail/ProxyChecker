package co.proxychecker.ProxyChecker.components;

import java.io.*;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;


/**
 * Manages Application settings and configuration files and directory
 */
public class Settings {

    private static final String APPLICATION_VERSION = "1.1";
    private static final String APPLICATION_NAME = "Proxy Checker";
    private static final String APPLICATION_URL = "https://proxychecker.co";
    private static final String APPLICATION_REPO = "https://github.com/HiddenMotives/ProxyChecker/";

    public static String getApplicationName() {
        return APPLICATION_NAME;
    }

    public static String getApplicationUrl() {
        return APPLICATION_URL;
    }

    /**
     * Gets the configuration file and parses it using Gson
     * @return UserSettings
     */
    public static UserSettings getConfig() {
        try {
            return new Gson().fromJson(new JsonReader(new FileReader(getConfigFile())), UserSettings.class);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Saves an updated UserSettings  to disk
     * @param userSettings - UserSettings
     * @return boolean - Whether or not the save was successful
     */
    public static boolean saveConfig(UserSettings userSettings) {
        File file = new File(getSettingsFolder().getAbsolutePath() + File.separator + "config.json");
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(new Gson().toJson(userSettings));
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the configuration File object from the settings folder
     * @return File
     */
    public static File getConfigFile() {
        File file = new File(getSettingsFolder().getAbsolutePath() + File.separator + "config.json");
        if(!file.exists()) {
            if(!saveConfig(new UserSettings())) {
                throw new RuntimeException("Unable to save default config.json!");
            }
        }
        return file;
    }


    /**
     * Gets the settings folder and creates it if it doesn't exist in the home directory
     * @return File
     */
    public static File getSettingsFolder()  {
        String safe_name = APPLICATION_NAME.replaceAll(" ", "_");
        File file = new File(
                System.getProperty("user.home") + File.separator + "." + safe_name + File.separator
        );
        if(!file.exists()) {
            if(!file.mkdirs()) {
                throw new RuntimeException("Unable to create application directory!");
            }
        }
        return file;
    }

    /**
     * Gets the current released version of this application
     * @return version
     */
    public static String getApplicationVersion() {
        return APPLICATION_VERSION;
    }

    /**
     * Gets the main repo of this project
     * @return
     */
    public static String getApplicationRepo() {
        return APPLICATION_REPO;
    }




}
