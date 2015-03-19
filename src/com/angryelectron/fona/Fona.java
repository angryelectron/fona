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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.ws.http.HTTPException;

/**
 * Primary class for controlling a Fona / SIM800 cellular module via serial
 * port.
 *
 * <p>
 * Based on the
 * <a href="http://www.adafruit.com/datasheets/sim800_series_at_command_manual_v1.01.pdf">
 * Sim800 AT Command Manual</a>, this class only implements a subset of the
 * features supported by the SIM800. Feel free to implement any missing features
 * by contributing to the
 * <a href="http://github.com/angryelectron/fona">GitHub Project</a>.</p>
 *
 
 * <p><b>Notes</b></p>
 * <ul>
 *
 * <li>All network-based operations use a hard coded Bearer Profile Identifier of
 * 1.</li>
 *
 * <li>Some settings, required for correct operation of this API, will be
 * written to NVRAM. See {@link #open(java.lang.String, java.lang.Integer)} for
 * a details.</li>
 * </ul>
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
     * These flag are set by the unsolicited event handlers when different
     * modules become ready
     */
    private volatile boolean isSerialReady = false;
    private volatile boolean isCallReady = false;
    private volatile boolean isSmsReady = false;
    volatile Network networkStatus = Network.UNKNOWN;

    /**
     * Open serial port connection to SIM800 module. This method will alter the
     * configuration of the SIM8000 with settings necessary for the proper
     * operation of the API, writing the values to NVRAM:
     * <br>
     * <ul>
     * <li>Auto-baud is disabled (AT+IPR=baud)</li>
     * <li>Local echo is disabled (ATE0).</li>
     * <li>Network registration unsolicited result code is enabled
     * (AT+CGREG=1)</li>
     * <li>All other settings restored to factory-defaults (AT&amp;F)</li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> Disabling auto-baud is required to receive notification when
     * the serial port is ready for use. If the application re-enables
     * auto-baud,
     * {@link #simWaitForReady(int, com.angryelectron.fona.Fona.Ready)} will
     * always timeout when used with {@link Ready#SERIAL} or {@link Ready#BOTH},
     * but {@link Ready#NETWORK} will work and provide reasonable assurance that
     * the serial module is also ready.</p>
     *
     * @param port Port name (/dev/ttyUSB1, COM7, etc.)
     * @param baud Baud rate. 115200 is recommended.
     * @throws com.angryelectron.fona.FonaException if port cannot be opened
     */
    public void open(String port, Integer baud) throws FonaException {
        /**
         * Make sure RXTX can enumerate this port. This is to support platforms
         * like Raspberry Pi and Beaglebone, without having to worry about
         * setting the property on the command line.
         */
        Properties properties = System.getProperties();
        String currentPorts = properties.getProperty("gnu.io.rxtx.SerialPorts", "");
        if (currentPorts.equals(port)) {
            properties.setProperty("gnu.io.rxtx.SerialPorts", port);
        } else {
            properties.setProperty("gnu.io.rxtx.SerialPorts", currentPorts + ":" + port);
        }        
        properties.setProperty("gnu.io.rxtx.NoVersionOutput", "true");

        /**
         * Connect the serial port, listener thread, and this class so
         * unsolicited responses can be handled.
         */
        unsolicitedListener = new FonaUnsolicitedListener(serial.getUnsolicitedQueue());
        unsolicitedListener.start(this);

        serial.open(port, baud);

        /**
         * Settings that are critical to the proper operation of the Fona
         * library.
         */
        serial.atCommand("AT");
        serial.atCommand("AT&F");
        serial.atCommand("ATE0"); //turn off local echo                
        serial.atCommandOK("AT+IPR=" + baud); //use fixed baud-rate
        serial.atCommandOK("AT+CGREG=1"); //turn on unsolicited network status 
        serial.atCommandOK("AT&W"); //persist settings through reboot/reset

        /**
         * On reboot, GPRS is in a strange state:  enabled, but without a valid
         * bearer profile.  Disable GPRS so it is in a known state.
         */
        gprsDisable();
    }

    /**
     * Open serial port connection to SIM800 module and set handler for
     * asynchronous events.
     *
     * @param port Port name (/dev/ttyUSB1, COM7, etc.)
     * @param baud Baud rate. 115200 is recommended.
     * @param handler FonaEventHandler for handling incoming SMS and Calls.
     * @throws FonaException if port cannot be opened
     */
    public void open(String port, Integer baud, FonaEventHandler handler) throws FonaException {
        this.applicationEventHandler = handler;
        this.open(port, baud);
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
        isSerialReady = false;
    }

    /**
     * Configure SMTP server for sending e-mail without user authentication.
     *
     * @param server SMTP server hostname or IP.
     * @param port SMTP port, typically 25.
     * @throws FonaException if smtp configuration fails
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
     * @throws FonaException if configuration fails
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
     * @throws FonaException if configuration fails
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
     * @throws FonaException if email cannot be send
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
     * @throws FonaException if email cannot be downloaded
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
     * @throws com.angryelectron.fona.FonaException if id is invalid or message
     * cannot be deleted.
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
     * @throws com.angryelectron.fona.FonaException if GPIO cannot be set
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
     * @throws com.angryelectron.fona.FonaException if GPIO cannot be read
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
     * @throws com.angryelectron.fona.FonaException if GPIO cannot be configured
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
     * GPRS is 'enabled' but not 'authenticated'. This call will block up to 15
     * seconds while waiting for the network to register.
     *
     * @param apn Cell provider APN address.
     * @param user APN user.
     * @param password APN password.
     * @throws FonaException if GPRS cannot be enabled.
     */
    public void gprsEnable(String apn, String user, String password) throws FonaException {
        gprsEnable(apn, user, password, 15000);
    }

    /**
     * Enable GPRS with user-defined timeout.
     *
     * @param apn Cell provider APN address.
     * @param user APN user name.
     * @param password APN password.
     * @param timeout Max time to wait, in milliseconds.
     * @throws FonaException if GPRS cannot be enabled
     */
    public void gprsEnable(String apn, String user, String password, Integer timeout) throws FonaException {
        if (gprsIsEnabled()) {
            return;
        }
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
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            if (networkStatus == Network.REGISTERED || networkStatus == Network.ROAMING) {
                return;
            }
        }
    }

    /**
     * Disable GPRS.
     *
     * @throws FonaException if GPRS cannot be disabled
     */
    public void gprsDisable() throws FonaException {
        try {
            serial.atCommand("AT+SAPBR=0,1", 10000); /* returns ERROR if no bearer */
            serial.atCommand("AT+CGATT=0", 10000);
        } catch (FonaException ex) {
            throw new FonaException("GPRS disable failed: " + ex.getMessage());
        }
        if (networkStatus == Network.UNKNOWN) {
            /**
             * If this is the first GPRS call, the status will be UNKNOWN.
             * Disabling GPRS without a bearer profile will not generate the
             * unsolicited message needed to change the status, so safely change
             * it now.
             */
            networkStatus = Network.UNREGISTERED;
        }
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) {
            if (networkStatus == Network.UNREGISTERED) {
                return;
            }
        }
    }

    /**
     * Check if GPRS is enabled.
     *
     * @return true if GPRS is enabled and authenticated.
     * @throws FonaException if GPRS status cannot be determined
     */
    public boolean gprsIsEnabled() throws FonaException {

        if (!networkStatus.equals(Network.REGISTERED) && !networkStatus.equals(Network.ROAMING)) {
            return false;
        }

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
     * @throws FonaException if GPRS is not enabled or GET request fails
     */
    public String gprsHttpGet(String url) throws FonaException {
        if (!url.toLowerCase().startsWith("http")) {
            throw new FonaException("Invalid protocol.  Only HTTP is supported.");
        }
        if (!this.gprsIsEnabled()) {
            throw new FonaException("GPRS is not enabled.");
        }
        String address = url.replaceAll("http://", "");

        serial.atCommand("AT+HTTPTERM"); /* don't care if this works or not */

        serial.atCommandOK("AT+HTTPINIT");

        /**
         * Make sure the HTTP connection is terminated, regardless of what
         * happens in this try block.
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
            /* try to shut down, regardless of outcome */
            serial.atCommand("AT+HTTPTERM");
        }

        /**
         * Response format: \n+HTTPREAD:<data_len>\n<data>\nOK
         */
        Pattern pattern = Pattern.compile("\n\\+HTTPREAD: [0-9]+\n(.*)\nOK", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new FonaException("HTTP Read Failed: " + response);
        }
        return matcher.group(1);
    }

    /**
     * Get battery voltage.
     *
     * @return Battery voltage in millivolts.
     * @throws FonaException if voltage cannot be read
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
     * @throws FonaException if charge level cannot be read
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
     * @throws FonaException if charging state cannot be determined
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
     * @throws com.angryelectron.fona.FonaException if GPRS is not enabled or time
     * cannot be fetched
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
     * @throws FonaException if messageId is invalid or message cannot be read.
     */
    public FonaSmsMessage smsRead(int messageId, boolean markAsRead) throws FonaException {
        FonaSms sms = new FonaSms(serial);
        return sms.read(messageId, markAsRead);
    }

    /**
     * Read all SMS messages.
     *
     * @param folder Folder containing messages.
     * @param markAsRead If true, message will be marked as read after it is retrieved.
     * @return A list of messages. List is empty if no messages were found.
     * @throws FonaException if SMS messages cannot be retrieved.
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
        if (applicationEventHandler != null) {
            try {
                this.applicationEventHandler.onSmsMessageReceived(smsRead(message.id, true));
            } catch (FonaException ex) {
                this.applicationEventHandler.onError(ex.getMessage());
            }
        }
    }

    /**
     * Internal. Called when an error has occurred handling an unsolicited
     * serial response. Will also fire the application's FonaEventHandler, if
     * registered.
     *
     * @param message Error message.
     */
    @Override
    public void onError(String message) {
        if (applicationEventHandler != null) {
            this.applicationEventHandler.onError(message);
        }
    }

    /**
     * Cellular network registration status used with
     * {@link FonaEventHandler#onNetworkStatusChange(com.angryelectron.fona.Fona.Network) }.
     * Status descriptions are quoted from the SIM800 AT Command Manual (ie.
     * abbreviations not necessarily understood).
     */
    public enum Network {

        /**
         * Not registered. MT is not currently searching an operator to register
         * with. The GPRS service is disabled, the UE is allowed to re-attached
         * for GPRS if requested by the user.
         */
        UNREGISTERED(0),
        /**
         * Registered, home network.
         */
        REGISTERED(1),
        /**
         * Not registered, but MT is currently trying to attach or searching an
         * operator to register with. The GPRS service is enabled, but an
         * allowable PLMN is currently not available. The UE will start a GPRS
         * attach as soon as an allowable PLMN is available.
         */
        SEARCHING(2),
        /**
         * Registration denied. The GPRS servis is disabled, the UE is not
         * allowed to attached for GPRS if it is requested by the user.
         */
        DENIED(3),
        /**
         * Unknown.
         */
        UNKNOWN(4),
        /**
         * Registered, roaming.
         */
        ROAMING(5);

        private final int value;

        Network(int value) {
            this.value = value;
        }
    };

    /**
     * Internal. Called when network status changes. Will also fire the
     * application's FonaEventHandler, if registered.
     *
     * @param status New network state.
     */
    @Override
    public void onNetworkStatusChange(Network status) {
        this.networkStatus = status;
        if (applicationEventHandler != null) {
            this.applicationEventHandler.onNetworkStatusChange(status);
        }
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
     * @throws com.angryelectron.fona.FonaException if fona is not responding.
     */
    public void simPowerOff() throws FonaException {
        serial.atCommand("AT+CPOWD=0");
        isSerialReady = false;
    }

    /**
     * Sleep Mode. When enabled, the SIM module enters sleep mode when DTR is
     * high, and wakes when DTR is low. When asleep, the serial port is
     * inaccessible until 50ms after wake-up.
     * <p>
     * <b>Note:</b> FONA modules don't use the DTR pin, so if sleep is enabled
     * the only way to wake-up is to reset/power-cycle.</p>
     *
     * @param enable Use low-power sleep mode when true.
     * @throws FonaException if fona is not responding
     */
    public void simSleepMode(boolean enable) throws FonaException {
        if (enable) {
            serial.atCommandOK("AT+CSCLK=1");
        } else {
            serial.atCommandOK("AT+CSCLK=0");
        }
    }

    /**
     * Internal. Used by methods that reboot or reset the module to wait until
     * the module is ready for use. When called, this method will fire the
     * application's FonaEventHandler, if registered.
     */
    @Override
    public void onSerialReady() {
        isSerialReady = true;
        if (applicationEventHandler != null) {
            applicationEventHandler.onSerialReady();
        }
    }

    /**
     * Functionality modes used with
     * {@link #simFunctionality(com.angryelectron.fona.Fona.Mode)}.
     */
    public enum Mode {

        /**
         * Minimum functionality. RF function and SIM card functions are
         * disabled, but serial port is still accessible. Current consumption
         * 0.796 mA (when sleeping).
         */
        MIN,
        /**
         * Full functionality. This is the default mode. Current consumption
         * 1.16 mA (when sleeping).
         */
        FULL,
        /**
         * Flight/Airplane mode. RF functions are disabled. Current consumption
         * 0.892 mA (when sleeping).
         */
        FLIGHT
    };

    /**
     * Set functionality mode. Default mode is FULL. In cases where the mode is
     * changing to/from MIN and FLIGHT modes, this method will switch to FULL
     * mode as an intermediate step, as changing between these modes directly is
     * not allowed.
     *
     * <p>
     * If reset is enabled, the application must wait until the serial port is
     * ready before sending AT commands, and until the network is registered
     * before sending any GPRS commands. This can be done using:</p>
     * <ul>
     * <li>{@link #simWaitForReady(int, com.angryelectron.fona.Fona.Ready)
     * }</li>
     * <li>{@link FonaEventHandler#onSerialReady() }</li>
     * <li>{@link FonaEventHandler#onNetworkStatusChange(com.angryelectron.fona.Fona.Network)}</li>
     * </ul>
     * <p>
     * Here is a typical "wakup" sequence:</p>
     * <pre>
     * {@code
     * simFunctionality(Mode.FULL, false); // wake-up
     * simWaitForReady(15000, Ready.NETWORK); // wait for network
     * gprsEnable(APN, USER, PWD); //login with credentials
     * }
     * </pre>
     *
     * @param mode new Functionality Mode: MIN, FULL, or FLIGHT.
     * @throws com.angryelectron.fona.FonaException if mode cannot be set
     */
    public void simFunctionality(Mode mode) throws FonaException {
        /* get current mode */
        String response = serial.atCommand("AT+CFUN?");
        Pattern pattern = Pattern.compile("\\+CFUN: ([014])\n\nOK");
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new FonaException("Functionality query failed: " + response);
        }
        Integer currentMode = Integer.parseInt(matcher.group(1));
        Integer newMode = 1;

        switch (mode) {
            case MIN:
                if (currentMode == 4) {
                    /* can't switch directly from flight to min */
                    simFunctionality(Mode.FULL);
                }
                newMode = 0;
                break;
            case FLIGHT:
                if (currentMode == 0) {
                    /* can't switch directly to min to flight */
                    simFunctionality(Mode.FULL);
                }
                newMode = 4;
                break;
            case FULL:
            default:
                newMode = 1;
                break;
        }
        serial.atCommandOK("AT+CFUN=" + newMode);
    }

    /**
     * Reset / reboot the SIM800 module. Reboots into FULL mode. Be sure to wait
     * for serial and/or network to become ready after reboot. See
     * {@link #simWaitForReady(int, com.angryelectron.fona.Fona.Ready)}.
     *
     * @throws FonaException if fona is not responding
     */
    public void simReset() throws FonaException {
        /**
         * While the docs say you can't reset in FLIGHT mode, testing shows that
         * reseting depends on the current mode and the new mode. Keep things
         * simple by resetting to FULL mode.
         */
        networkStatus = Network.UNKNOWN;
        isSerialReady = false;
        serial.atCommandOK("AT+CFUN=1");
        serial.atCommandOK("AT+CFUN=" + 1 + ",1");
    }

    /**
     * Ready wait modes used with
     * {@link #simWaitForReady(int, com.angryelectron.fona.Fona.Ready)}.
     */
    public enum Ready {

        /**
         * Wait for serial module. When ready, AT commands can be sent but
         * network functions may fail. Will only work when auto-bauding is
         * disabled.
         */
        SERIAL,
        /**
         * Wait for cellular network. Network functions will fail if called
         * before the device has registered with a cellular provider. In cases
         * where auto-baud is enabled,
         */
        NETWORK,
        /**
         * Wait for the serial and cellular network to be ready. Same behavior
         * as NETWORK, but
         */
        BOTH
    };

    /**
     * Wait until SIM800 module is ready. Blocks until ready. For asynchronous
     * notification, use
     * {@link FonaEventHandler#onNetworkStatusChange(com.angryelectron.fona.Fona.Network)}
     * and {@link FonaEventHandler#onSerialReady()}. See
     * {@link #open(java.lang.String, java.lang.Integer)} for an important note
     * about auto-baud and the implications on this method.
     *
     * @param timeout Max time in milliseconds to wait for module to become
     * ready. In tests it took as long as 15 seconds for the network to register
     * following a reboot.
     * @param ready Ready signal to wait for: Serial, Network, or Both.
     * @throws com.angryelectron.fona.FonaException on timeout.
     */
    public void simWaitForReady(int timeout, Ready ready) throws FonaException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            switch (ready) {
                case SERIAL:
                    if (isSerialReady) {
                        return;
                    }
                    break;
                case NETWORK:
                    if ((networkStatus == Network.REGISTERED)
                            || (networkStatus == Network.ROAMING)) {
                        return;
                    }
                    break;
                case BOTH:
                    if (isSerialReady
                            && ((networkStatus == Network.REGISTERED)
                            || (networkStatus == Network.ROAMING))) {
                        return;
                    }
                    break;
            }
        }
        throw new FonaException("WaitForReady Timeout.");
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
     * @throws com.angryelectron.fona.FonaException if error is related to ME
     * functionality.
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
     * @throws com.angryelectron.fona.FonaException if RSSI cannot be read
     */
    public Integer simRSSI() throws FonaException {        
        String response = serial.atCommand("AT+CSQ");
        Pattern pattern = Pattern.compile("\n\\+CSQ: ([0-9]{1,2}),[0-9]\n\nOK", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new FonaException("Signal quality report failed: " + response);
        }               
        
        String rssi = matcher.group(1);
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
     * @throws com.angryelectron.fona.FonaException if no SIM / name cannot be
     * read.
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
     * @throws com.angryelectron.fona.FonaException if temperature cannot be read
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
