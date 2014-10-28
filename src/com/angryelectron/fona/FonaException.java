/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

/**
 * Exception thrown by Fona methods.
 */
public class FonaException extends Exception {

    /**
     * Stop compiler complaining about serializable classes.
     */
    private static final long serialVersionUID = 1;

    /**
     * Constructor.
     * @param message Informative message about the exception.
     */
    public FonaException(String message) {
        super(message);
    }
}
