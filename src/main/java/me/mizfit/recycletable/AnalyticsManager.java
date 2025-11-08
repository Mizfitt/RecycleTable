package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Lightweight analytics with periodic autosave.
 * - Tracks usage counts and observed average recipe depth.
 * - Provides usagePenalty and depthBonus signals (0..~0.5) for ComplexityCalculator.
 *
 * CONFIG KEYS:
 * analytics.enabled (boolean)
 * analytics.save-interval-seconds (int)
 * analytics.decay-rate (double)           # 0..1 how fast old data decays each save
 * analytics.rebalance-weight (double)     # applied in ComplexityCalculator
 */
public final class AnalyticsManager {
    private static boolean enabled = false;

    private static final Map<Material, Long> usageCounts = new HashMap<>();
    private static final Map<Material, DepthStats> depthStats = new HashMap<>();

    private static File file;
    private static YamlConfiguration yaml;

    private static BukkitRunnable saverTask;

    private static double cfgDouble(String path, double def) {
        try { return RecycleTable.getInstance().getConfig().getDouble(path, def); }
        catch (Throwable t) { return def; }
    }
    private static int cfgInt(String path, int def) {
        try { return RecycleTable.getInstance().getConfig().getInt(path, def); }
        catch (Throwable t) { return def; }
    }
    private static boolean cfgBool(String path, boolean def) {
        try { return RecycleTable.getInstance().getConfig().getBoolean(path, def); }
        catch (Throwable t) { return def; }
    }

    public static void initialize(JavaPlugin plugin) {
        enabled = cfgBool("analytics.enabled", true);
        file = new File(plugin.getDataFolder(), "analytics.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        load();

        // autosave
        int intervalSec = Math.max(60, cfgInt("analytics.save-interval-seconds", 300));
        if (saverTask != null) saverTask.cancel();
        saverTask = new BukkitRunnable() {
            @Override public void run() { try { saveWithDecay(); } catch (Exception ex) { ex.printStackTrace(); } }
        };
        saverTask.runTaskTimer(plugin, intervalSec * 20L, intervalSec * 20L);
    }

    public static void shutdown() {
        try { saveWithDecay(); } catch (Exception ignored) {}
        if (saverTask != null) saverTask.cancel();
    }

    public static boolean isEnabled() { return enabled; }

    public static void recordProcessed(ItemStack item, int observedDepth) {
        if (!enabled || item == null) return;
        Material m = item.getType();
        usageCounts.put(m, usageCounts.getOrDefault(m, 0L) + 1);

        DepthStats ds = depthStats.computeIfAbsent(m, k -> new DepthStats());
        ds.addSample(observedDepth);
    }

    /**
     * Returns a non-negative small penalty (0..~0.5) based on relative usage.
     * More commonly recycled materials are gently penalized to avoid farm abuse.
     */
    public static double usagePenalty(Material m, double weight) {
        if (!enabled) return 0.0;
        long total = 0L;
        for (Long v : usageCounts.values()) total += v;
        if (total <= 0) return 0.0;
        long count = usageCounts.getOrDefault(m, 0L);

        // Relative use share
        double share = count / (double) total; // 0..1
        // Convert to bounded penalty; sqrt to dampen extremes, capped
        double penalty = Math.min(0.5, Math.sqrt(share) * weight);
        return penalty;
    }

    /**
     * Returns a non-negative small bonus (0..~0.5) for items whose observed craft depth
     * is high relative to the global average.
     */
    public static double depthBonus(Material m, double weight, int maxDepth) {
        if (!enabled) return 0.0;
        double global = 0.0;
        int n = 0;
        for (DepthStats ds : depthStats.values()) {
            global += ds.avg;
            n++;
        }
        if (n == 0) return 0.0;
        global /= n;

        DepthStats ds = depthStats.get(m);
        if (ds == null || ds.samples == 0) return 0.0;

        // Positive if above global average
        double diff = ds.avg - global; // could be negative
        if (diff <= 0) return 0.0;

        double norm = Math.min(1.0, diff / Math.max(1.0, maxDepth));
        return Math.min(0.5, norm * weight);
    }

    /* ------------ persistence ------------ */

    private static void load() {
        try {
            if (yaml.isConfigurationSection("usage")) {
                for (String key : Objects.requireNonNull(yaml.getConfigurationSection("usage")).getKeys(false)) {
                    try {
                        Material m = Material.valueOf(key);
                        long c = yaml.getLong("usage." + key, 0L);
                        usageCounts.put(m, c);
                    } catch (Exception ignored) {}
                }
            }
            if (yaml.isConfigurationSection("depth")) {
                for (String key : Objects.requireNonNull(yaml.getConfigurationSection("depth")).getKeys(false)) {
                    try {
                        Material m = Material.valueOf(key);
                        double avg = yaml.getDouble("depth." + key + ".avg", 0.0);
                        int samples = yaml.getInt("depth." + key + ".samples", 0);
                        DepthStats ds = new DepthStats();
                        ds.avg = avg;
                        ds.samples = samples;
                        depthStats.put(m, ds);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[AnalyticsManager] Failed to load analytics.yml: " + ex.getMessage());
        }
    }

    private static void saveWithDecay() {
        double decay = cfgDouble("analytics.decay-rate", 0.05);
        if (decay > 0) {
            // Decay usage
            for (Map.Entry<Material, Long> e : new ArrayList<>(usageCounts.entrySet())) {
                long val = e.getValue();
                long decayed = Math.max(0L, val - Math.max(1L, Math.round(val * decay)));
                usageCounts.put(e.getKey(), decayed);
            }
            // Decay samples (pull avg softly toward 0 with fewer samples)
            for (DepthStats ds : depthStats.values()) {
                int shrink = Math.max(0, (int) Math.round(ds.samples * decay));
                ds.samples = Math.max(0, ds.samples - shrink);
                // keep avg as-is; with fewer samples it will be corrected by new data
            }
        }

        save();
    }

    private static void save() {
        try {
            yaml.set("usage", null);
            yaml.set("depth", null);

            for (Map.Entry<Material, Long> e : usageCounts.entrySet()) {
                yaml.set("usage." + e.getKey().name(), e.getValue());
            }
            for (Map.Entry<Material, DepthStats> e : depthStats.entrySet()) {
                yaml.set("depth." + e.getKey().name() + ".avg", e.getValue().avg);
                yaml.set("depth." + e.getKey().name() + ".samples", e.getValue().samples);
            }

            yaml.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void logRecycle(ItemStack item) {
    }

    /* ------------ helpers ------------ */

    private static final class DepthStats {
        double avg = 0.0;
        int samples = 0;

        void addSample(int depth) {
            // incremental mean
            samples++;
            avg += (depth - avg) / samples;
        }
    }
}
