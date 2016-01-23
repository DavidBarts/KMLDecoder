import java.io.*;
import com.sleepycat.je.*;

/**
 * @author David Barts
 * @version 0.2
 * @since 2016-01-09
 *
 * A simple, persistent string:string hash using BDB Java Edition.
 *
 * This is a simple hash that implements get, containsKey, and put
 * (and nothing else), because that's all I need and many of the other
 * methods the Map interface requires are painful, inefficient, or both
 * when one is using a disk file.
 */
public class SimpleDBHash {
    private static final String CODING = "UTF-8";

    private Environment env;
    private Database db;
    private boolean onlyOne;

    /**
     * Constructor.
     *
     * @param epath Path to environment directory.
     * @param dbname Database name.
     * @return Constructed object.
     */
    public SimpleDBHash(String epath, String dbname) throws DatabaseException {
        env = null;
        try {
            init(getEnvironment(epath), dbname);
        } catch (DatabaseException exc) {
            if (env != null) env.close();
            throw exc;
        }
        onlyOne = true;
    }

    /**
     * Constructor.
     *
     * @param env An Environment object.
     * @param dbname Database name.
     * @return Constructed object.
     */
    public SimpleDBHash(Environment env, String dbname) throws DatabaseException {
    	init(env, dbname);
        onlyOne = false;
    }

    private void init(Environment env, String dbname) throws DatabaseException {
        this.env = env;
        // xxx - BDB is broken unless we enable deferred write
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.setAllowCreate(true).setDeferredWrite(true);
        db = env.openDatabase(null, dbname, dbc);
        env.sync();
    }

    /**
     * Obtain a suitable Environment.
     *
     * @param epath Path on filesystem.
     * @return Environment object.
     */
    public static Environment getEnvironment(String epath) {
        EnvironmentConfig envc = new EnvironmentConfig();
        envc.setAllowCreate(true);
        return new Environment(new File(epath), envc);
    }

    private DatabaseEntry dbEntry(String value) {
        try {
            return new DatabaseEntry(value.getBytes(CODING));
        } catch (UnsupportedEncodingException exc) {
            throw new RuntimeException(exc);
        }
    }

    private String dbString(DatabaseEntry value) {
        try {
            return new String(value.getData(), CODING);
        } catch (UnsupportedEncodingException exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * See if a key exists already.
     *
     * @param key The key.
     * @return A boolean value.
     */
    public boolean containsKey(String key) throws DatabaseException {
        DatabaseEntry junk = new DatabaseEntry();
        return db.get(null, dbEntry(key), junk, null) == OperationStatus.SUCCESS;
    }

    /**
     * Get a value associated with a key.
     *
     * @param key The key.
     * @return Value found, or null if key not found.
     */
    public String get(String key) throws DatabaseException {
        DatabaseEntry ret = new DatabaseEntry();
        if (db.get(null, dbEntry(key), ret, null) == OperationStatus.SUCCESS)
            return dbString(ret);
        else
            return null;
    }

    /**
     * Put something into the database.
     *
     * @param key The key.
     * @param value The value.
     */
    public void put(String key, String value) throws DatabaseException {
        DatabaseEntry existing = new DatabaseEntry();
        DatabaseEntry dbkey = dbEntry(key);

        // It is expensive to write a record, so avoid pointless writes.
        if (db.get(null, dbkey, existing, null) == OperationStatus.SUCCESS) {
            if (value.equals(dbString(existing)))
                return;
        }

        // OK, not pointless, write it.
        db.put(null, dbkey, dbEntry(value));
        // xxx - only deferred-write mode works, so must do this
        db.sync();
    }

    /**
     * Close this database hash.
     */
    public void close() throws DatabaseException {
        db.close();
        if (onlyOne)
            env.close();
        else
            env.sync();
    }
}