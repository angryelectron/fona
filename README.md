Fona / Sim800 Library
===
A Java library for controlling an [Adafruit
FONA](https://www.adafruit.com/product/1946) or other SIM800-based cellular
module using a serial connection.

Currently under active development.  Testing is recommended - update FonaTest
class with your cellular provider's info.  If you are interested in contributing
please get in touch or see the [Github
Project](http://github.com/angryelectron/fona).

Build
---
This is a Netbeans / Ant project.  Build in the Netbeans IDE, or on the command
line by running 'ant', which will build javadocs and jars in ./dist.

Usage
---
Add fona.jar and RXTX (see ./lib) to your project.  Then:

	Fona fona = new Fona();
	fona.open(SERIAL_PORT, BAUD_RATE);
	fona.check();
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
