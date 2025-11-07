package me.mizfit.recycletable;

import org.bukkit.Bukkit;
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

        ConfigManager.load(this);
        RecipeManager.initialize();

        getServer().getPluginManager().registerEvents(new TableListener(), this);
        getServer().getPluginManager().registerEvents(new HopperListener(), this);
        getServer().getPluginManager().registerEvents(new PlaceListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);

        this.getCommand("giverecycler").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Only players may use this.");
                return true;
            }
            RecyclingTableItem.giveTo((org.bukkit.entity.Player) sender, 1);
            sender.sendMessage("Given Recycling Table.");
            return true;
        });

        storage = new SessionStorage(getDataFolder());
        Map<UUID, RecycleSession> loaded = storage.loadSessions();
        for (Map.Entry<UUID, RecycleSession> e : loaded.entrySet()) {
            RecycleSession s = e.getValue();
            SessionManager.registerSession(e.getKey(), s);
            if (s.isActive()) s.resumeTask(this);
        }

        TablePersistence.loadPlacedTables(this);
        getLogger().info("RecycleTable enabled. Recipes indexed: " + RecipeManager.getRecipeCount());
    }

    @Override
    public void onDisable() {
        storage.saveSessions(SessionManager.getAllSessions());
        TablePersistence.savePlacedTables(this);
    }

    public static RecycleTable getInstance() { return instance; }
}
