package co.proxychecker.ProxyChecker.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorsTest {

    @Test
    void formatsColorsWithLeadingZeros() {
        assertEquals("#000000", Colors.toHex(0, 0, 0));
        assertEquals("#ffffff", Colors.toHex(1, 1, 1));
        assertEquals("#0a0b0c", Colors.toHex(10 / 255.0, 11 / 255.0, 12 / 255.0));
        assertEquals("#b3e6b3", Colors.toHex(0xb3 / 255.0, 0xe6 / 255.0, 0xb3 / 255.0));
    }

    @Test
    void clampsOutOfRangeValues() {
        assertEquals("#ff0000", Colors.toHex(2, -1, 0));
    }
}
