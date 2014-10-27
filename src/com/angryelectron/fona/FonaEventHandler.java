/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */

package com.angryelectron.fona;

/**
 * Implement this interface to catch unsolicited events like incoming calls,
 * SMS messages, and e-mail.  This interface is also used internally to parse
 * unsolicited responses into their corresponding objects.
 */
public interface FonaEventHandler {
    public void onSmsMessageReceived(FonaSmsMessage sms);
    public void onError(String message);
}
