/**
 * Fona Java Library for SIM800. Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.util.Date;
import javax.xml.ws.http.HTTPException;

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
        
        /**
         * GPRS is enabled by default, however it isn't actually useful as
         * the APN has not yet been set, so disable it to avoid 601/network
         * errors.
         */
        //serial.atCommand("AT+CGATT=0", 10000); //disable GPRS                      
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
            throw new FonaException("GPRS enable failed.  Check credentials.");
        }
    }

    public void gprsDisable() throws FonaException {
        try {            
            serial.atCommand("AT+SAPBR=0,1");                  
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

    /**
     * HTTP GET request.
     * @param url URL.  
     * @return HTTP response.
     * @throws FonaException 
     */
    public String gprsHttpGet(String url) throws FonaException {        
        if (!url.toLowerCase().startsWith("http")) {
            throw new FonaException("Invalid protocol.  Only HTTP is supported.");
        }
        if (!this.gprsIsEnabled()) {
            throw new FonaException("GPRS is not enabled.");
        }
        String address = url.replaceAll("http://", "");        
        serial.atCommandOK("AT+HTTPINIT");
        serial.atCommandOK("AT+HTTPPARA=\"CID\",1");
        serial.atCommandOK("AT+HTTPPARA=\"URL\",\"" + address + "\"");
        
        String httpResult = serial.atCommand("AT+HTTPACTION=0", 100000, "HTTPACTION");
        /* TODO: above command returns "+HTTPACTION: 0,200,30" 
         * need a way to read it, as it doesn't end with OK or ERROR
         */                
        if (!httpResult.startsWith("+HTTPACTION: 0")) {
            /* try to close the http connection so subsequent runs don't fail */
            serial.atCommand("AT+HTTPTERM");
            throw new FonaException("Invalid HTTP response: " + httpResult);
        }
        String httpFields[] = httpResult.split(",");
        Integer httpStatusCode = Integer.parseInt(httpFields[1]);
        if (httpStatusCode != 200) {
            throw new HTTPException(httpStatusCode);
        }
        
        String response = serial.atCommand("AT+HTTPREAD", 5000);
        /**
         * TODO: parse response in format:  
         * +HTTPREAD: 30
            {
                "origin": "24.114.38.60"
            }
            OK
         */
        serial.atCommandOK("AT+HTTPTERM");
        return response;
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

    /**
     * Read Analog/Digital Converter.
     * @return Value between 0 and 2800.
     * @throws FonaException if ADC read fails.
     */
    public Integer simReadADC() throws FonaException {
        /* max response time 2s */
        String response = serial.atCommand("AT+CADC?", 2000);
        String fields[] = response.split(",");
        if (!response.endsWith("OK") || fields[0].endsWith("0")) {
            throw new FonaException("ADC read failed: " + response);
        }
        String value = fields[1].substring(0, fields[1].length() - 2);
        return Integer.parseInt(value);        
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
