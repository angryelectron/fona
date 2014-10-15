/**
 * Fona Java Library for SIM800. Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.net.URL;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Control Fona / SIM800 via Serial Port.
 *
 * TODO: implement PUT, email, FTP, SMS, Voice, Audio, FM
 */
public class Fona {

    private final FonaSerial serial = new FonaSerial();

    /**
     * Open serial port connection to SIM800 module.
     *
     * @param port port name
     * @param baud baud rate. 115200 is typical.
     * @throws com.angryelectron.fona.FonaException
     */
    public void open(String port, Integer baud) throws FonaException {
        serial.open(port, baud);

        /**
         * This response will vary based on the current Echo setting: AT&FOK or
         * just OK.
         */
        if (!serial.atCommand("AT&F").endsWith("OK")) {
            throw new FonaException("Factory reset failed.  Can't communicate with device.");
        }

        serial.atCommand("ATE0"); //turn off local echo
    }

    /**
     * Close serial port connection to SIM800 module.
     *
     * @throws FonaException if communication with serial port fails.
     */
    public void close() throws FonaException {
        serial.close();
    }

    /**
     * Check communication with SIM800.
     *
     * @return true if communication is OK.
     * @throws com.angryelectron.fona.FonaException
     */
    public boolean check() throws FonaException {
        return serial.atCommand("AT").equals("OK");
    }

    /**
     * Set state of GPIO output pin.
     *
     * @param pin 1-3
     * @param value 1=high, 0=low
     * @throws com.angryelectron.fona.FonaException
     */
    public void gpioSetOutput(int pin, int value) throws FonaException {
        if (pin < 1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=0," + pin + ",1," + value);
        if (!response.equals("OK")) {
            throw new FonaException("GPIO write failed.");
        }
    }

    /**
     * Read state of GPIO input pin.
     *
     * @param pin
     * @return 1=high, 0=low
     * @throws com.angryelectron.fona.FonaException
     */
    public int gpioGetInput(int pin) throws FonaException {
        if (pin < 1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=1," + pin);
        if (response.contains("0")) {
            return 0;
        } else if (response.contains("1")) {
            return 1;
        } else {
            throw new FonaException("GPIO read failed: " + response);
        }
    }

    /**
     * Configure GPIO pin direction.
     *
     * @param pin
     * @throws com.angryelectron.fona.FonaException
     */
    public void gpioSetInput(int pin) throws FonaException {
        if (pin < 1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=0," + pin + ",0");
        if (!response.equals("OK")) {
            throw new FonaException("GPIO config input pin failed.");
        }
    }

    public void gprsEnable(String apn, String user, String password) throws FonaException {
        try {
            serial.atCommand("AT+CGATT=1", 10000);
            serial.atCommandOK("AT+SAPBR=3,1,\"CONTYPE\",\"GPRS\"");
            serial.atCommandOK("AT+SAPBR=3,1,\"APN\",\"" + apn + "\"");
            serial.atCommandOK("AT+SAPBR=3,1,\"USER\",\"" + user + "\"");
            serial.atCommandOK("AT+SAPBR=3,1,\"PWD\",\"" + password + "\"");
            serial.atCommandOK("AT+SAPBR=1,1");
        } catch (FonaException ex) {
            //one of the above commands did not return OK.
            throw new FonaException("GPRS enable failed.  Check credentials.");
        }
    }

    public void gprsDisable() throws FonaException {
        try {            
            serial.atCommand("AT+SAPBR=0,1");      
            /**
             * Max response time is 10seconds according to data sheet.
             */
            serial.atCommand("AT+CGATT=0", 10000); 
        } catch (FonaException ex) {
            throw new FonaException("GPRS disable failed: " + ex.getMessage());
        }
    }

    public boolean gprsIsEnabled() throws FonaException {
        String response = serial.atCommand("AT+CGATT?");
        if (response.contains("1")) {
            return true;
        } else if (response.contains("0")) {
            return false;
        } else {
            throw new FonaException("GPRS status check failed: " + response);
        }
    }

    public String gprsHttpGet(URL url) {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    /**
     * Get battery voltage.
     * @return Battery voltage in millivolts.
     * @throws FonaException 
     */
    public Integer batteryVoltage() throws FonaException {
        String response = serial.atCommand("AT+CBC");
        if (!response.endsWith("OK")) {
            throw new FonaException("Unexpected response: " + response);
        }        
        String fields[] = response.split(",");
        String voltage = fields[2].substring(0, fields[2].length() - 2);
        return Integer.parseInt(voltage);
    }

    /**
     * Get battery charge level.
     * @return Battery charge, as a percentage.
     * @throws FonaException 
     */
    public Integer batteryPercent() throws FonaException {
        String response = serial.atCommand("AT+CBC");
        if (!response.endsWith("OK")) {
            throw new FonaException("Unexpected response: " + response);
        }        
        String fields[] = response.split(",");        
        return Integer.parseInt(fields[1]);
    }
    
    /**
     * Get battery charging state.
     * @return 0=not charging, 1=charging, 2=charging finished
     * @throws FonaException 
     */
    public Integer batteryChargingState() throws FonaException {
        String response = serial.atCommand("AT+CBC");
        if (!response.endsWith("OK")) {
            throw new FonaException("Unexpected response: " + response);
        }        
        String fields[] = response.split(",");        
        String subfields[] = fields[0].split(" ");
        return Integer.parseInt(subfields[1]);
    }

    public void timeSync(boolean enable) {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    public Date time() {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    public void smsSend(String phoneNumber, String message) {
        throw new UnsupportedOperationException("Not Implemented.");
    }
            
    public boolean smsReceived() {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    public String smsRead() {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    public enum SmsSelect {

        READ, UNREAD, SEND, UNSENT, INBOX, ALL
    };

    /**
     * Delete SMS messages. May take up to 25s to delete large numbers of
     * messages (>50).
     *
     * @param selection
     */
    public void smsDelete(SmsSelect selection) {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    /**
     * Power-down the SIM800 module. This may have different implications depending
     * on how the hardware has been configured. For example, a FONA with KEY
     * tied to GND will reboot after this command, although the time required to
     * reboot varies. In testing, sometimes this command returns "NORMAL POWER
     * DOWN" and other times returns nothing, so the result is ignored. In
     * general, it is probably best to use hardware-specific methods (like
     * FONA's KEY line) to control the power state of the device.
     *     
     * @throws com.angryelectron.fona.FonaException
     */
    public void simPowerOff() throws FonaException {        
            serial.atCommand("AT+CPOWD=0");        
    }

    public Double simReadADC() {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    public void simUnlock(String password) {
        throw new UnsupportedOperationException("Not Implemented.");
    }

    /**
     * Received signal strength indicator.
     *
     * @return RSSI in dBm between -54dBm and -115dBm. -999dBM indicates that
     * the signal strength is not known or is undetectable.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    public Integer simRSSI() throws FonaException {
        String response = serial.atCommand("AT+CSQ");
        if (response.contains("ERROR")) {
            throw new FonaException("Signal quality report failed: " + response);
        }
        String rssi = response.substring(response.indexOf(":") + 2, response.indexOf(",") - 1);
        Integer dbm = Integer.parseInt(rssi);
        switch (dbm) {
            case 0:
                return -115; /* may be less */

            case 1:
                return -111;
            case 31:
                return -52;
            case 99:
                return -999; /* not known or not detectable */

            default:
                return (dbm * 2) - 114;
        }
    }

    /**
     * Name of Service Provider. Value is read from the SIM.
     *
     * @return Name of Service Provider.
     * @throws com.angryelectron.fona.FonaException
     */
    public String simProvider() throws FonaException {
        String response = serial.atCommand("AT+CSPN?");
        if (response.contains("ERROR")) {
            throw new FonaException("Reading service provider failed: " + response);
        }
        /**
         * Extract provider name from response. Response format is
         * +CSPN:"<spn>",<display mode>.
         */
        return response.substring(response.indexOf(":") + 3, response.indexOf(",") - 1);
    }

    /**
     * Get temperature of SIM800 module.
     *
     * @return degrees Celsius -40 - 90
     * @throws com.angryelectron.fona.FonaException
     */
    public Double temperature() throws FonaException {
        String response = serial.atCommand("AT+CMTE?");
        if (response.contains("ERROR")) {
            throw new FonaException("Read temperature failed: " + response);
        }
        String temperature = response.substring(response.indexOf(",") + 1, response.length() - 2);
        return Double.parseDouble(temperature);
    }

}
