package me.mizfit.recycletable;


import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SessionStorage {
    private final File file;
    private final YamlConfiguration cfg;

    public SessionStorage(File dataFolder) {
        this.file = new File(dataFolder, "sessions.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void saveSessions(Map<UUID, RecycleSession> sessions) {
        cfg.set("sessions", null);
        for (Map.Entry<UUID, RecycleSession> e : sessions.entrySet()) {
            String base = "sessions." + e.getKey();
            RecycleSession s = e.getValue();
            cfg.set(base + ".active", s.isActive());
            cfg.set(base + ".timeLeft", s.getTimeLeft());
            cfg.set(base + ".complexity", s.getComplexity());
            cfg.set(base + ".items", s.serializeItems());
            cfg.set(base + ".progress", s.getProgress());
            cfg.set(base + ".lastActive", System.currentTimeMillis());
        }
        try { cfg.save(file); } catch (IOException ex) { ex.printStackTrace(); }
    }

    public Map<UUID, RecycleSession> loadSessions() {
        Map<UUID, RecycleSession> map = new HashMap<>();
        if (!cfg.contains("sessions")) return map;
        for (String key : cfg.getConfigurationSection("sessions").getKeys(false)) {
            UUID id = UUID.fromString(key);
            RecycleSession s = new RecycleSession(id);
            // Note: This is a minimal resume; in a full implementation, you'd restore queue items, progress, and bind to an inventory again if needed.
            s.start(RecycleTable.getInstance());
            map.put(id, s);
        }
        return map;
    }
}

