/**
 * Fona Java Library for SIM800. Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import com.angryelectron.fona.FonaSmsMessage.Folder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal methods for sending and receiving SMS messages. 
 */
class FonaSms {

    private final FonaSerial serial;

    /**
     * SMS Message format.
     */
    private enum Format {

        TEXT, PDU
    };

    /**
     * Constructor.
     * @param serial 
     */
    FonaSms(FonaSerial serial) {
        this.serial = serial;
    }

    /**
     * Set SMS message format. Most methods in this library require the TEXT
     * format.
     *
     * @param format
     * @throws FonaException
     */
    private void format(Format format) throws FonaException {
        switch (format) {
            case TEXT:
                serial.atCommandOK("AT+CMGF=1");
                break;
            case PDU:
                serial.atCommandOK("AT+CMGF=0");
                break;
        }
    }

    /**
     * Delete SMS message.
     *
     * @param messageId Id of the message to delete.
     * @throws FonaException
     */
    void delete(int messageId) throws FonaException {
        serial.atCommandOK("AT+CMGD=" + messageId);
    }
    
    /**
     * List all messages in a folder.
     *
     * @param folder {@link com.angryelectron.fona.FonaSms#Format}
     * @param markAsRead When true, message folder will change from UNREAD to
     * READ after listing.
     * @return A list of {@link com.angryelectron.fona.FonaSmsMessage}. List is
     * empty if no messages are found in the specified folder.
     * @throws FonaException
     */
    List<FonaSmsMessage> list(Folder folder, boolean markAsRead) throws FonaException {
        format(Format.TEXT);
        List<FonaSmsMessage> messages = new ArrayList<>();
        Pattern headerPattern = Pattern.compile("\\+CMGL: ([0-9]+),\"([A-Z ]+)\",\"([\\+0-9]+)\",\"\",\"([0-9/,:]+)[-\\+][0-9]+\"");

        /* list all messages in folder and validate response*/
        String command = "AT+CMGL=\"" + folder.stat() + "\"";
        if (!markAsRead) {
            command += ",1";
        }
        String response = serial.atCommand(command);
        if (response.startsWith("+CMS ERROR:")) {
            throw new FonaException("Error reading SMS: " + response);
        } else if (response.startsWith("OK")) {
            /* no messages in this folder */
            return messages;
        }

        /* parse response into individual SMS messages line-by-line */
        FonaSmsMessage sms = null;
        String lines[] = response.split(System.lineSeparator());
        for (String line : lines) {
            Matcher matcher = headerPattern.matcher(line);
            if (matcher.find()) {
                if (sms != null) {
                    messages.add(sms);
                }
                sms = new FonaSmsMessage();
                sms.id = Integer.parseInt(matcher.group(1));
                sms.folder = Folder.get(matcher.group(2));
                sms.sender = matcher.group(3);
                sms.timestamp = parseTimestamp(matcher.group(4));
                sms.message = "";
            } else {
                sms.message += line;
            }
        }
        return messages;
    }

    /**
     * Read an SMS message.
     * @param messageId Id of the message to read.
     * @param markAsRead if true, UNREAD messages will be marked as READ.
     * @return SMS message.
     * @throws FonaException 
     */
    FonaSmsMessage read(int messageId, boolean markAsRead) throws FonaException {
        format(Format.TEXT);
        String command = "AT+CMGR=" + messageId;        
        if (!markAsRead) {
            command += ",1";
        }        
        String response = serial.atCommand(command);
        if (response.startsWith("+CMS ERROR:")) {
            throw new FonaException("Error reading SMS: " + response);
        } else if (response.startsWith("OK")) {
            throw new FonaException("Invalid SMS message number.");
        }
        Pattern pattern = Pattern.compile("\\+CMGR: \"([A-Z ]+)\",\"([+0-9]+)\",\"\",\"([0-9/,:]+)[-+][0-9]+\"");
        FonaSmsMessage sms = new FonaSmsMessage();
        String lines[] = response.split(System.lineSeparator());
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                sms = new FonaSmsMessage();
                sms.id = messageId;
                sms.folder = Folder.get(matcher.group(1));
                sms.sender = matcher.group(2);
                sms.timestamp = parseTimestamp(matcher.group(3));
                sms.message = "";
            } else {
                sms.message += line;
            }
        }
        /* trim OK */
        sms.message = sms.message.substring(0, sms.message.length() - 2);
        return sms;
    }
    
    /**
     * Send SMS message.
     *
     * @param phoneNumber Recipient's phone number.
     * @param message Message body. Max length 160 characters.
     * @throws FonaException If message cannot be sent.
     */
    void send(String phoneNumber, String message) throws FonaException {
        if (message.length() > 160) {
            throw new FonaException("SMS messages cannot exceed 160 characters.");
        }
        format(Format.TEXT);
        serial.atCommandOK("AT+CSCS=\"GSM\"");

        //the CMGS command will return ">", which can't be read using read() or
        //expect(), as it isn't a complete line.
        //serial.write("AT+CMGS=\"" + phoneNumber +"\"");
        try {
            serial.atCommand("AT+CMGS=\"" + phoneNumber + "\"", 1000);
        } catch (FonaException ex) {
            /* timeout is expected - we need to delay waiting for the > prompt */
        }

        /* notice up to 60 seconds required for response! */
        String response = serial.atCommand(message + "\u001A", 60000);
        if (!response.contains("OK")) {
            throw new FonaException("SMS Send Failed: " + response);
        }
    }

    /**
     * Internal - set timestamp from AT command response.
     *
     * @param timestamp
     * @throws ParseException
     */
    static Date parseTimestamp(String timestamp) throws FonaException {        
        SimpleDateFormat df = new SimpleDateFormat("yy/MM/dd,HH:mm:ss");
        try {
            return df.parse(timestamp);
        } catch (ParseException ex) {
            throw new FonaException(ex.getMessage());
        }
    }
}
