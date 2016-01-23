import java.net.*;
import java.io.*;

/**
 * @author David Barts
 * @version 0.1
 * @since 2016-01-22
 *
 * Listens for incoming HTTP requests on the loopback interface and spawns
 * threads to deal with them.
 */
public class RequestListener extends SaneThread {
    private int port;
    private PlottableShips ships;

    /**
     * Constructor.
     *
     * @return Constructed object.
     * @param port Port to listen on.
     * @param ships Ships to plot.
     */
    public RequestListener(int port, PlottableShips ships) {
        setDaemon(true);
        this.port = port;
        this.ships = ships;
    }

    void runn() throws Exception {
        ServerSocket sock = new ServerSocket(port, 5,
            InetAddress.getLoopbackAddress());

        while (true) {
            Socket conn = sock.accept();
            RequestServer server = new RequestServer(conn, ships);
            server.start();
        }
    }
}
