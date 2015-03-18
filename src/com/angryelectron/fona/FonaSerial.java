/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IO methods for communicating with a SIM800 Series module over serial port
 * using RXTX.
 */
class FonaSerial implements SerialPortEventListener {

    private final boolean DEBUG = false;
    private SerialPort serialPort;
    private OutputStream outStream;
    private InputStream inStream;
    private BufferedReader readBuffer;
    private final LinkedBlockingQueue<String> lineQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> unsolicitedQueue = new LinkedBlockingQueue<>();

    /**
     * Default timeout value (in milliseconds) to wait for a response to a
     * command.
     */
    private final Integer FONA_DEFAULT_TIMEOUT = 5000;

    /**
     * This is a full list of 'unsolicited' responses according to section 9.1
     * of the documentation. However - some of these responses *are* solicited:
     * they are only returned in response to an AT command which we issue. Any
     * response of this type is handled using a standard read(), so it must be
     * removed from this list.
     */
    private final List<String> unsolicitedResponsePatterns = Arrays.asList(
            "+CME ERROR:", //see 19.1 for a complete list of codes
            "+CMS ERROR:", //see 192.3 for a complete list of codes
            "+CCWA:", //call is waiting
            "+CLIP:", //calling line identify (??)
            "+CRING:", //incoming call
            "+CREG:", //change in network registration
            "+CCWV:", //accumulated call meter about to reach max
            "+CMTI:", //new SMS message has arrived
            "+CMT:", //new SMS message has arrived
            "+CBM:", //new cell broadcast message
            "+CDS:", //new SMS status report received
            "+COLP:", //related to call presentation (?)
            "+CSSU:", //related to call presentation
            "+CSSI:", //related to call presentation
            "+CLCC:", //report list of current calls automatically (?)
            "*PSNWID:", //refresh network name by network
            "*PSUTTZ:", //refresh time and timezone by network
            "+CTZV:", //refresh network timezone by network
            "DST:", //refresh daylight savings time by network
            "+CSMINS:", //sim card inserted or removed
            "+CDRIND:", //voice call, data has been terminated (?)
            "+CHF:", //current channel
            "+CENG:", //report network information
            "MO RING",
            "MO CONNECTED",
            "+CPIN:", //sim card ready, not ready, or requires pin
            "+CSQN:", //signal quality report
            "+SIMTONE:", //tone started or stopped playing
            "+STTONE:", //tone started or stopped playing
            "+CR:", //intermediate result code
            "+CUSD:", //ussd response
            "RING", //incoming call
            "NORMAL POWER DOWN", //sim is powering down
            //"+CMTE:", //temperature.  Temperature detection not enabled.
            "UNDER-VOLTAGE", //alarm
            "OVER-VOLTAGE", //alarm
            "CHARGE-ONLY MODE", //charging via external charger
            "RDY", //module is ready
            //"+CFUN:", //phonebok initialization is complete
            "CONNECT", //tcp/udp connection info
            "SEND OK", //data sending successful
            "CLOSED", //tcp/udp connection is closed
            "RECV FROM", //remote IP address and port
            "+IPD", //protocol data
            "+RECEIVE",
            "REMOTE IP:",
            "+CDNSGIP", //dns successful or failed
            "+PDP DEACT", //gprs disconnected by network
            //"+SAPBR", //bearer
            //"+HTTPACTION:", //response to HTTP request
            "+FTPGET:",
            "+FTPPUT:",
            "+FTPDELE:",
            "+FTPSIZE:",
            "+FTPMKD:",
            "+FTPRMD:",
            "+FTPLIST:",
            "Call Ready",
            "SMS Ready",
            "+CGREG:"
    );
    private final Pattern unsolicitedPattern = buildUnsolicitedPattern();

    /**
     * Open serial port connection to FONA.
     *
     * @param port Serial port name. Must be a port recognized by RXTX.
     * @param baud Baud rate. FONA typically is 115200 but has auto baud
     * detection so other rates are supported automagically.
     * @throws com.angryelectron.fona.FonaException
     */
    void open(String port, Integer baud) throws FonaException {
        try {
            CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(port);
            serialPort = (SerialPort) portId.open("FONA", FONA_DEFAULT_TIMEOUT);
            serialPort.setSerialPortParams(baud,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            serialPort.notifyOnDataAvailable(true);
            serialPort.addEventListener(this);
            outStream = serialPort.getOutputStream();
            inStream = serialPort.getInputStream();
            readBuffer = new BufferedReader(new InputStreamReader(inStream));
            flush();

        } catch (NoSuchPortException ex) {
            throw new FonaException("Can't open " + port);
        } catch (IOException | TooManyListenersException | UnsupportedCommOperationException | PortInUseException ex) {
            throw new FonaException(ex.getMessage());
        }
    }

    /**
     * Close serial connection.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    void close() throws FonaException {
        try {
            serialPort.notifyOnDataAvailable(false);
            serialPort.removeEventListener();
            if (readBuffer != null) {
                readBuffer.close();
            }
            if (inStream != null) {
                inStream.close();
            }
            if (outStream != null) {
                outStream.close();
            }
            if (serialPort != null) {
                serialPort.close();
            }
        } catch (IOException ex) {
            throw new FonaException(ex.getMessage());
        }
    }

    /**
     * Internal use only. Called whenever there is data waiting to be read from
     * the serial port. All SIM800 responses are in the format
     * <CR><LF>data<CR><LF>, so read line-by-line. Add lines to a queue for
     * processing elsewhere so that no DATA_AVAILABLE events are missed.
     *
     * @param event Only interested in DATA_AVAILABLE event.
     */
    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPortEvent.DATA_AVAILABLE) {
            return;
        }
        try {
            String line;
            while (readBuffer.ready() && (line = readBuffer.readLine()) != null) {
                /**
                 * While it might make sense to ignore empty lines,
                 * blank newlines are needed when parsing e-mail and others.
                 */
                if (isUnsolicited(line)) {
                    if (DEBUG) {
                        System.out.println("DEBUG unsolicited read: " + line);
                    }
                    unsolicitedQueue.add(line);
                } else {
                    if (DEBUG) {
                    System.out.println("DEBUG read: " + line);
                    }
                    lineQueue.add(line);
                }
            }
        } catch (IOException ex) {
            //TODO: handle this better.
            //System.out.println(ex.getMessage());
        }
    }

    /**
     * Discard any serial data waiting to be read.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    void flush() throws FonaException {
        try {
            int bytes = inStream.available();
            if (bytes > 0) {
                inStream.skip(bytes);
            }
        } catch (IOException ex) {
            throw new FonaException(ex.getMessage());
        }
        this.lineQueue.clear();
    }

    /**
     * Write to serial port.
     *
     * @param data Command to send, without any linefeeds or newlines (these
     * will be added as required).
     * @throws com.angryelectron.fona.FonaException
     */
    void write(String data) throws FonaException {
        data += "\r";
        try {
            //System.out.println("DEBUG write: " + data);
            outStream.write(data.getBytes());
        } catch (IOException ex) {
            throw new FonaException(ex.getMessage());
        }

    }

    /**
     * Read from serial port. Use to gather responses to AT commands that finish
     * with ERROR or OK.
     *
     * @param timeout Time in milliseconds to wait for a response.
     * @return AT response.
     * @throws com.angryelectron.fona.FonaException
     */
    String read(Integer timeout) throws FonaException {
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = lineQueue.poll(timeout, TimeUnit.MILLISECONDS)) != null) {
                builder.append(line);
                if (line.equals("OK") || line.contains("ERROR")) {
                    return builder.toString();
                } else {
                    builder.append(System.lineSeparator());
                }
            }
        } catch (InterruptedException ex) {
            throw new FonaException("Read was interrupted");
        }
        throw new FonaException("Read timed out.");
    }

    /**
     * Read from serial port. Use to read responses that do not end with OK or
     * ERROR but instead end with a specified keyword.
     *
     * @param keyword A string contained in the last line of the response.
     * @param timeout Time in milliseconds to wait for response.
     * @return Response.
     * @throws FonaException if command fails or times out.
     */
    String expect(String keyword, Integer timeout) throws FonaException {
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = lineQueue.poll(timeout, TimeUnit.MILLISECONDS)) != null) {
                builder.append(line);
                if (line.contains(keyword)) {
                    return builder.toString();
                } else {
                    builder.append(System.lineSeparator());
                }
            }
        } catch (InterruptedException ex) {
            throw new FonaException("Read was interrupted");
        }
        throw new FonaException("Read timed out.");
    }

    /**
     * Send an AT command and get the response using the default timeout (5
     * seconds).
     *
     * @param command AT command as defined in the SIM800 Series AT Command
     * Manual v1.01.
     * @return Response.
     * @throws com.angryelectron.fona.FonaException
     */
    String atCommand(String command) throws FonaException {
        this.write(command);
        return this.read(FONA_DEFAULT_TIMEOUT);
    }

    /**
     * Send an AT command and get the response with user-defined timeout value.
     * The timeout value should match the max_time for the AT command, as
     * specified in the SIM800 AT Command documentation.
     *
     * @param command AT command
     * @param timeout Time in milliseconds to wait for a response.
     * @return Response.
     * @throws com.angryelectron.fona.FonaException if command fails or times
     * out.
     */
    String atCommand(String command, Integer timeout) throws FonaException {
        this.write(command);
        return this.read(timeout);
    }

    /**
     * Send an AT command with default timeout and fail if the response is
     * anything other than "OK".
     *
     * @param command At command
     * @throws FonaException if response is not OK.
     */
    void atCommandOK(String command) throws FonaException {
        this.write(command);
        String response = this.read(FONA_DEFAULT_TIMEOUT);
        if (!response.endsWith("OK")) {
            throw new FonaException("AT command failed: " + response);
        }
    }

    /**
     * Assemble all unsolicited codes into a huge regex pattern. This seems
     * complicated but is necessary for fast comparisons in the serial event
     * handler. The regex looks like this:
     *
     * ^( (first)|(second)|...).*
     *
     * @return Pattern of unsolicited responses.
     */
    private Pattern buildUnsolicitedPattern() {
        StringBuilder builder = new StringBuilder("^(");
        for (String s : unsolicitedResponsePatterns) {
            builder.append("(");
            builder.append(s);
            builder.append(")|");
        }
        String regex = builder.toString();
        regex = regex.substring(0, regex.length() - 1); //trim last '|'
        regex = regex.replaceAll("\\+", "\\\\+"); //escape literal +
        regex = regex.replaceAll("\\*", "\\\\*"); //escape literal *
        regex = regex + ").*"; //match the rest of the line
        return Pattern.compile(regex);
    }

    /**
     * Check if response contains an unsolicited response.
     *
     * @param line String to be checked.
     * @return True if line matches unsolicited pattern.
     */
    private boolean isUnsolicited(String line) {
        Matcher matcher = unsolicitedPattern.matcher(line);
        return matcher.matches();
    }

    /**
     * Get the queue that contains all the unsolicited responses.
     *
     * @return LinkedBlockingQueue.
     */
    LinkedBlockingQueue<String> getUnsolicitedQueue() {
        return this.unsolicitedQueue;
    }
}
