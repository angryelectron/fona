/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Run in a thread, poll the unsolicited response queue (which is populated
 * by FonaSerial) and fire the internal FonaEventHandler to parse the
 * responses.
 */
 class FonaUnsolicitedListener implements Runnable {
     
    /**
     * The queue to monitor.  This must be the same queue that is populated
     * by FonaSerial.serialEvent().
     */
    private final LinkedBlockingQueue<String> queue;
    
    /**
     * Internal event handler.  This implementation needs to parse the raw
     * responses and then fire the external event handler so the application
     * can react.
     */
    private FonaEventHandler internalHandler;

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
    void listen(FonaEventHandler handler) {
        this.internalHandler = handler;
        Thread thread = new Thread(this);
        thread.start();
    }
    
    /**
     * Monitor and parse the queue, firing the appropriate internal 
     * events.
     */
    @Override
    public void run() {
        try {
            String response = queue.take();
            if (response.startsWith("+CMTI: \"SM\"")) {
                /* sms message */
                FonaSmsMessage sms = new FonaSmsMessage();
                sms.response = response;
               internalHandler.onSmsMessageReceived(sms);
            } else {
                internalHandler.onError("Unknown unsolicited response: " + response);
            }
        } catch (InterruptedException ex) {
            internalHandler.onError(ex.getMessage());
        }
    }
    
}
