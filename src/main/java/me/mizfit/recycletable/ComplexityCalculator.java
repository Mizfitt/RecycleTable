package me.mizfit.recycletable;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ComplexityCalculator (algorithmic + self-learning).
 *
 * CONFIG KEYS (add to your config.yml if not present):
 *
 * analytics:
 *   enabled: true
 *   save-interval-seconds: 300
 *   decay-rate: 0.05
 *   rebalance-weight: 0.25
 *   depth-weight: 0.4
 *   usage-weight: 0.6
 *
 * complexity:
 *   min-seconds: 15
 *   max-seconds: 10800
 *   base-rarity-weight: 0.5
 *   base-difficulty-weight: 0.5
 *   smoothing: 0.3
 *   max-depth: 6
 *   smelting-bias: 0.15
 *   unstackable-bias: 0.1
 *   dimension-bias-overworld: 0.0
 *   dimension-bias-nether: 0.15
 *   dimension-bias-end: 0.25
 */
public class ComplexityCalculator {

    // Cache depth for speed
    private static final Map<Material, Integer> DEPTH_CACHE = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_DEPTH = 6;

    private static double cfgDouble(String path, double def) {
        try {
            return RecycleTable.getInstance().getConfig().getDouble(path, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static int cfgInt(String path, int def) {
        try {
            return RecycleTable.getInstance().getConfig().getInt(path, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static boolean isOre(Material m) {
        final String n = m.name();
        return n.endsWith("_ORE") || n.contains("DEEPSLATE_") && n.endsWith("_ORE") || n.endsWith("_RAW_BLOCK");
    }

    private static boolean isNether(Material m) {
        final String n = m.name();
        return n.contains("NETHER") || n.contains("BLAZE") || n.contains("QUARTZ") || n.contains("ANCient_DEBRIS".toUpperCase());
    }

    private static boolean isEnd(Material m) {
        final String n = m.name();
        return n.contains("END_") || n.contains("CHORUS");
    }

    private static boolean isMobDrop(Material m) {
        final String n = m.name();
        return n.contains("BONE") || n.contains("STRING") || n.contains("ROTTEN_FLESH") || n.contains("ENDER_PEARL")
                || n.contains("BLAZE_ROD") || n.contains("GUNPOWDER") || n.contains("SLIME_BALL") || n.contains("MEMBRANE")
                || n.contains("SHULKER_SHELL");
    }

    private static boolean isCropOrPlant(Material m) {
        final String n = m.name();
        return n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO") || n.contains("BEETROOT")
                || n.contains("SEEDS") || n.contains("MELON") || n.contains("PUMPKIN") || n.contains("SUGAR_CANE")
                || n.contains("BAMBOO") || n.contains("KELP") || n.contains("FLOWER") || n.contains("SAPLING")
                || n.contains("NETHER_WART") || n.contains("COCOA") || n.contains("CACTUS");
    }

    private static boolean isSmeltable(Material m) {
        // A quick heuristic: ingots, glass, cooked variants, etc.
        final String n = m.name();
        return n.contains("INGOT") || n.contains("GLASS") || n.startsWith("COOKED_") || n.contains("TERRACOTTA")
                || n.contains("BRICK") || n.contains("SMOOTH_") || n.contains("CHARCOAL");
    }

    private static double baseRarityPrior(Material m) {
        // Score 0..1 (higher = rarer/harder by priors)
        final String n = m.name();
        double score = 0.25; // default base

        if (n.contains("NETHERITE") || n.contains("ANCIENT_DEBRIS")) score = 0.95;
        else if (n.contains("DIAMOND")) score = 0.85;
        else if (n.contains("EMERALD")) score = 0.8;
        else if (n.contains("REDSTONE") || n.contains("LAPIS")) score = 0.6;
        else if (n.contains("GOLD")) score = 0.55;
        else if (n.contains("IRON")) score = 0.45;
        else if (n.contains("COPPER")) score = 0.35;
        else if (n.contains("COAL")) score = 0.25;

        if (isOre(m)) score = Math.max(score, 0.4);
        if (isMobDrop(m)) score = Math.max(score, 0.5);
        if (isCropOrPlant(m)) score = Math.min(score, 0.2);

        // Dimension bias
        double ow = cfgDouble("complexity.dimension-bias-overworld", 0.0);
        double nether = cfgDouble("complexity.dimension-bias-nether", 0.15);
        double end = cfgDouble("complexity.dimension-bias-end", 0.25);
        if (isNether(m)) score += nether;
        if (isEnd(m)) score += end;
        score += ow; // usually 0

        // Stackability bias: unstackable often harder to obtain/handle
        if (m.getMaxStackSize() == 1) score += cfgDouble("complexity.unstackable-bias", 0.1);

        // Smelting bias: adds operational friction
        if (isSmeltable(m)) score += cfgDouble("complexity.smelting-bias", 0.15);

        return clamp01(score);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /**
     * Recursively estimate crafting depth (0 for raw materials; increases with crafting layers).
     * Capped by config complexity.max-depth to avoid deep recursion.
     */
    public static int estimateRecipeDepth(Material root) {
        return estimateRecipeDepth(root, new HashSet<>(), 0, cfgInt("complexity.max-depth", DEFAULT_MAX_DEPTH));
    }

    private static int estimateRecipeDepth(Material mat, Set<Material> seen, int depth, int maxDepth) {
        if (depth >= maxDepth) return depth;
        if (mat == null || seen.contains(mat)) return depth;
        seen.add(mat);

        // Cached?
        Integer cached = DEPTH_CACHE.get(mat);
        if (cached != null) return Math.min(cached, maxDepth);

        RecipeManager.RecipeData r = RecipeManager.getRecipeFor(mat);
        if (r == null) {
            DEPTH_CACHE.put(mat, 0);
            return 0;
        }

        int best = 0;
        for (ItemStack ing : r.getIngredients()) {
            int sub = estimateRecipeDepth(ing.getType(), seen, depth + 1, maxDepth);
            if (sub > best) best = sub;
        }
        DEPTH_CACHE.put(mat, best);
        return best;
    }

    /**
     * Main complexity (1..250)
     */
    public static int calculateComplexity(ItemStack item) {
        if (item == null || item.getType() == null) return 1;
        final Material m = item.getType();

        // Priors (0..1)
        double rarityPrior = baseRarityPrior(m);

        // Difficulty from recipe depth normalized 0..1 by max depth
        int depth = estimateRecipeDepth(m);
        double depthNorm = (cfgInt("complexity.max-depth", DEFAULT_MAX_DEPTH) == 0)
                ? 0.0 : (depth / (double) cfgInt("complexity.max-depth", DEFAULT_MAX_DEPTH));
        depthNorm = clamp01(depthNorm);

        // Analytics modifiers (0..1): convert to signed adjustments
        double usageAdj = 0.0;
        double depthAdj = 0.0;
        if (AnalyticsManager.isEnabled()) {
            // usage penalty (more usage -> a bit harder over time)
            usageAdj = AnalyticsManager.usagePenalty(m, cfgDouble("analytics.usage-weight", 0.6));
            // depth bonus (observed deeper items -> a bit harder)
            depthAdj = AnalyticsManager.depthBonus(m, cfgDouble("analytics.depth-weight", 0.4), cfgInt("complexity.max-depth", DEFAULT_MAX_DEPTH));
        }

        // Blend base signals
        double baseRarityWeight = cfgDouble("complexity.base-rarity-weight", 0.5);
        double baseDiffWeight   = cfgDouble("complexity.base-difficulty-weight", 0.5);
        double baseSignal = (rarityPrior * baseRarityWeight) + (depthNorm * baseDiffWeight);

        // Apply analytics influence
        double rebalanceWeight = cfgDouble("analytics.rebalance-weight", 0.25);
        double adjusted = baseSignal + rebalanceWeight * (usageAdj + depthAdj);

        // Smooth for stability
        double smoothing = cfgDouble("complexity.smoothing", 0.3);
        double smoothed = (1.0 - smoothing) * baseSignal + (smoothing) * adjusted;
        smoothed = clamp01(smoothed);

        // Map 0..1 to 1..250
        int score = (int) Math.round(1 + smoothed * 249.0);
        if (score < 1) score = 1;
        if (score > 250) score = 250;
        return score;
    }

    public static long mapScoreToSeconds(int score) {
        int min = (int) cfgDouble("complexity.min-seconds", 15);
        int max = (int) cfgDouble("complexity.max-seconds", 10800);
        if (score <= 1) return min;
        if (score >= 250) return max;
        double t = (score - 1.0) / 249.0;
        return Math.round(min + t * (max - min));
    }
}
