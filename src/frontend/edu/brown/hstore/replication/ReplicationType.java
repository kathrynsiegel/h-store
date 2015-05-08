package edu.brown.hstore.replication;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Replication Enabled
 * @author aelmore
 */
public enum ReplicationType {
    /**
     * No Replication
     */
    NONE,
    /**
     * Sync
     */
    SYNC,
    /**
     * semi sync
     */
    SEMI,
    /**
     * async
     */
    ASYNC
    ;

    private static final Map<String, ReplicationType> name_lookup = new HashMap<String, ReplicationType>();
    static {
        for (ReplicationType vt : EnumSet.allOf(ReplicationType.class)) {
            name_lookup.put(vt.name().toLowerCase(), vt);
        }
    } // STATIC

    public static ReplicationType get(int idx) {
        ReplicationType values[] = ReplicationType.values();
        if (idx < 0 || idx >= values.length) {
            return (null);
        }
        return (values[idx]);
    }

    public static ReplicationType get(String name) {
        return ReplicationType.name_lookup.get(name.toLowerCase());
    }

}
