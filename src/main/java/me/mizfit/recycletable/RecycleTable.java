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
        AEIntegration.initialize();
        RecipeRegistry.registerAll(this);

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

        // Hourly reminder: tell online players if their table's overflow has items waiting
        final long ONE_HOUR_TICKS = 72000L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                String tableKey = TablePersistence.getTableKeyForOwner(p.getUniqueId());
                if (tableKey == null) continue;
                if (!OverflowStorage.hasOverflow(tableKey)) continue;
                int total = OverflowStorage.overflowItemTotal(tableKey);
                p.sendMessage(org.bukkit.ChatColor.GOLD + "⚠ Your Recycling Table is full! "
                        + org.bukkit.ChatColor.YELLOW + total + " item(s) are sitting in overflow storage. "
                        + org.bukkit.ChatColor.GRAY + "Open your table and take items from the output to free up space.");
            }
        }, ONE_HOUR_TICKS, ONE_HOUR_TICKS);

        // Load placed tables
        TablePersistence.loadPlacedTables(this);

        getLogger().info("RecycleTable enabled. Recipes indexed: " + RecipeManager.getRecipeCount());
    }

    @Override
    public void onDisable() {
        HologramManager.removeAll();
        AnalyticsManager.shutdown();
        if (storage != null) storage.saveSessions(SessionManager.getAllSessions());
        TablePersistence.savePlacedTables(this);
        OverflowStorage.save();
    }

    public static RecycleTable getInstance() { return instance; }
}