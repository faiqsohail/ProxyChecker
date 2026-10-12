package co.proxychecker.ProxyChecker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoadCommandTest {

    @Test
    void normalizesWhitespaceAndByteOrderMark() {
        assertEquals("1.2.3.4:80", LoadCommand.normalize("  1.2.3.4:80\t"));
        assertEquals("1.2.3.4:80", LoadCommand.normalize("\uFEFF1.2.3.4:80"));
        assertEquals("", LoadCommand.normalize("   "));
    }
}
