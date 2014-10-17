/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.angryelectron.fona;

import java.util.Date;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author abythell
 */
public class FonaTest {

    //TODO: move these to a properties file so others can test.
    private static final String PORT = "/dev/ttyUSB1";
    private static final Integer BAUD = 115200;
    
    //Credentials for Rogers Wireless required for testing GPRS.
    private static final String APN = "internet.com";
    private static final String USER = "wapuser1";
    private static final String PWD = "wap";    
            
    private static final Fona fona = new Fona();

    public FonaTest() {
    }

    @Before
    public void setUp() throws FonaException {
        try {
            fona.open(PORT, BAUD);
        } catch (FonaException ex) {
            System.out.println(ex.getMessage());
            throw ex;
        }
    }

    @After
    public void tearDown() throws FonaException {
        fona.close();
    }

    /**
     * Test of open method, of class Fona.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testCheck() throws Exception {
        System.out.println("check");
        if (!fona.check()) {
            fail("Can't communicate with module.");
        }
    }

    /**
     * Test of gpioSetOutput method, of class Fona. Can't really test without
     * external hardware to monitor pin.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testGpioOutput() throws FonaException {
        System.out.println("gpioOutput");        
        fona.gpioSetOutput(1, 1);
        fona.gpioSetOutput(1, 0);
    }

    /**
     * Can't really test this without external hardware. Return value is
     * irrelevant - just ensure no exception is thrown.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testGpioInput() throws FonaException {
        int pin = 1;
        System.out.println("gpioInput");
        fona.gpioSetInput(pin);
        fona.gpioGetInput(pin);
    }

    @Test(expected = FonaException.class)
    public void testGpioBadDirection() throws FonaException {
        int pin = 1;
        int value = 1;
        System.out.println("gpioBadDirection");
        fona.gpioSetOutput(pin, value); //pin is an output
        fona.gpioGetInput(pin); //should throw exception.  can't read output pins.
    }
    
    /**
     * Test of gprsEnable method, of class Fona.
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testGprs() throws FonaException {
        System.out.println("gprsEnable");                                
        fona.gprsEnable(APN, USER, PWD);
        assertTrue(fona.gprsIsEnabled());
        fona.gprsDisable();
        assertFalse(fona.gprsIsEnabled());                
    }

    
    /**
     * Test of gprsHttpGet method, of class Fona.
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testGprsHttpGet() throws FonaException {
        System.out.println("gprsHttpGet");        
        if (!fona.gprsIsEnabled()) {
            fona.gprsEnable(APN, USER, PWD);
            assertTrue("GPRS not enabled", fona.gprsIsEnabled());
        }        
        String response = fona.gprsHttpGet("http://httpbin.org/ip");
        /**
         * expected response
         * {
                "origin": "24.114.38.60"
                           }
         */
        fail("The test case is a prototype.");
    }

    /**
     * Test of batteryVoltage method, of class Fona.
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testBatteryVoltage() throws FonaException {
        System.out.println("batteryVoltage: " + fona.batteryVoltage());        
    }

    /**
     * Test of batteryPercent method, of class Fona.
     */
    @Test
    public void testBatteryPercent() throws FonaException {
        System.out.println("batteryPercent: " + fona.batteryPercent());                
    }
    
    @Test
    public void testBatteryCharge() throws FonaException {
        System.out.println("batteryChargingState: " + fona.batteryChargingState());
    }

    /**
     * Test of timeSync method, of class Fona.
     */
    @Test
    public void testTimeSync() {
        System.out.println("timeSync");
        boolean enable = false;
        Fona instance = new Fona();
        instance.timeSync(enable);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of time method, of class Fona.
     */
    @Test
    public void testTime() {
        System.out.println("time");
        Fona instance = new Fona();
        Date expResult = null;
        Date result = instance.time();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of smsSend method, of class Fona.
     */
    @Test
    public void testSmsSend() {
        System.out.println("smsSend");
        String phoneNumber = "";
        String message = "";
        Fona instance = new Fona();
        instance.smsSend(phoneNumber, message);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of smsReceived method, of class Fona.
     */
    @Test
    public void testSmsReceived() {
        System.out.println("smsReceived");
        Fona instance = new Fona();
        boolean expResult = false;
        boolean result = instance.smsReceived();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of smsRead method, of class Fona.
     */
    @Test
    public void testSmsRead() {
        System.out.println("smsRead");
        Fona instance = new Fona();
        String expResult = "";
        String result = instance.smsRead();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of smsDelete method, of class Fona.
     */
    @Test
    public void testSmsDelete() {
        System.out.println("smsDelete");
        Fona.SmsSelect selection = null;
        Fona instance = new Fona();
        instance.smsDelete(selection);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of simReadADC method, of class Fona.
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testSimReadADC() throws FonaException {        
        System.out.println("simReadADC: " + fona.simReadADC());
        Integer value = fona.simReadADC();
        assertTrue(0 <= value && value <= 2800);
    }

    /**
     * Test of simUnlock method, of class Fona.
     */
    @Test
    public void testSimUnlock() {
        System.out.println("simUnlock");
        String password = "";
        Fona instance = new Fona();
        instance.simUnlock(password);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of simRSSI method, of class Fona.
     */
    @Test
    public void testSimRSSI() throws FonaException {
        System.out.println("simRSSI: " + fona.simRSSI() + "dBm");        
    }

    /**
     * Test of simProvider method, of class Fona.
     */
    @Test
    public void testSimProvider() throws FonaException {
        System.out.println("simProvider: " + fona.simProvider());        
    }

    /**
     * Test of temperature method, of class Fona.
     */
    @Test
    public void testTemperature() throws FonaException {        
        System.out.println("temperature: " + fona.temperature() + "C");
    }

}
