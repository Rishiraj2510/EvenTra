package com.eventra.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignupPropertiesTests {

    @Test
    void validDomainsParseCorrectly() {
        SignupProperties props = new SignupProperties("acropolis.in, MEDI-CAPS.AC.IN ,  ietdavv.edu ");
        assertTrue(props.isDomainAllowed("acropolis.in"));
        assertTrue(props.isDomainAllowed("ACROPOLIS.IN"));
        assertTrue(props.isDomainAllowed("medi-caps.ac.in"));
        assertTrue(props.isDomainAllowed("ietdavv.edu"));
        assertFalse(props.isDomainAllowed("gmail.com"));
        assertFalse(props.isDomainAllowed("mail.acropolis.in"));
        assertFalse(props.isDomainAllowed("acropolis.in.evil.com"));
    }

    @Test
    void emptyDomainsRefusesToStart() {
        assertThrows(IllegalStateException.class, () -> new SignupProperties(""));
        assertThrows(IllegalStateException.class, () -> new SignupProperties("   ,  , "));
        assertThrows(IllegalStateException.class, () -> new SignupProperties(null));
    }
}

