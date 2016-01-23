import java.net.*;
import java.nio.charset.*;
import java.io.*;
import java.text.*;
import java.util.*;
import javax.xml.stream.*;

/**
 * @author David Barts
 * @version 0.1
 * @since 2016-01-22
 *
 * Serves a single HTTP request. This is super-simple; we ignore all request
 * data and always serve the same XML.
 */
public class RequestServer extends SaneThread {
    private static final String BODY_CODING = "UTF-8";

    private InputStream sock_in;
    private OutputStream sock_out;
    private PlottableShips ships;

    /**
     * Constructor.
     *
     * @return Constructed object.
     * @param sock Socket object of accepted connection to serve.
     * @param ships Ships to plot.
     */
    public RequestServer(Socket sock, PlottableShips ships) throws IOException {
        setDaemon(true);
        sock_in = sock.getInputStream();
        sock_out = sock.getOutputStream();
        this.ships = ships;
    }

    /**
     * Run the thread.
     */
    void runn() throws Exception {
        SimpleDateFormat iso8601 = new SimpleDateFormat("HH:mm:ss'Z'");
        iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));

        /* just throw away any request data */
        BufferedReader reader = new BufferedReader(
        	new InputStreamReader(sock_in, StandardCharsets.ISO_8859_1));
        String line;
        while ((line = reader.readLine()) != null)
        	if (line.isEmpty()) break;

        /* write response headers */
        OutputStreamWriter hwriter = new OutputStreamWriter(sock_out,
        	StandardCharsets.US_ASCII);
        hwriter.write("HTTP/1.0 200 OK\r\n");
        hwriter.write("Content-Type: text/xml; charset=\""+BODY_CODING+"\"\r\n");
        hwriter.write("\r\n");
        hwriter.flush();

        /* prepare to write some XML */
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter bwriter = factory.createXMLStreamWriter(sock_out, BODY_CODING);

        /* write the response body */
        bwriter.writeStartDocument(BODY_CODING, "1.0");
        bwriter.writeStartElement("kml");
        bwriter.writeAttribute("xmlns", "http://www.opengis.net/kml/2.2");
        bwriter.writeStartElement("Document");
        for(PlottableShip ship: ships.getCurrent())
            ship.plot(bwriter, iso8601);
        bwriter.writeEndDocument();
        bwriter.flush();

        /* finish up and exit */
        sock_out.close();
    }
}
