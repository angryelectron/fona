/**
 * Fona Java Library for SIM800.
 * Copyright 2014 Andrew Bythell <abythell@ieee.org>
 */ 

package com.angryelectron.fona;

import java.net.URL;
import java.util.Date;

/**
 * Control Fona / SIM800 via Serial Port.
 *
 * TODO: implement PUT, email, FTP, SMS, Voice, Audio, FM
 */
public class Fona {
    
    private final FonaSerial serial = new FonaSerial();
    
    /**
     * Open serial port connection to SIM800 module.
     * @param port port name
     * @param baud baud rate.  115200 is typical.
     * @throws com.angryelectron.fona.FonaException
     */
    public void open(String port, Integer baud) throws FonaException {
        serial.open(port, baud);
        serial.atCommandOK("AT&F"); //reset to factory defaults
        serial.atCommand("ATE0"); //turn off local echo
    }
    
    /**
     * Close serial port connection to SIM800 module.
     * @throws FonaException if communication with serial port fails.
     */
    public void close() throws FonaException {
        serial.close();
    }
    
    /**
     * Check communication with SIM800.
     * @return true if communication is OK.     
     * @throws com.angryelectron.fona.FonaException     
     */   
    public boolean check() throws FonaException {        
            return serial.atCommand("AT").equals("OK");        
    }
            
    /**
     * Set state of GPIO output pin.     
     * @param pin 1-3
     * @param value 1=high, 0=low
     * @throws com.angryelectron.fona.FonaException
     */
    public void gpioSetOutput(int pin, int value) throws FonaException {        
        if (pin <  1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=0," +pin + ",1," + value);
        if (!response.equals("OK")) {
            throw new FonaException("GPIO write failed.");
        }
    }
    
    /**
     * Read state of GPIO input pin.
     * @param pin
     * @return 1=high, 0=low
     * @throws com.angryelectron.fona.FonaException
     */
    public int gpioGetInput(int pin) throws FonaException {
        if (pin <  1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=1," + pin);
        if (response.contains("0")) return 0;
        else if (response.contains("1")) return 1;
        else {
            throw new FonaException("GPIO read failed: " + response);
        }                            
    }
    
    /**
     * Configure GPIO pin direction.
     * @param pin
     * @throws com.angryelectron.fona.FonaException
     */
    public void gpioSetInput(int pin) throws FonaException {
        if (pin <  1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=0," + pin + ",0");
        if (!response.equals("OK")) {
            throw new FonaException("GPIO config input pin failed.");
        }
    }
    
    public void gprsEnable(String apn, String user, String password) throws FonaException {
        try {
            serial.atCommandOK("AT+CGATT=1");
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
            serial.atCommandOK("AT+SAPBR=0,1");
            serial.atCommandOK("AT+CGATT=0");
        } catch (FonaException ex) {
            throw new FonaException("GPRS disable failed.");
        }
    }
    
    public boolean gprsIsEnabled() throws FonaException {
        String response = serial.atCommand("AT+CGATT?");
        if (response.contains("1")) return true;
        else if (response.contains("0")) return false;
        else {
            throw new FonaException("GPRS status check failed: " + response);
        }                            
    }
    
    public String gprsHttpGet(URL url) {
        throw new UnsupportedOperationException("Not Implemented.");
    }
    
    public Double batteryVoltage() {
        throw new UnsupportedOperationException("Not Implemented.");
    }
    
    public Integer batteryPercent() {
        throw new UnsupportedOperationException("Not Implemented.");
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
    
    public enum SmsSelect { READ, UNREAD, SEND, UNSENT, INBOX, ALL };
    /**
     * Delete SMS messages.  May take up to 25s to delete large numbers of
     * messages (>50).
     * @param selection 
     */
    public void smsDelete(SmsSelect selection) {
        throw new UnsupportedOperationException("Not Implemented.");
    }
    
    /**
     * Power-down the SIM800 module.  Note that the module requires
     * hardware & platform specific methods to turn the module back on.
     * @param urgent if ture, power off urgently.  Will not send out NORMAL 
     * POWER DOWN.
     */
    public void simPowerOff(boolean urgent) {
        throw new UnsupportedOperationException("Not Implemented.");
    }
    
    public Double simReadADC() {
        throw new UnsupportedOperationException("Not Implemented.");
    }
    
    public void simUnlock(String password) {
        throw new UnsupportedOperationException("Not Implemented.");
    }
    
    /**
     * Received signal strength indicator.
     * @return RSSI in dBm between -54dBm and -115dBm.  -999dBM indicates that
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
        switch(dbm) {
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
     * Name of Service Provider.  Value is read from the SIM.
     * @return Name of Service Provider.
     * @throws com.angryelectron.fona.FonaException
     */
    public String simProvider() throws FonaException {
        String response = serial.atCommand("AT+CSPN?");
        if (response.contains("ERROR")) {
            throw new FonaException("Reading service provider failed: " + response);
        }
        /**
         * Extract provider name from response.  Response format is
         * +CSPN:"<spn>",<display mode>.
         */
        return response.substring(response.indexOf(":") + 3, response.indexOf(",") - 1);
    }
    
    /**
     * Get temperature of SIM800 module.
     * @return degrees Celsius -40 - 90
     * @throws com.angryelectron.fona.FonaException
     */
    public Double temperature() throws FonaException {
        String response = serial.atCommand("AT+CMTE?");
        if (response.contains("ERROR")) {
            throw new FonaException("Read temperature failed: " + response);
        }
        String temperature = response.substring(response.indexOf(",")+1, response.length()-2);
        return Double.parseDouble(temperature);
    }
    
}
