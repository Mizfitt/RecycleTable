package me.mizfit.recycletable;


import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplexityCalculator {
    private static final Map<Material, Integer> rarityScore = new HashMap<>();
    private static final Map<Material, Integer> materialDifficulty = new HashMap<>();

    static {
        set(Material.OAK_PLANKS, 2); set(Material.STICK, 2); set(Material.COBBLESTONE, 2);
        set(Material.IRON_INGOT, 8); set(Material.GOLD_INGOT, 9); set(Material.LEATHER, 6);
        set(Material.REDSTONE, 12); set(Material.LAPIS_LAZULI, 10); set(Material.BLAZE_ROD, 14);
        set(Material.DIAMOND, 20); set(Material.EMERALD, 18); set(Material.NETHERITE_INGOT, 28);
        materialDifficulty.put(Material.DIAMOND, 10); materialDifficulty.put(Material.NETHERITE_INGOT, 15);
        materialDifficulty.put(Material.IRON_INGOT, 4); materialDifficulty.put(Material.GOLD_INGOT, 5);
        materialDifficulty.put(Material.REDSTONE, 6); materialDifficulty.put(Material.OAK_PLANKS, 1); materialDifficulty.put(Material.STICK, 1);
    }

    private static void set(Material m, int v) { rarityScore.put(m, v); materialDifficulty.putIfAbsent(m, Math.max(1, v/2)); }

    public static int calculateComplexity(ItemStack item) {
        List<ItemStack> raw = RecipeManager.decomposeToRaw(item);
        double sumR = 0, sumD = 0;
        for (ItemStack r : raw) {
            Material m = r.getType();
            int c = Math.max(1, r.getAmount());
            int rS = rarityScore.getOrDefault(m, 3);
            int dS = materialDifficulty.getOrDefault(m, Math.max(1, rS/2));
            sumR += rS * c; sumD += (dS * c) / 2.0;
        }
        double rawScore = sumR + sumD;
        double mapped = 1.0 + ((rawScore - 1.0) * (249.0 / 299.0));
        int score = (int) Math.round(mapped);
        if (score < 1) score = 1; if (score > 250) score = 250;
        return score;
    }

    public static long mapScoreToSeconds(int score) {
        int min = 15, max = 10800; // 15s .. 3h
        double t = (score - 1.0) / (250.0 - 1.0);
        return Math.round(min + t * (max - min));
    }
}

