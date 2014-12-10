Fona / Sim800 Java Library
===
A Java library for controlling an [Adafruit
FONA](https://www.adafruit.com/product/1946) or other SIM800-based cellular
module using a serial connection.

This library gives Java-enabled devices (including BeagleBone and Raspberry Pi)
and applications light-weight access to the Internet and cellular networks.  

While most devices capable of running Java are also capable of establishing a
PPP link, this can quickly burn through the small monthly data allotment
typical of low-cost GPRS/M2M data plans, and doesn't provide access to SMS.

By using the Fona/Sim800 Library instead of a full-fledged network via PPP,
HTTP requests and SMS messages can be exchanged with a minimal amount of
overhead while providing additional control of the modem hardware (power
saving, temperature and battery monitoring, etc.) which can be desirable in
remote, battery-operated, and/or embedded applications.

Features
---
Currently Supported:
* send and receive SMS messages (synchronous and asynchronous)
* make HTTP GET requests and receive responses
* send e-mail messages via SMTP
* receive e-mail messages via POP3
* GPIO and A/D control
* network time synchronization
* battery charge status and monitoring

Currently Unimplemented:
* send and receive phone calls
* make HTTP POST requests
* FTP 
* e-mail attachments
* MMS

Unsupported by Hardware:
* make HTTPS requests
* POP3 authentication other than plaintext

Build
---
To build this library you will need the Java 7 SDK and Apache Ant. Run 'ant
jar' and optionally 'ant javadoc' to build.  This will produce ./dist/fona.jar
and ./dist/javadoc.

Alternatively, open and build the library using the Netbeans IDE.  

To run tests, first edit test/com/angryelectron/fona/FonaTest.java and add
user-specific info like serial port, phone numbers, SMTP servers, and APN
settings.  Then run 'ant test' (or run from the Netbeans IDE).

Use
---
Build, then add ./dist/fona.jar to your project.  

You will also need to add the RXTX library to your project.  You can use
./lib/RXTXcomm-2.2pre2.jar if it is not already part of your class path.

To use any of the e-mail features, you'll need Java Mail.  You can use
./lib/mail.jar if it is not already part of your class path.

Examples
---
Some simplified examples to demonstrate how to use the library.  See the
javadocs for a full list of other methods, or the test classes for more
examples.  

Send an SMS:

	Fona fona = new Fona();
	fona.open(YOUR_SERIAL_PORT, YOUR_BAUD_RATE);
	fona.smsSend(PHONE_NUMBER, "Hello from FONA!");
	fona.close();

List all unread SMS, and mark them as read:

	Fona fona = new Fona();
	fona.open(YOUR_SERIAL_PORT, YOUR_BAUD_RATE);
	List<FonaSmsMessage> messages = fona.smsRead(FonaSMSMessage.Folder.UNREAD, true);
	for (FonaSmsMessage sms : messages) {
		System.out.println(sms.sender + ": " + sms.message);
	}
	fona.close();

Send an e-mail:

	/* build message */
	FonaEmailMessage email = new FonaEmailMessage();
	email.from("fona@test.com", "Fona");
	email.to("someone@somewhere.com", "Recipient Name");
	email.cc("cc@somewhere.com", "Carbon Copy Recipient Name");
	email.bcc("someone@somewhere.com", "Blind Copy Name");
	email.subject("Test Email From Fona");
	email.body("Hello, from Fona.");

	/* login to network */	
	if (!fona.gprsIsEnabled() {
		fona.grpsEnable(YOUR_APN, YOUR_USER, YOUR_PASSWORD); //provider-specific
	}

	/* send e-mail, then (optionally) logout */
	fona.emailSMTPLogin(YOUR_SMTP, 25);
	fona.gprsDisable();

Receive SMS messages asynchronously:

	/* setup handler */
	FonaEventHandler smsHandler = new FonaEventHandler() {

		@Override
		public void onSmsMessageReceived(FonaSmsMessage sms) {
			/* called when new message arrives */
			System.out.println("New message received:");
			System.out.println(sms.sender);
			System.out.println(sms.message);
		}

		@Override
		public void onError(String message) {
			/* called when something goes wrong */
			System.out.println(message);
		}
	};

	/* connect to serial port and start listening */
	Fona fona = new Fona();
	fona.open(YOUR_SERIAL_PORT, YOUR_BAUD_RATE, smsHandler);

About
---
* Fona / Sim800 Library 
* Copyright 2014, Andrew Bythell <abythell@ieee.org>
* http://angryelectron.com/
 
This library is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This library is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
the library. If not, see <http://www.gnu.org/licenses/>.
