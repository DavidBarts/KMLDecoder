import java.io.*;
import java.text.*;
import java.util.*;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import dk.tbsalling.aismessages.nmea.messages.NMEAMessage;
import dk.tbsalling.aismessages.ais.messages.*;
import dk.tbsalling.aismessages.ais.exceptions.*;
import dk.tbsalling.aismessages.nmea.exceptions.*;

/**
 *
 * @author David Barts
 * @version 0.1
 * @since 2016-01-22
 *
 * Decodes raw AIS messages to both an in-memory database (most items) and
 * persistent key/value stores (MMSI to name and callsign mappings). The
 * positions of the decoded ships are available as KML via an HTTP server
 * bound to the loopback address. This enables ship positions to be plotted
 * via Google Earth, Marble, or a similar program.
 */
public class KMLDecoder {

    private static final String MYNAME = "KMLDecoder";
    private static final String INDENT = "      ";
    private static HashMap<String,ArrayList<NMEAMessage>> msgbuf;
    private static String now;
    private static String line;
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {

        /* Initialize things */
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));
        Environment env = SimpleDBHash.getEnvironment("db_env");
        SimpleDBHash calls = new SimpleDBHash(env, "calls");
        SimpleDBHash names = new SimpleDBHash(env, "names");
        msgbuf = new HashMap<String,ArrayList<NMEAMessage>>();
        msgbuf.put("A", new ArrayList<NMEAMessage>());
        msgbuf.put("B", new ArrayList<NMEAMessage>());
        PlottableShips ships = new PlottableShips();

        /* Spawn a thread to listen for and deal with HTTP requests */
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.format("%s: invalid number \"%s\"%n",
                    MYNAME, args[0]);
                System.exit(2);
            }
        }
        RequestListener listener = new RequestListener(port, ships);
        listener.start();

        /* The main thread reads standard input and updates data */
        while ((line = stdin.readLine()) != null) {
            /* Silently discard obviously bad messages */
            if (!line.startsWith("!")) {
                continue;
            }

            /* Get the time of this message */
            now = iso8601.format(new Date());

            /* Do preliminary parsing, bail on failure */
            NMEAMessage nmsg = null;
            try {
                nmsg = NMEAMessage.fromString(line);
            } catch (NMEAParseException exc) {
                errmsg("Unable to parse");
                continue;
            } catch (InvalidMessage exc) {
                errmsg("Invalid message");
                continue;
            } catch (dk.tbsalling.aismessages.nmea.exceptions.UnsupportedMessageType exc) {
                errmsg("Unsupported message");
                continue;
            }

            /* Determine channel */
            String chan = nmsg.getRadioChannelCode();
            if (!msgbuf.containsKey(chan)) {
                errmsg("Invalid channel code " + chan);
                continue;
            }

            /* Deal with any fragmentation */
            int nfrag = nmsg.getNumberOfFragments();
            AISMessage amsg = null;
            try {
                if (nfrag < 0) {
                    errmsg("Invalid fragment count " + Integer.toString(nfrag));
                } else if (nfrag == 1) {
                    amsg = AISMessage.create(nmsg);
                    clearbuf(chan);
                } else {
                    int fragno = nmsg.getFragmentNumber();
                    if (fragno < 0 || fragno > nfrag) {
                        errmsg("Invalid fragment number " + Integer.toString(fragno));
                        clearbuf(chan);
                        continue;
                    }
                    ArrayList<NMEAMessage> thisbuf = msgbuf.get(chan);
                    int expected = thisbuf.size() + 1;
                    if (fragno != expected) {
                        errmsg("Expecting fragment " + Integer.toString(expected)
                            + ", got " + Integer.toString(fragno) + "!");
                        clearbuf(chan);
                        continue;
                    }
                    thisbuf.add(nmsg);
                    if (nfrag == fragno) {
                        amsg = AISMessage.create(thisbuf.toArray(new NMEAMessage[thisbuf.size()]));
                        clearbuf(chan);
                    }
                }
            } catch (InvalidMessage|InvalidAISMessage exc) {
                errmsg("Invalid message");
                continue;
            } catch (dk.tbsalling.aismessages.ais.exceptions.UnsupportedMessageType exc) {
                errmsg("Unsupported message");
                continue;
            }

            /* If there was an error or incomplete message, we have nothing
               to print. */
            if (amsg == null)
                continue;

            /* Every message starts with a time stamp, message type, and MMSI */
            String msgtype = amsg.getClass().getSimpleName();
            String mmsi = PlottableShip.formatMMSI(amsg.getSourceMmsi().getMMSI());
            System.out.format("%s %s %s%n", now, msgtype, mmsi);

            /* Most of the ship updating happens here. Note that we also can
               do purging in the RequestServer threads. */
            if (ships.addOrUpdate(mmsi, amsg))
                ships.rememberNameCall(mmsi, names.get(mmsi), calls.get(mmsi));
            ships.purgeOld();

            /* Messages that map MMSI to a ship name and/or callsign get
               logged and tracked in persistent key/value stores */
            if (amsg instanceof StaticDataReport) {
                String shipName = ((StaticDataReport) amsg).getShipName();
                String callsign = ((StaticDataReport) amsg).getCallsign();
                boolean updated = false;
                if (shipName != null) {
                    names.put(mmsi, shipName);
                    System.out.format("%sName = %s", INDENT, shipName);
                    updated = true;
                }
                if (callsign != null) {
                    calls.put(mmsi, callsign);
                    System.out.format("%sCallsign = %s",
                        updated ? ", " : INDENT, callsign);
                    updated = true;
                }
                System.out.println();
            }

            /* Messages that report positions get logged */
            else if (amsg instanceof DynamicDataReport) {
                String shipName = names.get(mmsi);
                System.out.print(INDENT);
                if (shipName != null)
                    System.out.format("%s ", shipName);
                System.out.format("@ %f, %f%n",
                    ((DynamicDataReport) amsg).getLatitude(),
                    ((DynamicDataReport) amsg).getLongitude());
            }
        }

        calls.close();
        names.close();
        env.close();
    }

    private static void errmsg(String msg) {
        System.out.format("%s *Error* %s%n", now, msg);
        if (!line.equals(""))
            System.out.format("%s%s%n", INDENT, line);
    }

    private static void clearbuf(String chan) {
        msgbuf.get(chan).clear();
    }
}
