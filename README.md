Fona / Sim800 Library
===
A Java library for controlling an [Adafruit
FONA](https://www.adafruit.com/product/1946) or other SIM800-based cellular
module using a serial connection.

This project gives Java-enabled devices (including BeagleBone
and RaspberryPi) light-weight access to the Internet.  While most devices capable
of running Java are also capable of establishing a PPP link, this can quickly burn 
through the small monthly data allotment typical of low-cost GPRS/M2M data plans.

By using the Fona/Sim800 Library instead of PPP, HTTP requests and SMS messages
can be exchanged with a minimal amount of overhead while providing additional 
control of the modem hardware (power saving, temperature and battery monitoring,
 etc.) which can be desirable in remote, battery-operated, and/or embedded 
applications.

Features
---
Currently Supported:
* send and receive SMS messages
* make HTTP requests and receive responses
* send e-mail messages
* GPIO and A/D control
* network time synchronization
* battery charge status and monitoring

Future development:
* send and receive phone calls
* receive e-mail messages

Build
---
This is a Netbeans / Ant project.  Build in the Netbeans IDE, or on the command
line by running 'ant', which will build javadocs and jars in ./dist.

Use
---
Add fona.jar and RXTX (see ./lib) to your project.  Then:

	Fona fona = new Fona();
	fona.open(SERIAL_PORT, BAUD_RATE);
	fona.smsSend(PHONE_NUMBER, "Hello from FONA!");
	fona.close();

See javadocs for a list of other methods.

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
