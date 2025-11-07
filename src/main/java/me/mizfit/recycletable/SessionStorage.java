package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles saving and loading of active recycling sessions.
 * Now supports full queue persistence, offline compensation, and player feedback.
 */
public class SessionStorage {
    private final File file;
    private final YamlConfiguration cfg;

    public SessionStorage(File dataFolder) {
        this.file = new File(dataFolder, "sessions.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Save all active sessions to sessions.yml, including queue items and timestamps.
     */
    public void saveSessions(Map<UUID, RecycleSession> sessions) {
        cfg.set("sessions", null);

        for (Map.Entry<UUID, RecycleSession> entry : sessions.entrySet()) {
            UUID id = entry.getKey();
            RecycleSession s = entry.getValue();

            String base = "sessions." + id;
            cfg.set(base + ".active", s.isActive());
            cfg.set(base + ".timeLeft", s.getTimeLeft());
            cfg.set(base + ".progress", s.getProgress());
            cfg.set(base + ".lastActiveTime", s.getLastActiveTime());
            cfg.set(base + ".items", s.serializeItems());
        }

        try {
            cfg.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Loads all saved sessions and rebuilds them with queue + offline time compensation.
     */
    public Map<UUID, RecycleSession> loadSessions() {
        Map<UUID, RecycleSession> map = new HashMap<>();
        if (!cfg.contains("sessions")) return map;

        for (String key : cfg.getConfigurationSection("sessions").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                boolean active = cfg.getBoolean("sessions." + key + ".active");
                long lastActiveTime = cfg.getLong("sessions." + key + ".lastActiveTime", System.currentTimeMillis());
                List<ItemStack> queued = (List<ItemStack>) cfg.get("sessions." + key + ".items");

                long offlineSeconds = (System.currentTimeMillis() - lastActiveTime) / 1000;
                if (offlineSeconds < 0) offlineSeconds = 0;

                // Create a temporary GUI for restoration
                Inventory tempInv = Bukkit.createInventory(null, 54, TableListener.GUI_TITLE);
                RecycleSession session = new RecycleSession(id, queued, tempInv, lastActiveTime);

                // Start with offline compensation
                session.start(RecycleTable.getInstance(), offlineSeconds);

                SessionManager.registerSession(id, session);
                map.put(id, session);

                // If player is online, send feedback
                Player p = Bukkit.getPlayer(id);
                if (p != null && active) {
                    int minutes = (int) Math.floor(offlineSeconds / 60.0);
                    p.sendMessage(ChatColor.YELLOW + "While you were offline for " + minutes +
                            " minutes, your recycling continued.");
                }

            } catch (Exception ex) {
                Bukkit.getLogger().warning("[RecycleTable] Failed to load session for " + key);
                ex.printStackTrace();
            }
        }
        return map;
    }
}
