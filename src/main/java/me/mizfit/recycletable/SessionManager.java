package me.mizfit.recycletable;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private static final Map<UUID, RecycleSession> sessions = new HashMap<>();
    public static void registerSession(UUID id, RecycleSession s) { sessions.put(id, s); }
    public static RecycleSession getSession(UUID id) { return sessions.get(id); }
    public static Map<UUID, RecycleSession> getAllSessions() { return new HashMap<>(sessions); }
}

