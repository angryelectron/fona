/**
 * Fona Java Library for SIM800. Copyright 2014 Andrew Bythell
 * <abythell@ieee.org>
 */
package com.angryelectron.fona;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Fona POP3 internal methods.
 */
class FonaPOP3 {

    private final FonaSerial serial;
    private final Integer POP3TIMEOUT = 30000;

    /**
     * Constructor.
     *
     * @param serial
     */
    FonaPOP3(FonaSerial serial) {
        this.serial = serial;
    }

    /**
     * Login to POP3 Server.
     * {@link com.angryelectron.fona.Fona#emailPOP(java.lang.String, java.lang.Integer, java.lang.String, java.lang.String)}.
     *
     * @throws FonaException
     */
    void login(String server, Integer port, String user, String password) throws FonaException {
        serial.atCommandOK("AT+EMAILCID=1");
        serial.atCommandOK("AT+EMAILTO=30");
        serial.atCommandOK("AT+POP3SRV=\"" + server + "\",\"" + user + "\",\"" + password + "\"," + port);
        serial.atCommandOK("AT+POP3IN");
        String response = serial.expect("+POP3IN:", POP3TIMEOUT);
        String fields[] = response.split(" ");
        Integer code = Integer.parseInt(fields[1]);
        if (code != 1) {
            throw new FonaException("POP3: " + getPOPErrorMessage(code));
        }
    }

    void logout() throws FonaException {
        serial.atCommandOK("AT+POP3OUT");
        String response = serial.expect("+POP3OUT", POP3TIMEOUT);
        String fields[] = response.split(" ");
        Integer code = Integer.parseInt(fields[1]);
        if (code != 1) {
            throw new FonaException("POP3: " + getPOPErrorMessage(code));
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
        String response = serial.expect("+POP3", POP3TIMEOUT);
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

    /**
     * Get message from POP server.
     *
     * @param messageId
     * @throws FonaException
     */
    private void loadMessage(int messageId) throws FonaException {
        serial.atCommandOK("AT+POP3CMD=4," + messageId);
        String response = serial.expect("+POP3", POP3TIMEOUT);
        if (response.startsWith("+POP3OUT")) {
            String fields[] = response.split(" ");
            Integer code = Integer.parseInt(fields[1]);
            throw new FonaException(getPOPErrorMessage(code));
        } else if (response.startsWith("+POP3CMD: 0")) {
            throw new FonaException("Unexpected response: " + response);
        }
    }

    /**
     * Read email message.
     *
     * @param messageId
     * @return
     * @throws FonaException
     */
    FonaEmailMessage readMessage(int messageId, boolean markAsRead) throws FonaException {
        loadMessage(messageId);
        StringBuilder builder = new StringBuilder();
        String response = "";
        do {
            response = serial.atCommand("AT+POP3READ=1460").trim();
            if (response.startsWith("+POP3OUT")) {
                String fields[] = response.split(" ");
                Integer code = Integer.parseInt(fields[1]);
                throw new FonaException(getPOPErrorMessage(code));
            }

            /* trim the +POP3 response and the OK last line */
            int start = response.indexOf("\n");
            int end = response.lastIndexOf("\n");
            builder.append(response.substring(start, end));

        } while (!response.startsWith("+POP3READ: 2") && (!response.startsWith("ERROR")));
        FonaEmailMessage email = parseMessage(builder.toString());
        email.messageId = messageId;        
        return email;
    }

    /**
     * Informative error message codes.
     *
     * @param code
     * @return
     */
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

    /**
     * Delete message from POP3 server
     * @param messageId The pop3 server message ID to delete.
     * @throws FonaException 
     */
    void delete(int messageId) throws FonaException {
        serial.atCommandOK("AT+POP3DEL=" + messageId);
        String response = serial.expect("+POP3", POP3TIMEOUT).trim();
        String fields[] = response.split(" ");
        Integer code = Integer.parseInt(fields[1]);
        switch(code) {
            case 0:
                throw new FonaException("POP3 Server issued a negative response: " + response);
            case 1:
                /* "Server issues a positive response */
                break;
            default:
                throw new FonaException(getPOPErrorMessage(code));
        }
    }

    /**
     * Take response from POP3READ and parseMessage it into a FonaEmailMessage using
 Javamail.
     *
     * @param response
     * @return
     */
    FonaEmailMessage parseMessage(String response) throws FonaException {
        response = response.trim();  // leading newlines won't parseMessage        
        FonaEmailMessage fonaMessage = new FonaEmailMessage();
        InputStream is = new ByteArrayInputStream(response.trim().getBytes());
        Session session = Session.getDefaultInstance(new Properties());
        try {
            MimeMessage message = new MimeMessage(session, is);
            
            /**
             * Parse header.
             */
            fonaMessage.subject = message.getSubject();                        
            
            InternetAddress from[] = (InternetAddress[]) message.getFrom();
            if (from != null) {
                for (InternetAddress a : from) {
                    fonaMessage.from(a.getAddress(), a.getPersonal());
                }
            }
            InternetAddress to[] = (InternetAddress[]) message.getRecipients(Message.RecipientType.TO);
            if (to != null) {
                for (InternetAddress a : to) {
                    fonaMessage.to(a.getAddress(), a.getPersonal());
                }
            }
            InternetAddress cc[] = (InternetAddress[]) message.getRecipients(Message.RecipientType.CC);
            if (cc != null) {
                for (InternetAddress a : cc) {
                    fonaMessage.cc(a.getAddress(), a.getPersonal());
                }
            }
            InternetAddress bcc[] = (InternetAddress[]) message.getRecipients(Message.RecipientType.BCC);
            if (bcc != null) {
                for (InternetAddress a : bcc) {
                    fonaMessage.bcc(a.getAddress(), a.getPersonal());
                }
            }
            
            /**
             * Parse body.
             */
            if (message.isMimeType("text/plain")) {
                fonaMessage.body = (String) message.getContent();
            } else {
                fonaMessage.body = "Message contained a mime type not currently supported "
                        + "by the FONA Java library.";
            }            
            
        } catch (MessagingException | IOException ex) {
            throw new FonaException(ex.getMessage());
        }
        return fonaMessage;
    }
}
