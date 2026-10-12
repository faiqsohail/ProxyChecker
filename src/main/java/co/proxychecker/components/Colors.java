package co.proxychecker.components;

import java.util.Locale;

/**
 * Color helpers.
 */
public final class Colors {

    private Colors() {
    }

    /**
     * Converts RGB components in the range [0, 1] (as used by {@code javafx.scene.paint.Color})
     * to a CSS hex string such as {@code #0a1b2c}.
     */
    public static String toHex(double red, double green, double blue) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", channel(red), channel(green), channel(blue));
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
    }
}
