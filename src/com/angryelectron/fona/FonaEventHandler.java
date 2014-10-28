/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */

package com.angryelectron.fona;

/**
 * Handle events like incoming calls and SMS messages.
 * Implement this interface in your application, then attach it using 
 * {@link com.angryelectron.fona.Fona#open(java.lang.String, java.lang.Integer, com.angryelectron.fona.FonaEventHandler)}.
 */
public interface FonaEventHandler {
    
    /**
     * Called when a new SMS message is received.
     * @param message SMS message details.
     */
    public void onSmsMessageReceived(FonaSmsMessage message);
    
    /**
     * Called when an error is encountered while handling unsolicited events.
     * @param message Error message.
     */
    public void onError(String message);
}
