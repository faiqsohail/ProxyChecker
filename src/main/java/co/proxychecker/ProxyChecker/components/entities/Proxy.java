package co.proxychecker.ProxyChecker.components.entities;

import java.net.Proxy.Type;

import javafx.util.Pair;

/**
 * The main Proxy entity used throughout the system models a single Proxy
 */
public class Proxy {

    private int port;
    private final String ip;

    private String country;
    private String response_time;

    private Type type;
    private ProxyStatus status;
    private ProxyAnonymity level;

    /**
     * Default values for the proxy object
     */
    private void initProxyDefaults() {
        this.response_time = null; // assume proxy is dead
        this.setProxyStatus(ProxyStatus.DEAD);
        this.setProxyAnonymity(ProxyAnonymity.TRANSPARENT);
    }

    /**
     * Create a new proxy object given a valid proxy string and type
     * @param ip_port - String in the format ip:port
     * @param type - java.net.Proxy.Type either HTTP or SOCKS
     */
    public Proxy(String ip_port, Type type) {
        Pair<String, Integer> proxyPair = format(ip_port);
        if(proxyPair == null) {
            throw new RuntimeException("Proxy() called with a invalid formatted string!");
        } else {
            this.ip = proxyPair.getKey();
            this.port = proxyPair.getValue();
            this.setProxyType(type);
            this.initProxyDefaults();
        }
    }

    /**
     * Create a new proxy object given a IP address a port the proxy is running on and the proxy type
     * @param ip - Integer the IP address of the proxy
     * @param port - Integer the Port the proxy is running on
     * @param type - java.net.Proxy.Type either HTTP or SOCKS
     */
    public Proxy(String ip, int port, Type type) {
        this.ip = ip;
        this.port = port;
        this.setProxyType(type);
        this.initProxyDefaults();

    }

    /**
     * Formats the given string into a new javafx.util.Pair object
     * @param ip_port - String in the format ip:port
     * @return null if the IP address cannot be formatted (invalid format),
     *          if it's a valid format returns a javafx.util.Pair object with IP address as the key
     *          and the Port as the Value
     */
    private static Pair<String, Integer> format(String ip_port) {
        String[] explode = ip_port.split(":");
        if(explode.length == 2) {
            try {
                int port = Integer.parseInt(explode[1]);
                return new Pair<>(explode[0], port);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Determine whether or not a given string is in a valid proxy format
     * @param ip_port - String in the format ip:port
     * @return Boolean - Whether or not the given string is in the valid form ip:port.
     */
    public static boolean isValidFormat(String ip_port) {
        return (format(ip_port) != null);
    }

    /**
     * Get the IP Address of the Proxy
     * @return String - IP address
     */
    public String getIp() {
        return this.ip;
    }

    /**
     * Get the Port of the Proxy
     * @return int - Port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Sets the country of the Proxy
     * @param country - String the name of the country
     * @return Proxy - current Proxy object
     */
    public Proxy setCountry(String country) {
        this.country = country;
        return this;
    }

    /**
     * Gets the country of the Proxy
     * @return String - Country
     */
    public String getCountry() {
        return this.country;
    }


    /**
     * Sets the response time of the proxy
     * @param response_time - String response time (milliseconds)
     * @return Proxy - current Proxy object
     */
    public Proxy setResponseTime(String response_time) {
        this.response_time = response_time;
        return this;
    }

    /**
     * Gets the response time of the proxy
     * @return String - Response Time
     */
    public String getResponseTime() {
        return this.response_time;
    }

    /**
     * Set the type of the Proxy
     * @param type - java.net.Proxy.Type either HTTP or SOCKS
     * @return Proxy - current Proxy object
     */
    public Proxy setProxyType(Type type) {
        this.type = type;
        return this;
    }

    /**
     * Gets the type of the Proxy
     * @return java.net.Proxy.Type
     */
    public Type getProxyType() {
        return this.type;
    }

    /**
     * Sets the status of the Proxy
     * @param status - ProxyStatus either ALIVE or DEAD
     * @return Proxy - current Proxy object
     */
    public Proxy setProxyStatus(ProxyStatus status) {
        this.status = status;
        return this;
    }

    /**
     * Gets the status of the Proxy
     * @return ProxyStatus - Either ALIVE or DEAD
     */
    public ProxyStatus getProxyStatus() {
        return this.status;
    }

    /**
     * Sets the Proxy anonymity
     * @param level - ProxyAnonymity either ELITE ANONYMOUS or TRANSPARENT
     * @return Proxy - current Proxy object
     */
    public Proxy setProxyAnonymity(ProxyAnonymity level) {
        this.level = level;
        return this;
    }

    /**
     * Gets the Proxy anonymity
     * @return ProxyAnonymity - Either ELITE ANONYMOUS or TRANSPARENT
     */
    public ProxyAnonymity getProxyAnonymity() {
        return this.level;
    }

}
