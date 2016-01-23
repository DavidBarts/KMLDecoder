import java.util.*;
import dk.tbsalling.aismessages.ais.messages.*;

/**
 * @author David Barts
 * @version 0.1
 * @since 2016-01-20
 *
 * Track multiple plottable ships. Thread-safe, and manages the purging
 * of stale ship data.
 *
 * The synchronization is crude (we could easily do better and allow
 * concurrent reads) because the number of threads in our service is very
 * limited (typically only one reader).
 */
public class PlottableShips {
    /* purge a ship after no reports for this many milliseconds */
    private static final long MAXLIFE = 30 * 60 * 1000;

    /* don't run purges more often than this */
    private static final long MINPURGE = 30 * 1000;

    private long lastPurged;
    private HashMap<String, PlottableShip> shipMap;

    /**
     * Zero-argument constructor.
     *
     * @return Constructed object.
     */
    public PlottableShips() {
        lastPurged = (new Date()).getTime();
        shipMap = new HashMap<String, PlottableShip>();
    }

    /**
     * Purge old ships.
     */
    public synchronized void purgeOld() {
        /* don't do anything if we recently purged */
        long now = (new Date()).getTime();
        if (now - lastPurged < MINPURGE)
            return;
        lastPurged = now;

        /* first we build the list of things to zap */
        ArrayList<String> toPurge = new ArrayList<String>();
        for (Map.Entry<String, PlottableShip> entry: shipMap.entrySet()) {
            long then = entry.getValue().getUpdated().getTime();
            if (now - then > MAXLIFE)
                toPurge.add(entry.getKey());
        }

        /* then we zap them */
        for (String mmsi: toPurge)
            shipMap.remove(mmsi);
    }

    /**
     * Get current ships as a collection. The returned collection
     * is non-volatile.
     *
     * @return A Collection<PlottableShip>.
     */
    public synchronized Collection<PlottableShip> getCurrent() {
        purgeOld();
        ArrayList<PlottableShip> ret = new ArrayList<PlottableShip>();
        for (PlottableShip ship: shipMap.values())
            ret.add(ship.clone());
        return ret;
    }

    /**
     * Refresh or add a ship.
     *
     * @param mmsi MMSI from the below message.
     * @param message Any received AISMessage.
     * @return true if the ship was added
     */
    public synchronized boolean addOrUpdate(String mmsi, AISMessage message) {
        boolean ret = false;
        PlottableShip ship;
        if ((ship = shipMap.get(mmsi)) == null) {
            ship = new PlottableShip(mmsi);
            shipMap.put(mmsi, ship);
            ret = true;
        }
        ship.importFields(message);
        return ret;
    }

    private boolean shouldRemember(String _old, String _new) {
        return _new != null && _old == null;
    }

    /**
     * If the MMSI is associated with a ship that does not have a name or
     * callsign in our database, set them from the passed arguments.
     *
     * @param mmsi MMSI of the ship to update (must exist here)
     * @param name Name of the ship (if null, do not update)
     * @param call Callsign of the ship (if null, do not update)
     */
    public synchronized void rememberNameCall(String mmsi, String name, String call) {
        PlottableShip ship = shipMap.get(mmsi);
        if (shouldRemember(ship.getShipName(), name))
            ship.setShipName(name);
        if (shouldRemember(ship.getCallsign(), call))
            ship.setCallsign(call);
    }
}
