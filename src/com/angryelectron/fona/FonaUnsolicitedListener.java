/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitor the unsolicited response queue (which is populated by FonaSerial) and
 * fire the internal FonaEventHandler to parse the responses.
 */
class FonaUnsolicitedListener implements Runnable {

    /**
     * The queue to monitor. This must be the same queue that is populated by
     * FonaSerial.serialEvent().
     */
    private final LinkedBlockingQueue<String> queue;

    /**
     * Internal event handler. This implementation needs to parse the raw
     * responses and then fire the external event handler so the application can
     * react.
     */
    private FonaEventHandler internalHandler;

    private final Pattern smsPattern = Pattern.compile("\\+CMTI: \"[A-Z]+\",([0-9]+)");
    private boolean isRunning = false;

    /**
     * Constructor.
     */
    FonaUnsolicitedListener(LinkedBlockingQueue<String> queue) {
        this.internalHandler = null;
        this.queue = queue;
    }

    /**
     * Listen and handle unsolicited responses in a separate thread.
     */
    void start(FonaEventHandler handler) {
        this.internalHandler = handler;
        isRunning = true;
        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * Shutdown the listener and thread.
     */
    void stop() {
        isRunning = false;
        queue.add("SHUTDOWN");
    }

    /**
     * Internal - Monitor and parse the queue, firing the appropriate internal
     * events.
     */
    @Override
    public void run() {
        while (isRunning) {
            try {
                String response = queue.take();  //blocks until data available
                if (!dispatchSms(response) || !response.equals("SHUTDOWN")) {
                    internalHandler.onError("Unknown unsolicited response: " + response);
                }
            } catch (InterruptedException ex) {
                internalHandler.onError(ex.getMessage());
            }
        }
    }

    /**
     * Check if response is from an SMS message and if so fire the appropriate
     * event.
     * @param response The unsolicited response.
     * @return true if response is from an SMS message.
     */
    private boolean dispatchSms(String response) {
        Matcher smsMatcher = smsPattern.matcher(response);
        if (smsMatcher.find()) {
            FonaSmsMessage message = new FonaSmsMessage();
            message.id = Integer.parseInt(smsMatcher.group(1));
            internalHandler.onSmsMessageReceived(message);
            return true;
        }
        return false;
    }

}
