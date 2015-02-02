/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Fona SMS message object.
 */
public class FonaSmsMessage {

    /**
     * Sender's phone number.
     */
    public String sender;
    
    /**
     * Body of message.
     */
    public String message;
    
    /**
     * Timestamp.
     */
    public Date timestamp;
    
    /**
     * Message Id.
     */
    public Integer id;
    
    /**
     * Message folder / status.
     */
    public Folder folder;
        
    /**
     * Fona SMS message folders used with 
     * {@link Fona#smsRead(com.angryelectron.fona.FonaSmsMessage.Folder, boolean)}.
     */
    public enum Folder {

        /**
         * Received unread messages.
         */
        UNREAD("REC UNREAD"),
        
        /**
         * Received read messages.
         */
        READ("REC READ"),
        
        /**
         * Stored unsent messages.
         */
        UNSENT("STO UNSENT"),
        
        /**
         * Stored sent messages.
         */
        SENT("STO SENT"),
        
        /**
         * All messages.
         */
        ALL("ALL");

        private final String s;
        private static final Map<String, Folder> lookup = new HashMap<>();

        static {
            for (Folder f : Folder.values()) {
                lookup.put(f.stat(), f);
            }
        }

        Folder(String s) {
            this.s = s;
        }

        public String stat() {
            return s;
        }

        public static Folder get(String s) {
            return lookup.get(s);
        }
    };
}
