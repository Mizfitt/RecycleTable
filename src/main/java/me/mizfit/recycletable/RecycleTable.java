package me.mizfit.recycletable;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class RecycleTable extends JavaPlugin {
    private static RecycleTable instance;
    public SessionStorage storage;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Load config & managers
        ConfigManager.load(this);
        RecipeManager.initialize();
        OverflowStorage.initialize(getDataFolder()); // ✅ Correct usage: pass the File data folder

        // Register listeners
        getServer().getPluginManager().registerEvents(new TableListener(), this);
        getServer().getPluginManager().registerEvents(new HopperListener(), this);
        getServer().getPluginManager().registerEvents(new PlaceListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new OverflowListener(), this);

        // Command: /giverecycler
        if (getCommand("giverecycler") != null) {
            getCommand("giverecycler").setExecutor((sender, cmd, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player)) {
                    sender.sendMessage("Only players may use this.");
                    return true;
                }
                RecyclingTableItem.giveTo((org.bukkit.entity.Player) sender, 1);
                sender.sendMessage("Given Recycling Table.");
                return true;
            });
        }

        // Session persistence
        storage = new SessionStorage(getDataFolder());
        Map<UUID, RecycleSession> loaded = storage.loadSessions();

        for (Map.Entry<UUID, RecycleSession> e : loaded.entrySet()) {
            RecycleSession s = e.getValue();
            SessionManager.registerSession(e.getKey(), s);

            // Apply offline compensation
            long offlineSeconds = 0L;
            if (s.getLastActiveTime() > 0)
                offlineSeconds = (System.currentTimeMillis() - s.getLastActiveTime()) / 1000L;

            if (s.isActive()) s.start(this, offlineSeconds);
        }

        // Load placed tables
        TablePersistence.loadPlacedTables(this);

        getLogger().info("RecycleTable enabled. Recipes indexed: " + RecipeManager.getRecipeCount());
    }

    @Override
    public void onDisable() {
        storage.saveSessions(SessionManager.getAllSessions());
        TablePersistence.savePlacedTables(this);
        OverflowStorage.save(); // ✅ Save overflow storage on shutdown
    }

    public static RecycleTable getInstance() { return instance; }
}
