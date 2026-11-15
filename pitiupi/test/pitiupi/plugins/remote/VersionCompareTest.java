package pitiupi.plugins.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCompareTest {

    @Test
    void comparesNumbersNotText() {
        assertTrue(VersionCompare.compare("1.10.0", "1.9.0") > 0);
        assertTrue(VersionCompare.compare("1.9.0", "1.10.0") < 0);
        assertTrue(VersionCompare.compare("2.0", "1.99") > 0);
    }

    @Test
    void missingPartsCountAsZero() {
        assertEquals(0, VersionCompare.compare("1.0", "1.0.0"));
        assertTrue(VersionCompare.compare("1.0.1", "1.0") > 0);
    }

    @Test
    void equalVersionsAreEqual() {
        assertEquals(0, VersionCompare.compare("3.2.1", "3.2.1"));
    }

    @Test
    void suffixIsIgnored() {
        assertEquals(0, VersionCompare.compare("1.0.0-beta", "1.0.0"));
    }
}
