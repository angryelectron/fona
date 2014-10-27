/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */

package com.angryelectron.fona;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * SMS Message.
 */
public class FonaSmsMessage {
    public String sender;
    public String message;
    public Date timestamp;
    String response;
    
    /**
     * Internal - set timestamp from AT command response.
     * @param timestamp
     * @throws ParseException 
     */
    void setTimestamp(String timestamp) throws ParseException {
        // 02/01/30,20:40:31+00
        SimpleDateFormat df = new SimpleDateFormat("yy/MM/dd,HH:mm:ss+00");
        this.timestamp = df.parse(timestamp);
    }
    
}
