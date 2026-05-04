package me.mizfit.recycletable;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private static final Map<UUID, RecycleSession> sessions = new HashMap<>();
    public static void registerSession(UUID id, RecycleSession s) { sessions.put(id, s); }
    public static RecycleSession getSession(UUID id) { return sessions.get(id); }
    public static Map<UUID, RecycleSession> getAllSessions() { return new HashMap<>(sessions); }

    /** Finds a session whose tableKey matches the given key, or null if none exists. */
    public static RecycleSession getSessionByTableKey(String tableKey) {
        if (tableKey == null) return null;
        for (RecycleSession s : sessions.values()) {
            if (tableKey.equals(s.getTableKey())) return s;
        }
        return null;
    }
}

