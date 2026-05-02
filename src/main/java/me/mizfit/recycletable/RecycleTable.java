package me.mizfit.recycletable;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

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
        OverflowStorage.initialize(getDataFolder());
        AnalyticsManager.initialize(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new TableListener(), this);
        getServer().getPluginManager().registerEvents(new HopperListener(), this);
        getServer().getPluginManager().registerEvents(new PlaceListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new OverflowListener(), this);

        // Command: /recycletable reload
        if (getCommand("recycletable") != null) {
            getCommand("recycletable").setExecutor((sender, cmd, label, args) -> {
                if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /recycletable reload");
                    return true;
                }
                reloadConfig();
                ConfigManager.load(this);
                RecipeManager.initialize();
                sender.sendMessage(ChatColor.GREEN + "RecycleTable config reloaded.");
                return true;
            });
        }

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

        // Session persistence — loadSessions() handles registration and start internally
        storage = new SessionStorage(getDataFolder());
        storage.loadSessions();

        // Load placed tables
        TablePersistence.loadPlacedTables(this);

        getLogger().info("RecycleTable enabled. Recipes indexed: " + RecipeManager.getRecipeCount());
    }

    @Override
    public void onDisable() {
        AnalyticsManager.shutdown();
        storage.saveSessions(SessionManager.getAllSessions());
        TablePersistence.savePlacedTables(this);
        OverflowStorage.save();
    }

    public static RecycleTable getInstance() { return instance; }
}