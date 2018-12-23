package co.proxychecker.ProxyChecker.components;

import java.net.Proxy;

/**
 * Manages the user configurable system settings
 */
public class UserSettings {

    private int timeout; // timeout for checking the proxy
    private int threads; // max threads
    private String ip = "0.0.0.0"; // ip address of the user
    private Proxy.Type type = java.net.Proxy.Type.HTTP;

    /**
     * Creates a new UserSettings object
     * @param timeout - int the timeout for web requests
     * @param threads - int the number of threads to use
     */
    public UserSettings(int timeout, int threads) {
        this.setTimeout(timeout);
        this.setThreads(threads);
    }

    /**
     * Gets the current IP address of the user
     * @return String - IP address
     */
    public String getIp() {
        return this.ip;
    }

    /**
     * Gets the current maximum threads allowed to be spawned
     * @return int - number of threads
     */
    public int getThreads() {
        return threads;
    }

    /**
     * Gets the timeout for web requests
     * @return int - the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the current users IP address
     * @param ip - String IP address
     * @return UserSettings
     */
    public UserSettings setIp(String ip) {
        this.ip = ip;
        return this;
    }

    /**
     * Sets the current preferred timeout for web requests
     * @param timeout - int
     * @return UserSettings
     */
    public UserSettings setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the preffered maximum threads allowed to be spawned
     * @param threads - int
     * @return UserSettings
     */
    public UserSettings setThreads(int threads) {
        this.threads = threads;
        return this;
    }

    /**
     * Sets the default proxy type that will be checked
     * @param type - java.net.Proxy.Type (HTTP or SOCKS)
     * @return UserSettings
     */
    public UserSettings setProxyType(Proxy.Type type) {
        this.type = type;
        return this;
    }

    /**
     * Gets the default proxy type that will be checked
     * @return java.net.Proxy.Type
     */
    public Proxy.Type getProxyType() {
        return this.type;
    }
}
