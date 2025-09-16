package com.verlake.dam.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for UnixServerType enum
 */
public class UnixServerTypeTest {

    @Test
    public void testUnixServerTypeValues() {
        // Test that all expected values exist
        UnixServerType[] types = UnixServerType.values();
        assertEquals(4, types.length);
        
        // Test specific values
        assertNotNull(UnixServerType.valueOf("LINUX"));
        assertNotNull(UnixServerType.valueOf("UNIX"));
        assertNotNull(UnixServerType.valueOf("FREEBSD"));
        assertNotNull(UnixServerType.valueOf("SOLARIS"));
    }

    @Test
    public void testUnixServerTypeOrdinal() {
        // Test ordinal values
        assertEquals(0, UnixServerType.LINUX.ordinal());
        assertEquals(1, UnixServerType.UNIX.ordinal());
        assertEquals(2, UnixServerType.FREEBSD.ordinal());
        assertEquals(3, UnixServerType.SOLARIS.ordinal());
    }

    @Test
    public void testUnixServerTypeToString() {
        // Test toString method
        assertEquals("LINUX", UnixServerType.LINUX.toString());
        assertEquals("UNIX", UnixServerType.UNIX.toString());
        assertEquals("FREEBSD", UnixServerType.FREEBSD.toString());
        assertEquals("SOLARIS", UnixServerType.SOLARIS.toString());
    }
}
