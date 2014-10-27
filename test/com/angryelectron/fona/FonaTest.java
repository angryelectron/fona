/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.util.Date;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

public class FonaTest {

    //TODO: move these to a properties file so others can test.
    private static final String PORT = "/dev/tty.usbserial-FTEA5W5C";
    private static final Integer BAUD = 115200;

    //Credentials for Rogers Wireless required for testing GPRS.
    private static final String APN = "internet.com";
    private static final String USER = "wapuser1";
    private static final String PWD = "wap";
    private static final String SMTP = "smtp.rogerswirelessdata.com";

    //E-Mail Settings.  Use an address of an account you can access
    //to verify the message.
    private static final String TO_ADDRESS = "abythell@ieee.org";
    private static final String TO_NAME = "Andrew Bythell";

    //SMS Settings.
    private static final String SMSNUMBER = "";

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
     *
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testGprs() throws FonaException {
        System.out.println("gprsEnable");
        fona.gprsDisable();
        assertFalse(fona.gprsIsEnabled());
        fona.gprsEnable(APN, USER, PWD);
        assertTrue(fona.gprsIsEnabled());
        fona.gprsDisable();
        assertFalse(fona.gprsIsEnabled());
    }

    /**
     * Test of gprsHttpGet method, of class Fona.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testGprsHttpGet() throws FonaException {
        System.out.println("gprsHttpGet");
        if (!fona.gprsIsEnabled()) {
            fona.gprsEnable(APN, USER, PWD);
        }
        assertTrue("GPRS not enabled", fona.gprsIsEnabled());

        String response = fona.gprsHttpGet("http://httpbin.org/user-agent");
        if (!response.contains("SIMCOM_MODULE")) {
            fail("Unexpected response: " + response);
        }
    }

    /**
     * Test of batteryVoltage method, of class Fona.
     *
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
     * Test of time method, of class Fona.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testTime() throws FonaException {
        System.out.print("testTime: ");
        if (!fona.gprsIsEnabled()) {
            fona.gprsEnable(APN, USER, PWD);
        }
        Date date = fona.gprsTime();
        System.out.println(date);
        assertNotNull(date);
        fona.gprsDisable();
    }

    /**
     * Test of smsSend method, of class Fona.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    @Test
    public void testSmsSend() throws FonaException {
        System.out.println("smsSend");
        fona.smsSend(SMSNUMBER, "Test SMS from FONA");
    }

    /**
     * Test of simReadADC method, of class Fona.
     *
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

    @Test
    public void testEmail() throws FonaException {
        fail("Disabled during development.");

        fona.gprsDisable();
        fona.gprsEnable(APN, USER, PWD);
        fona.emailSMTP(SMTP, 25);

        FonaEmailMessage email = new FonaEmailMessage();
        email.fromAddress = "fona@test.com";
        email.fromName = "Fona";
        email.to(TO_ADDRESS, TO_NAME);
        email.subject("Fona Java Library Email Test");
        email.body("Hello, from FONA.");

        fona.emailSend(email);
        fona.gprsDisable();
    }

}
