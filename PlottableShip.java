import java.lang.reflect.*;
import java.text.DateFormat;
import java.util.*;
import javax.xml.stream.*;
import dk.tbsalling.aismessages.ais.messages.*;
import dk.tbsalling.aismessages.ais.exceptions.*;

/**
 * @author David Barts
 * @version 0.1
 * @since 2016-01-20
 *
 * Data to track a single ship. Would be a bean were it serializable.
 */
public class PlottableShip implements Cloneable {
    /**
     * Zero-argument constructor.
     *
     * @return Constructed object.
     */
    public PlottableShip() {
        this(null);
    }

    /**
     * One-argument constructor.
     * @param mmsi Maritime mobile service identity.
     * @return Constructed object.
     */
    public PlottableShip(String mmsi) {
        setLatitude(null);
        setLongitude(null);
        setSpeedOverGround(null);
        setCourseOverGround(null);
        setCallsign(null);
        setShipName(null);
        setMMSI(mmsi);
        markUpdated();
    }

    public PlottableShip clone() {
        PlottableShip ret = new PlottableShip(getMMSI());
        ret.doImport(this);
        ret.updated = updated;
        return ret;
    }

    private Float latitude;
    public Float getLatitude() {
        return latitude;
    }
    public void setLatitude(Float value) {
        this.latitude = value;
    }

    private Float longitude;
    public Float getLongitude() {
        return longitude;
    }
    public void setLongitude(Float value) {
        this.longitude = value;
    }

    private Float speedOverGround;
    public Float getSpeedOverGround() {
        return speedOverGround;
    }
    public void setSpeedOverGround(Float value) {
        this.speedOverGround = value;
    }

    private Float courseOverGround;
    public Float getCourseOverGround() {
        return courseOverGround;
    }
    public void setCourseOverGround(Float value) {
        this.courseOverGround = value;
    }

    private String callsign;
    public String getCallsign() {
        return callsign;
    }
    public void setCallsign(String value) {
        this.callsign = value;
    }

    private String shipName;
    public String getShipName() {
        return shipName;
    }
    public void setShipName(String value) {
        this.shipName = value;
    }

    private String MMSI;
    public String getMMSI() {
        return MMSI;
    }
    public void setMMSI(String value) {
        this.MMSI = value;
    }

    private Date updated;
    public Date getUpdated() {
        return updated;
    }
    public void markUpdated() {
        updated = new Date();
    }

    private static final String[] NORMAL_FIELDS = {
        "Latitude", "Longitude", "SpeedOverGround", "CourseOverGround",
        "Callsign", "ShipName"
    };

    private void doImport(Object source) {
        Class<?> sclass = source.getClass();
        Class<?> tclass = this.getClass();

        /* normal fields just copy objects over verbatim */
        for (String field : NORMAL_FIELDS) {
            Method getter = null;
            try {
                getter = sclass.getMethod("get" + field);
            } catch (NoSuchMethodException|SecurityException exc) {
                continue;
            }
            Object sourceValue = null;
            try {
            	sourceValue = getter.invoke(source);
            } catch (IllegalAccessException|InvocationTargetException exc) { }
            /* never copy over a null object */
            if (sourceValue == null)
                continue;
            Class fclass = sourceValue.getClass();
            try {
                Method setter = tclass.getMethod("set" + field, fclass);
                setter.invoke(this, sourceValue);
            } catch (NoSuchMethodException|SecurityException|IllegalAccessException|InvocationTargetException exc) {
            	/* this shouldn't happen */
                throw new RuntimeException(exc);
            }
        }

        /* the only non-normal field is MMSI, which by definition does not
           change since we index data by it, so no need to set it here */
    }

    /**
     * Import fields from bean with matching fields.
     *
     * @param source Source object.
     */
    public void importFields(Object source) {
    	doImport(source);
    	markUpdated();
    }

    private String unk(Object value) {
        return unk(value, "");
    }

    private String unk(Object value, String suffix) {
        if (value == null)
            return "(unknown)";
        else
            return value + suffix;
    }

    /**
     * Write out a KML fragment representing this ship.
     *
     * @param writer An XMLStreamWriter.
     * @param df A DateFormat object used to format date/time values.
     */
    public void plot(XMLStreamWriter writer, DateFormat df) throws XMLStreamException {
        /* It is impossible to plot an unknown location */
        if (latitude == null || longitude == null)
            return;

        /* Start the XML block */
        writer.writeStartElement("Placemark");

        /* Name or failing that MMSI */
        writer.writeStartElement("name");
        writer.writeCharacters(shipName == null ? unk(MMSI) : shipName);
        writer.writeEndElement();

        /* Position to place the mark on a map */
        writer.writeStartElement("Point");
        writer.writeStartElement("coordinates");
        writer.writeCharacters(longitude + "," + latitude + ",0");
        writer.writeEndElement();
        writer.writeEndElement();

        /* All info other than name */
        writer.writeStartElement("description");
        writer.writeCharacters("Name: " + unk(shipName) + "<br>");
        writer.writeCharacters("MMSI: " + unk(MMSI) + "<br>");
        writer.writeCharacters("Callsign: " + unk(callsign) + "<br>");
        writer.writeCharacters("Latitide: " + latitude + "<br>");
        writer.writeCharacters("Longitude: " + longitude + "<br>");
        writer.writeCharacters("Speed: "+unk(speedOverGround," kn")+"<br>");
        writer.writeCharacters("Heading: "+unk(courseOverGround,"˚")+"<br>");
        writer.writeCharacters("Updated: "+df.format(updated)+"<br>");
        writer.writeEndElement();

        /* End the XML block */
        writer.writeEndElement();
    }

    /**
     * Format a long MMSI as a string.
     *
     * @param value A long value.
     * @return A nine-digit string, with leading zeroes if needed.
     */
    public static String formatMMSI(Long value) {
        return (new Formatter()).format("%09d", value).toString();
    }
}