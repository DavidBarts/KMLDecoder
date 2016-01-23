ABSTRACT

This is a simple AIS plotter which decodes NMEA messages from an AIS
receiver and displays ship positions on a map.

It makes use of Thomas Salling's aismessages library and any mapping
program which supports the KML NetworkLink feature to display the
position of ships received by an AIS receiver. Received NMEA messages
are read from standard input.

COPYRIGHT

Copyright © 2016, David W. Barts. All rights reserved. This code is free
for noncommercial use; contact me at <david.w.barts@gmail.com> for
commercial licensing. (Note that the aismessages package which this
software uses has a similar copyright held by another individual.)

PREREQUISITES

In addition to Java 1.7 or better and the standard Java class libraries,
this code requires Oracle Berkeley DB Java Edition
(http://www.oracle.com/technetwork/database/berkeleydb/) and Thomas
Salling's aismessages (https://github.com/tbsalling/aismessages), you'll
need some sort of map viewer which can support the NetworkLink feature
of KML with automatic refreshing.

BUILDING

Basically, obtain the prerequisites, then compile all the .java files in
this directory, ensuring the prerequisites are in the classpath. (I've
not taken the trouble to organize them into a namespace or make a build
file yet, but as it's a fairly simple program with not that many source
files, it doesn't matter much.)

MAKING A DATABASE DIRECTORY

BDB Java Edition wants to have a directory named db_env in which to
store its files. This needs to be in the working directory of where you
plan to run this program. All you need to do is make an empty directory
by that name.

RUNNING

First, ensure the classpath is set, then run the class KMLDecoder. E.g.:

	export CLASSPATH=.:je-6.4.9.jar:aismessages-2.1.0-jdk7-backport-jar-with-dependencies.jar
	java KMLDecoder
	
This will start up an HTTP service listening on port 8080 of the
loopback interface. If you want to use another port, pass the port
number as an argument, e.g.:

	java KMLDecoder 8000

Now you need to make Google Maps, Marble, or some other KML-capable map
viewer regularly poll that service and display the results. That's where
display.kml comes in. It's currently set to poll a server listening on
port 8080 of the loopback interface once every 10 seconds.

Each placemark will be labeled with the ship's name or failing that it's
MMSI, and will have a description giving further details about the ship.
In Google Maps, one can click on the pushpin icon of the placemarks and
this information will display in a small pop-up window.

THE WEB SERVICE

The web service provided is extremely simple. It ignores any request
data and sends back an HTTP/1.0 200 response containing the KML
necessary to create labeled placemarks for known, currently-reporting
ships. As a security measure to prevent unauthorized use, it binds
itself to the loopback address, so its service can only be accessed on
the local computer.
