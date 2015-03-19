/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */

package com.angryelectron.fona;

import com.angryelectron.fona.Fona.Network;

/**
 * Handle asynchronous ("unsolicited") events like incoming calls and SMS
 * messages and module status changes. Implement this interface in your
 * application, then attach it using
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
    
    /**
     * Called after boot/reset to indicate the serial interface is ready.  This event 
     * is used internally, so does not need to be implemented for normal operation,
     * but is provided should an application have a need for it.
     */
    public void onSerialReady();
    
    /**
     * Called whenever the network registration status changes.  This event is
     * used internally, so does not need to be implemented for normal operation,
     * but is provided should an application have a need for it.
     * @param status The updated network status.
     */
    public void onNetworkStatusChange(Network status);
}
