/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */

package com.angryelectron.fona;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test cases for FonaUnsolicited class.
 */
public class FonaUnsolicitedTest {
    
    @Test
    public void testUnsolicitedPatterns() {
        System.out.println("testUnsolicitedPatterns");
        FonaUnsolicited response = new FonaUnsolicited();
        assertFalse("false match", response.isUnsolicited("HELLO"));
        assertTrue("positive match", response.isUnsolicited("RING"));
        assertTrue("escaped *", response.isUnsolicited("*PSNWID:"));
        assertTrue("escape +", response.isUnsolicited("+CRING:"));
    }
    
}
