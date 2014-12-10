/**
 * Fona Java Library for SIM800. Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.xml.ws.http.HTTPException;

/**
 * Primary class for controlling a Fona / SIM800 cellular module via serial
 * port.
 * <p>
 * Based on the <a
 * href="http://www.adafruit.com/datasheets/sim800_series_at_command_manual_v1.01.pdf">
 * Sim800 AT Command Manual</a>, this class only implements a subset of the
 * features supported by the SIM800. Feel free to implement any missing features
 * by contributing to the
 * <a href="http://github.com/angryelectron/fona">GitHub Project</a>.</p>
 * <p>
 * Note: All network-based operations use a hardcoded Bearer Profile Identifier
 * of 1.</p>
 *
 */
public class Fona implements FonaEventHandler {

    /**
     * The lower level IO functions are handled by a separate class in case a
     * different implementation is required. Not sure about the future of the
     * RXTX project.
     */
    private final FonaSerial serial = new FonaSerial();
    private FonaEventHandler applicationEventHandler = null;
    private FonaUnsolicitedListener unsolicitedListener = null;

    /**
     * Open serial port connection to SIM800 module.
     *
     * @param port Port name (/dev/ttyUSB1, COM7, etc.)
     * @param baud Baud rate. 115200 is recommended.
     * @throws com.angryelectron.fona.FonaException
     */
    public void open(String port, Integer baud) throws FonaException {
        serial.open(port, baud);
        serial.atCommand("AT");
        serial.atCommand("AT&F");
        serial.atCommand("ATE0"); //turn off local echo         
    }

    /**
     * Open serial port connection to SIM800 module and set handler for
     * asynchronous events.
     *
     * @param port Port name (/dev/ttyUSB1, COM7, etc.)
     * @param baud Baud rate. 115200 is recommended.
     * @param handler FonaEventHandler for handling incoming SMS and Calls.
     * @throws FonaException
     */
    public void open(String port, Integer baud, FonaEventHandler handler) throws FonaException {
        this.open(port, baud);
        this.applicationEventHandler = handler;

        /**
         * Connect the serial port, listener thread, and this class so
         * unsolicited responses can be handled.
         */
        unsolicitedListener = new FonaUnsolicitedListener(serial.getUnsolicitedQueue());
        unsolicitedListener.start(this);
    }

    /**
     * Close serial port connection to SIM800 module.
     *
     * @throws FonaException if communication with serial port fails.
     */
    public void close() throws FonaException {
        serial.close();
        if (unsolicitedListener != null) {
            unsolicitedListener.stop();
        }
    }

    /**
     * Configure SMTP server for sending e-mail without user authentication.
     *
     * @param server SMTP server hostname or IP.
     * @param port SMTP port, typically 25.
     * @throws FonaException
     */
    public void emailSMTPLogin(String server, Integer port) throws FonaException {
        /* this number must match the bearer profide ID used in enableGPRS() */
        serial.atCommandOK("AT+EMAILCID=1");

        /* set the SMTP server response timeout value */
        serial.atCommandOK("AT+EMAILTO=30");

        /* set SMTP server */
        serial.atCommandOK("AT+SMTPSRV=\"" + server + "\"," + port);

        /* don't require SMTP authorization */
        serial.atCommandOK("AT+SMTPAUTH=0");
    }

    /**
     * Configure SMTP server for sending e-mail with user authentication.
     *
     * @param server SMTP server host name or IP.
     * @param port SMTP port, typically 25.
     * @param user SMTP username.
     * @param password SMTP password.
     * @throws FonaException
     */
    public void emailSMTPLogin(String server, Integer port, String user, String password) throws FonaException {
        emailSMTPLogin(server, port);
        serial.atCommandOK("AT+SMTPAUTH=1,\"" + user + "\",\"" + password + "\"");
    }

    /**
     * Configure POP server for receiving e-mail. You must login before using
     * {@link #emailPOP3Get(boolean)} or {@link #emailPOP3Delete(int)}.
     *
     * @param server POP server host name or IP.
     * @param port POP port. Typically 110.
     * @param user POP username.
     * @param password POP password.
     * @throws FonaException
     */
    public void emailPOP3Login(String server, Integer port, String user, String password) throws FonaException {
        FonaPOP3 pop3 = new FonaPOP3(serial);
        pop3.login(server, port, user, password);
    }

    public void emailPOP3Logout() throws FonaException {
        FonaPOP3 pop3 = new FonaPOP3(serial);
        pop3.logout();
    }

    /**
     * Send e-mail. Note that if the FonaEmailMessage has more than one FROM
     * address, only the first one will be used.
     *
     * Ensure SMTP is configured using
     * {@link #emailSMTPLogin(java.lang.String, java.lang.Integer)} or
     * {@link #emailSMTPLogin(java.lang.String, java.lang.Integer, java.lang.String, java.lang.String)}
     *
     * @param email FonaEmailMessage object.
     * @throws FonaException
     */
    public void emailSMTPSend(FonaEmailMessage email) throws FonaException {

        /**
         * Set FROM:
         */
        String fromName = email.from.firstEntry().getValue();
        String fromAddress = email.from.firstEntry().getKey();
        serial.atCommandOK("AT+SMTPFROM=\"" + fromAddress + "\",\"" + fromName + "\"");

        /**
         * Set TO: recipients
         */
        int index = 0;
        for (Map.Entry<String, String> entry : email.to.entrySet()) {
            serial.atCommandOK("At+SMTPRCPT=0," + index + ",\"" + entry.getKey() + "\",\"" + entry.getValue() + "\"");
            index++;
        }

        /**
         * Set CC: recipients
         */
        index = 0;
        for (Map.Entry<String, String> entry : email.cc.entrySet()) {
            serial.atCommandOK("AT+SMTPRCPT=1," + index + ",\"" + entry.getKey() + "\",\"" + entry.getValue() + "\"");
            index++;
        }

        /**
         * Set BCC: recipients
         */
        index = 0;
        for (Map.Entry<String, String> entry : email.bcc.entrySet()) {
            serial.atCommandOK("At+SMTPRCPT=2," + index + ",\"" + entry.getKey() + "\",\"" + entry.getValue() + "\"");
        }

        /**
         * Set subject and body.
         */
        serial.atCommandOK("AT+SMTPSUB=\"" + email.subject + "\"");
        serial.write("AT+SMTPBODY=" + email.body.length());
        serial.expect("DOWNLOAD", 5000);
        serial.atCommandOK(email.body);

        /**
         * Send it.
         */
        serial.atCommandOK("AT+SMTPSEND");
        String response = serial.expect("+SMTPSEND", 30000);
        if (!response.equals("+SMTPSEND: 1")) {
            throw new FonaException("Email send failed: " + response);
        }
    }

    /**
     * Download email messages from POP3 server. Will fail if not logged into
     * POP3 server with
     * {@link #emailPOP3Login(java.lang.String, java.lang.Integer, java.lang.String, java.lang.String)}
     *
     * @param markAsRead if true, message will be marked as read.
     * @return A list of {@link com.angryelectron.fona.FonaEmailMessage}.
     * @throws FonaException
     */
    public List<FonaEmailMessage> emailPOP3Get(boolean markAsRead) throws FonaException {
        List<FonaEmailMessage> messages = new ArrayList<>();
        FonaPOP3 pop3 = new FonaPOP3(serial);
        for (int i = 1; i <= pop3.getNewMessageCount(); i++) {
            FonaEmailMessage message = pop3.readMessage(i, markAsRead);
            messages.add(message);
        }
        return messages;
    }

    /**
     * Delete email message from POP3 server.
     *
     * @param messageId The ID of the message on the POP3 server (not the
     * "Message-id" header value).
     */
    public void emailPOP3Delete(int messageId) throws FonaException {
        FonaPOP3 pop3 = new FonaPOP3(serial);
        pop3.delete(messageId);
    }

    /**
     * Set state of GPIO output.
     *
     * @param pin Pin number (1-3).
     * @param value 1=high, 0=low
     * @throws com.angryelectron.fona.FonaException
     */
    public void gpioSetOutput(int pin, int value) throws FonaException {
        if (pin < 1 || pin > 3) {
            throw new FonaException("Invalid pin (1-3).");
        }
        if (value != 0 && value != 1) {
            throw new FonaException("Invalid pin value (0,1)");
        }
        String response = serial.atCommand("AT+SGPIO=0," + pin + ",1," + value);
        if (!response.trim().equals("OK")) {
            throw new FonaException("gpioSetOutput failed.");
        }
    }

    /**
     * Read state of GPIO input pin.
     *
     * @param pin Pin number (1-3).
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
     * Configure GPIO pin as an input.
     *
     * @param pin Pin number (1-3).
     * @throws com.angryelectron.fona.FonaException
     */
    public void gpioSetInput(int pin) throws FonaException {
        if (pin < 1 || pin > 3) {
            throw new FonaException("Invalid pin value (1-3).");
        }
        String response = serial.atCommand("AT+SGPIO=0," + pin + ",0");
        if (!response.trim().equals("OK")) {
            throw new FonaException("GPIO config input pin failed.");
        }
    }

    /**
     * Enable GPRS. GPRS must be enabled before using any of the other GPRS
     * methods. Most mobile carriers publish their APN credentials. On boot,
     * GPRS is 'enabled' but not 'authenticated'.
     *
     * @param apn Cell provider APN address.
     * @param user APN user.
     * @param password APN password.
     * @throws FonaException
     */
    public void gprsEnable(String apn, String user, String password) throws FonaException {
        try {
            serial.atCommand("AT+CGATT=1", 10000);
            serial.atCommandOK("AT+SAPBR=3,1,\"CONTYPE\",\"GPRS\"");
            serial.atCommandOK("AT+SAPBR=3,1,\"APN\",\"" + apn + "\"");
            serial.atCommandOK("AT+SAPBR=3,1,\"USER\",\"" + user + "\"");
            serial.atCommandOK("AT+SAPBR=3,1,\"PWD\",\"" + password + "\"");
            serial.atCommandOK("AT+SAPBR=1,1");
        } catch (FonaException ex) {
            throw new FonaException("GPRS enable failed: " + ex.getMessage());
        }
    }

    /**
     * Disable GPRS.
     *
     * @throws FonaException
     */
    public void gprsDisable() throws FonaException {
        try {
            serial.atCommand("AT+SAPBR=0,1", 10000);
            serial.atCommand("AT+CGATT=0", 10000);
        } catch (FonaException ex) {
            throw new FonaException("GPRS disable failed: " + ex.getMessage());
        }
    }

    /**
     * Check if GPRS is enabled.
     *
     * @return true if GPRS is enabled and authenticated.
     * @throws FonaException
     */
    public boolean gprsIsEnabled() throws FonaException {
        /**
         * Check if GPRS is enabled.
         */
        String response = serial.atCommand("AT+CGATT?");
        if (response.contains("0")) {
            return false;
        } else if (!response.contains("1")) {
            throw new FonaException("GPRS status check failed: " + response);
        }

        /**
         * GPRS is enabled, but also need to check if there is an active bearer
         * profile.
         */
        response = serial.atCommand("AT+SAPBR=2,1");
        if (!response.endsWith("OK")) {
            throw new FonaException("Can't query GPRS context: " + response);
        }
        String fields[] = response.split(",");

        /**
         * If response is 1, bearer is connected and gprs is enabled.
         */
        return fields[1].equals("1");
    }

    /**
     * HTTP GET request.
     *
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

        /**
         * Make sure the HTTP connection is terminated, regardless
         * of what happens in this try block.
         * 
         */
        String response;
        try {
            serial.atCommandOK("AT+HTTPPARA=\"CID\",1");
            serial.atCommandOK("AT+HTTPPARA=\"URL\",\"" + address + "\"");
            serial.atCommandOK("AT+HTTPACTION=0");
            String httpResult = serial.expect("HTTPACTION", 5000).trim();
            if (!httpResult.startsWith("+HTTPACTION: 0")) {                
                throw new FonaException("Invalid HTTP response: " + httpResult);
            }
            String httpFields[] = httpResult.split(",");
            Integer httpStatusCode = Integer.parseInt(httpFields[1]);
            if (httpStatusCode != 200) {
                throw new HTTPException(httpStatusCode);
            }
            response = serial.atCommand("AT+HTTPREAD", 5000);
        } finally {
            serial.atCommandOK("AT+HTTPTERM");
        }
        //ignore the first line and the last line (which is "OK\n")
        int start = response.indexOf(System.lineSeparator());
        return response.substring(start, response.length() - 3);
    }

    /**
     * Get battery voltage.
     *
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
        return Integer.parseInt(voltage.trim());
    }

    /**
     * Get battery charge level.
     *
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
     *
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

    /**
     * Get Time from GSM connection. This will fail with error code 601 if GPRS
     * has not been enabled.
     *
     * @return DateTime in UTC.
     * @throws com.angryelectron.fona.FonaException
     */
    public Date gprsTime() throws FonaException {
        String response = serial.atCommand("AT+CIPGSMLOC=2,1");
        if (!response.contains("+CIPGSMLOC: 0")) {
            throw new FonaException("Can't get time: " + response);
        }
        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String field[] = response.split(",");
        try {
            return df.parse(field[1] + " " + field[2]);
        } catch (ParseException ex) {
            throw new FonaException(ex.getMessage());
        }
    }

    /**
     * Send SMS message.
     *
     * @param phoneNumber Recipient's phone number.
     * @param message Message body. Max length 160 characters.
     * @throws FonaException If message cannot be sent.
     */
    public void smsSend(String phoneNumber, String message) throws FonaException {
        FonaSms sms = new FonaSms(serial);
        sms.send(phoneNumber, message);
    }

    /**
     * Read SMS message. Also see
     * {@link #onSmsMessageReceived(com.angryelectron.fona.FonaSmsMessage)}.
     *
     * @param messageId ID of message.
     * @param markAsRead If true, UNREAD messages will be marked as READ.
     * @return SMS message.
     * @throws FonaException
     */
    public FonaSmsMessage smsRead(int messageId, boolean markAsRead) throws FonaException {
        FonaSms sms = new FonaSms(serial);
        return sms.read(messageId, markAsRead);
    }

    /**
     * Read all SMS messages.
     *
     * @param folder Folder containing messages.
     * @param markAsRead
     * @return A list of messages. List is empty if no messages were found.
     * @throws FonaException
     */
    public List<FonaSmsMessage> smsRead(FonaSmsMessage.Folder folder, boolean markAsRead) throws FonaException {
        FonaSms sms = new FonaSms(serial);
        return sms.list(folder, markAsRead);
    }

    /**
     * Internal - Called when an SMS message has been received. To be notified
     * of incoming SMS messages, implement
     * {@link com.angryelectron.fona.FonaEventHandler} in your application and
     * use
     * {@link com.angryelectron.fona.FonaSerial#open(java.lang.String, java.lang.Integer)}.
     *
     * @param message FonaSmsMessage object containing only the message ID
     * number.
     */
    @Override
    public void onSmsMessageReceived(FonaSmsMessage message) {
        try {
            this.applicationEventHandler.onSmsMessageReceived(smsRead(message.id, true));
        } catch (FonaException ex) {
            this.applicationEventHandler.onError(ex.getMessage());
        }
    }

    /**
     * Internal. Called when an error has occurred handling an unsolicited
     * serial response.
     *
     * @param message Error message.
     */
    @Override
    public void onError(String message) {
        this.applicationEventHandler.onError(message);
    }

    /**
     * Power-down the SIM800 module. This may have different implications
     * depending on how the hardware has been configured. For example, a FONA
     * with KEY tied to GND will reboot after this command, although the time
     * required to reboot varies. In testing, sometimes this command returns
     * "NORMAL POWER DOWN" and other times returns nothing, so the result is
     * ignored. In general, it is probably best to use hardware-specific methods
     * (like FONA's KEY line) to control the power state of the device.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    public void simPowerOff() throws FonaException {
        serial.atCommand("AT+CPOWD=0");
    }

    /**
     * Read Analog/Digital Converter.
     *
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
        return Integer.parseInt(value.trim());
    }

    /**
     * Unlock SIM card.
     *
     * @param password SIM card password.
     */
    public void simUnlock(String password) throws FonaException {
        serial.atCommandOK("AT+CPIN=" + password);
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
