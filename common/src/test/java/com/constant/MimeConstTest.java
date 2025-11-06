package com.constant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MimeConstTest {

    @Test
    void NameTest() {
        assertEquals("application/json", MimeConst.guessByName("a.JSON"));
        assertEquals("image/svg+xml",   MimeConst.guessByName("icon.SvG"));
        assertEquals("text/plain",      MimeConst.guessByName("readme.txt"));
        assertNull(MimeConst.guessByName("noext"));
    }

    @Test
    void TextTest() {
        assertTrue(MimeConst.isTextual("text/plain"));
        assertTrue(MimeConst.isTextual("application/json"));
        assertTrue(MimeConst.isTextual("image/svg+xml"));
        assertFalse(MimeConst.isTextual("image/png"));
    }
}
