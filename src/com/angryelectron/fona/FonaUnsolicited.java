/**
 * Fona / Sim800 Library for Java Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handle unsolicited / asynchronous SIM800 responses.
 */
 class FonaUnsolicited {

    /**
     * This is a full list of 'unsolicited' responses according to section 9.1
     * of the documentation. However - some of these responses *are* solicited:
     * they are only returned in response to an AT command which we issue.
     * Any response of this type is handled using a standard read(), so it must
     * be removed from this list.
     */
    private final List<String> unsolicitedResponsePatterns = Arrays.asList(
            "+CME ERROR:", //see 19.1 for a complete list of codes
            "+CMS ERROR:", //see 192.3 for a complete list of codes
            "+CCWA:", //call is waiting
            "+CLIP:", //calling line identify (??)
            "+CRING:", //incoming call
            "+CREG:", //change in network registration
            "+CCWV:", //accumulated call meter about to reach max
            "+CMTI:", //new SMS message has arrived
            "+CMT:", //new SMS message has arrived
            "+CBM:", //new cell broadcast message
            "+CDS:", //new SMS status report received
            "+COLP:", //related to call presentation (?)
            "+CSSU:", //related to call presentation
            "+CSSI:", //related to call presentation
            "+CLCC:", //report list of current calls automatically (?)
            "*PSNWID:", //refresh network name by network
            "*PSUTTZ:", //refresh time and timezone by network
            "+CTZV:", //refresh network timezone by network
            "DST:", //refresh daylight savings time by network
            "+CSMINS:", //sim card inserted or removed
            "+CDRIND:", //voice call, data has been terminated (?)
            "+CHF:", //current channel
            "+CENG:", //report network information
            "MO RING",
            "MO CONNECTED",
            "+CPIN:", //sim card ready, not ready, or requires pin
            "+CSQN:", //signal quality report
            "+SIMTONE:", //tone started or stopped playing
            "+STTONE:", //tone started or stopped playing
            "+CR:", //intermediate result code
            "+CUSD:", //ussd response
            "RING", //incoming call
            "NORMAL POWER DOWN", //sim is powering down
            //"+CMTE:", //temperature.  Temperature detection not enabled.
            "UNDER-VOLTAGE", //alarm
            "OVER-VOLTAGE", //alarm
            "CHARGE-ONLY MODE", //charging via external charger
            "RDY", //module is ready
            "+CFUN:", //phonebok initialization is complete
            "CONNECT", //tcp/udp connection info
            "SEND OK", //data sending successful
            "CLOSED", //tcp/udp connection is closed
            "RECV FROM", //remote IP address and port
            "+IPD", //protocol data
            "+RECEIVE",
            "REMOTE IP:",
            "+CDNSGIP", //dns successful or failed
            "+PDP DEACT", //gprs disconnected by network
            //"+SAPBR", //bearer
            //"+HTTPACTION:", //response to HTTP request
            "+FTPGET:",
            "+FTPPUT:",
            "+FTPDELE:",
            "+FTPSIZE:",
            "+FTPMKD:",
            "+FTPRMD:",
            "+FTPLIST:"
    );

    /**
     * A pre-compiled pattern used for matching.
     */
    private final Pattern unsolicitedPattern;

    /**
     * Constructor. 
     */
    FonaUnsolicited() {
        this.unsolicitedPattern = buildUnsolicitedPattern();
    }

    /**
     * Assemble all unsolicited codes into a huge regex pattern. This seems
     * complicated but is necessary for fast comparisons in the serial event
     * handler. The regex looks like this:
     *
     * ^( (first)|(second)|...).*
     *
     * @return Pattern of unsolicited responses.
     */
    private Pattern buildUnsolicitedPattern() {
        StringBuilder builder = new StringBuilder("^(");
        for (String s : unsolicitedResponsePatterns) {
            builder.append("(");
            builder.append(s);
            builder.append(")|");
        }
        String regex = builder.toString();
        regex = regex.substring(0, regex.length() - 1); //trim last '|'
        regex = regex.replaceAll("\\+", "\\\\+"); //escape literal +
        regex = regex.replaceAll("\\*", "\\\\*"); //escape literal *
        regex = regex + ").*"; //match the rest of the line
        return Pattern.compile(regex);
    }

    /**
     * Check if response contains an unsolicited response.
     * @param line String to be checked.
     * @return True if line matches unsolicited pattern.
     */
    boolean isUnsolicited(String line) {
        Matcher matcher = unsolicitedPattern.matcher(line);
        return matcher.matches();
    }

}
