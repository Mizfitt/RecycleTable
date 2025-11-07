package me.mizfit.recycletable;

public class ConfigManager {
    private static RecycleTable plugin;

    public static void load(RecycleTable pl) {
        plugin = pl;
    }

    public static boolean allowHopperInput() {
        return plugin.getConfig().getBoolean("hopper.allow-input", true);
    }

    public static boolean allowHopperOutput() {
        return plugin.getConfig().getBoolean("hopper.allow-output", true);
    }

    public static double getSpeedMultiplier() {
        return plugin.getConfig().getDouble("processing.speed-multiplier", 1.0);
    }

    public static long getSessionExpireMs() {
        return plugin.getConfig().getLong("processing.session-expire-ms", 1000L * 60 * 60 * 24);
    }

    // Enchantments
    public static boolean enchantmentsEnabled() {
        return plugin.getConfig().getBoolean("enchantments.enabled", true);
    }

    public static boolean enchantReturnSingleLevel() {
        return plugin.getConfig().getBoolean("enchantments.return-single-level", true);
    }

    public static boolean enchantReturnMultiLevel() {
        return plugin.getConfig().getBoolean("enchantments.return-multi-level", true);
    }
}
