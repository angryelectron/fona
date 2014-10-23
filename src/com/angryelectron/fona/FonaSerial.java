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
import java.util.TooManyListenersException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * IO methods for communicating with a SIM800 Series module over serial port
 * using RXTX.
 */
public class FonaSerial implements SerialPortEventListener {

    private SerialPort serialPort;
    private OutputStream outStream;
    private InputStream inStream;
    private BufferedReader readBuffer;
    private final LinkedBlockingQueue<String> lineQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> unsolicitedQueue = new LinkedBlockingQueue<>();
    private final FonaUnsolicited unsolicited = new FonaUnsolicited();

    /**
     * Default timeout value (in milliseconds) to wait for a response to a
     * command.
     */
    private final Integer FONA_DEFAULT_TIMEOUT = 5000;

    /**
     * Open serial port connection to FONA.
     *
     * @param port Serial port name. Must be a port recognized by RXTX.
     * @param baud Baud rate. FONA typically is 115200 but has auto baud
     * detection so other rates are supported automagically.
     * @throws com.angryelectron.fona.FonaException
     */
    public void open(String port, Integer baud) throws FonaException {
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
            throw new FonaException("Invalid port.");
        } catch (IOException | TooManyListenersException | UnsupportedCommOperationException | PortInUseException ex) {
            throw new FonaException(ex.getMessage());
        }
    }

    /**
     * Close serial connection.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    public void close() throws FonaException {
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
                if (!line.isEmpty()) {
                    if (unsolicited.isUnsolicited(line)) {
                        System.out.println("DEBUG unsolicited read: " + line); 
                        unsolicitedQueue.add(line);
                    } else {
                        System.out.println("DEBUG read: " + line);
                        lineQueue.add(line);
                    }
                }
            }
        } catch (IOException ex) {
            //TODO: handle this better.
            System.out.println(ex.getMessage());
        }
    }

    /**
     * Discard any serial data waiting to be read.
     *
     * @throws com.angryelectron.fona.FonaException
     */
    public void flush() throws FonaException {
        try {
        int bytes = inStream.available();
        if (bytes > 0) {
            inStream.skip(bytes);
        }
        } catch (IOException ex) {
            throw new FonaException(ex.getMessage());
        }
    }

    /**
     * Send an AT command.
     *
     * @param data The AT command to send, without any linefeeds or newlines
     * (these will be added as required).
     * @throws com.angryelectron.fona.FonaException
     */
    public void write(String data) throws FonaException {
        data += "\r";
        try {
            System.out.println("DEBUG write: " + data);
            outStream.write(data.getBytes());
        } catch (IOException ex) {
            throw new FonaException(ex.getMessage());
        }

    }

    /**
     * Read an AT response. Use to gather responses to AT commands that finish
     * with ERROR or OK.
     *
     * @param timeout Time in milliseconds to wait for a response.
     * @return AT response.
     * @throws com.angryelectron.fona.FonaException
     */
    public String read(Integer timeout) throws FonaException {
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
     * Read an unconventional AT command response. Use to read
     * responses that do not end with OK or ERROR but instead end with a
     * specified keyword.
     *
     * @param keyword A string contained in the last line of the response.
     * @param timeout Time in milliseconds to wait for response.
     * @return Response.
     * @throws FonaException if command fails or times out.
     */
    public String expect(String keyword, Integer timeout) throws FonaException {
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
    public String atCommand(String command) throws FonaException {
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
    public String atCommand(String command, Integer timeout) throws FonaException {
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
    public void atCommandOK(String command) throws FonaException {
        this.write(command);
        String response = this.read(FONA_DEFAULT_TIMEOUT);
        if (!response.endsWith("OK")) {
            throw new FonaException("AT command failed: " + response);
        }
    }
}
