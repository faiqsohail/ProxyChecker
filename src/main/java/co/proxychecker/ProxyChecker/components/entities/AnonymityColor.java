package co.proxychecker.ProxyChecker.components.entities;

/**
 * Background color used to highlight proxies of a given anonymity level in the result table.
 * <p>
 * Serialized as {@code {"key": "ELITE", "value": "#b3e6b3"}}, which matches the format previously
 * written by {@code javafx.util.Pair}, so existing configuration files keep working.
 */
public final class AnonymityColor {

    private ProxyAnonymity key;
    private String value;

    @SuppressWarnings("unused") // used by Gson
    private AnonymityColor() {
    }

    public AnonymityColor(ProxyAnonymity anonymity, String color) {
        this.key = anonymity;
        this.value = color;
    }

    public ProxyAnonymity getAnonymity() {
        return key;
    }

    public String getColor() {
        return value;
    }
}
