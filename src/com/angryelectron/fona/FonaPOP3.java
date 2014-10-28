/**
 * Fona Java Library for SIM800. Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

/**
 * Fona POP3 internal methods.
 */
class FonaPOP3 {

    private final FonaSerial serial;

    FonaPOP3(FonaSerial serial) {
        this.serial = serial;
    }

    /**
     * Login to POP3 Server.
     *
     * @throws FonaException
     */
    void login() throws FonaException {
        serial.atCommandOK("AT+POP3IN");
        String response = serial.expect("+POP3IN:", 5000);
        String fields[] = response.split(" ");
        Integer code = Integer.parseInt(fields[1]);
        if (code != 1) {
            throw new FonaException(getPOPErrorMessage(code));
        }
    }

    /**
     * Get new message count.
     *
     * @return Number of new messages waiting on POP3 server.
     * @throws FonaException
     */
    Integer getNewMessageCount() throws FonaException {
        serial.atCommandOK("AT+POP3NUM");
        String response = serial.expect("+POP3:", 5000);
        if (response.equals("+POP3NUM: 0")) {
            /* server contains no new messages */
            return 0;
        } else if (response.startsWith("+POP3OUT")) {
            /* error */
            String fields[] = response.split(" ");
            Integer code = Integer.parseInt(fields[1]);
            throw new FonaException(getPOPErrorMessage(code));
        }
        String fields[] = response.split(",");
        return Integer.parseInt(fields[1]);
    }

    private Integer getMessageSize(int messageId) throws FonaException {
        serial.atCommandOK("AT+POP3LIST=" + messageId);
        String response = serial.expect("+POP3", 5000);
        if (response.startsWith("+POP3OUT")) {
            /* error */
            String fields[] = response.split(" ");
            Integer code = Integer.parseInt(fields[1]);
            throw new FonaException(getPOPErrorMessage(code));
        }
        String fields[] = response.split(",");
        return Integer.parseInt(fields[2]);
    }

    private void loadMessage(int messageId) throws FonaException {
        serial.atCommandOK("AT+POP3CMD=4," + messageId);
        String response = serial.expect("+POP3", 5000);
        if (response.startsWith("+POP3OUT")) {
            String fields[] = response.split(" ");
            Integer code = Integer.parseInt(fields[1]);
            throw new FonaException(getPOPErrorMessage(code));
        } else if (response.startsWith("+POP3CMD: 0")) {
            throw new FonaException("Unexpected response: " + response);
        }
    }

    FonaEmailMessage readMessage(int messageId) throws FonaException {
        loadMessage(messageId);
        StringBuilder builder = new StringBuilder();
        String response = "";
        do {
            response = serial.atCommand("AT+POP3READ," + messageId + ",1460");

            /* check for errors */
            if (response.startsWith("+POP3OUT")) {
                String fields[] = response.split(" ");
                Integer code = Integer.parseInt(fields[1]);
                throw new FonaException(getPOPErrorMessage(code));
            }

            /* trim the +POP3 response and the OK last line */
            int start = response.indexOf("\n");
            int end = response.lastIndexOf("\n");
            builder.append(response.substring(start, end));

        } while (!response.startsWith("+POP3READ: 2"));
        FonaEmailMessage email = new FonaEmailMessage();
        email.messageId = messageId;
        //TODO: parse the response into subject, sender, etc.  for now 
        //put it all in the body.
        email.body = builder.toString();
        return email;
    }

    private String getPOPErrorMessage(Integer code) {
        switch (code) {
            case 61:
                return "Network error.";
            case 62:
                return "DNS resolve error.";
            case 63:
                return "POP3 tcp connection error.";
            case 64:
                return "Timeout of POP3 server response";
            case 65:
                return "POP3 server response error.";
            case 66:
                return "POP3 server rejects login.";
            case 67:
                return "Incorrect user name.";
            case 68:
                return "Incorrect user name or password.";
            default:
                return "Unknown error code " + code;
        }
    }

}
