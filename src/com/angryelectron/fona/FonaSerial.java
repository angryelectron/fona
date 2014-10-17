/**
 * Fona for Java Copyright 2014 Andrew Bythell <abythell@ieee.org>
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

/**
 * Basic IO methods for communicating with a SIM800 Series module over serial
 * port using RXTX. See
 * http://www.adafruit.com/datasheets/sim800_series_at_command_manual_v1.01.pdf
 * for details.
 */
public class FonaSerial implements SerialPortEventListener {

    private SerialPort serialPort;
    private OutputStream outStream;
    private InputStream inStream;
    private String readBuffer;    
    private String endMarker = "";

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
     * Internal use only. Wait for serial data to be available, then parse and
     * put result in readBuffer.  If isSingleLine=true, only 1 line will be read
     * otherwise reading continues until OK or ERROR is found.  The read() methods
     * handle timeouts.
     *
     * @param event
     */
    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPortEvent.DATA_AVAILABLE) {
            return;
        }    
        readBuffer = null;
        try {
            BufferedReader buffer = new BufferedReader(new InputStreamReader(inStream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffer.readLine()) != null) { 
                System.out.println("DEBUG READ: " + line);                   
                builder.append(line);
                if (!endMarker.isEmpty() && line.contains(endMarker)) {
                    System.out.println("DEBUG ENDMARKER: " + line);                   
                    synchronized(this) {
                        readBuffer = builder.toString();
                        this.notify();
                        return;
                    }
                }
                else if (line.equals("OK") || line.contains("ERROR")) {
                    synchronized(this) {
                        readBuffer = builder.toString();
                        this.notify();
                        return;
                    }
                }
            }
        } catch (IOException ex) {
            //TODO: handle this better.
            System.out.println(ex.getMessage());
        }
    }
    
    /**
     * Discard and serial data waiting to be read.
     * @throws java.io.IOException
     */
    public void flush() throws IOException {
        int bytes = inStream.available();
        if (bytes > 0) {
            inStream.skip(bytes);
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
        //TODO: remove this
        System.out.println("DEBUG WRITE: " + data);        
        data += "\r";
        try {
            outStream.write(data.getBytes());
        } catch (IOException ex) {
            throw new FonaException(ex.getMessage());
        }
        
    }

    /**
     * Read an AT response.  Use to gather responses to AT commands that
     * finish with ERROR or OK.
     *
     * @param timeout Time in milliseconds to wait for a response.
     * @return AT response. Echo is off. CR and LF stripped.
     * @throws com.angryelectron.fona.FonaException
     */
    public String read(Integer timeout) throws FonaException {
        /**
         * Wait until the Serial Event notifies us that data is available. If no
         * data arrives in time, throw an exception.
         */
        synchronized (this) {
            try {
                this.wait(timeout);
            } catch (InterruptedException ex) {
                throw new FonaException(ex.getMessage());
            }
        }

        /**
         * If readBuffer is null, this is a timeout. This is necessary as there
         * is no other way to determine if we timed out or were notified.
         */
        if (readBuffer == null) {
            throw new FonaException("Timeout waiting for FONA response.");
        }
        return readBuffer;
    }
    
    /**
     * Read a line from the serial port.  Use to read unsolicited responses
     * from the Sim800.
     * @param timeout
     * @return
     * @throws FonaException 
     */
    public String readline(String endMarker, Integer timeout) throws FonaException {
        this.endMarker = endMarker;
        String result = read(timeout);
        endMarker = "";
        return result;
    }

    /**
     * Send an AT command and get the response.
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
     *
     * @param command AT command
     * @param timeout Time in milliseconds to wait for a response.
     * @return
     * @throws com.angryelectron.fona.FonaException
     */
    public String atCommand(String command, Integer timeout) throws FonaException {
        this.write(command);
        return this.read(timeout);
    }
    
    /**
     * Send an AT command with default value. Checks if response is OK. Intended
     * as a short-cut for commands that don't return anything meaningful.
     *
     * @param command At command
     * @throws FonaException if response is not OK.
     */
    public void atCommandOK(String command) throws FonaException {
        this.write(command);
        String response = this.read(FONA_DEFAULT_TIMEOUT);
        if (!response.equals("OK")) {
            throw new FonaException("AT command failed: " + response);
        }
    }   
    
    public String atCommand(String command, Integer timeout, String expected) throws FonaException {
        this.write(command);
        return this.readline(expected, timeout);
    }
}
