package me.mizfit.recycletable;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Utility class that handles version-safe durability and damage calculations.
 * Works from MC 1.8 through modern 1.20.x versions.
 */
public class CompatibilityUtils {

    /**
     * Calculates how much of an item's durability remains as a fraction (0.0 - 1.0).
     * Returns 1.0 for items without durability.
     */
    public static double getDurabilityFactor(ItemStack item) {
        if (item == null) return 1.0;

        Material type = item.getType();
        int maxDurability = type.getMaxDurability();

        // --- Normal items with durability ---
        if (maxDurability > 0) {
            try {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof Damageable) {
                    Damageable dmg = (Damageable) meta;
                    int damage = dmg.getDamage();
                    int remaining = Math.max(0, maxDurability - damage);
                    return clamp((double) remaining / (double) maxDurability, 0.0, 1.0);
                }
            } catch (Throwable ignored) {
                // fallback to legacy method for older versions
            }

            try {
                short dur = item.getDurability();
                int remaining = Math.max(0, maxDurability - dur);
                return clamp((double) remaining / (double) maxDurability, 0.0, 1.0);
            } catch (Throwable ignored) {}
        }

        // --- Special handling for anvils (different "damage stages") ---
        if (type == Material.ANVIL) {
            int stage = 0;
            try {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof Damageable) {
                    Damageable dmg = (Damageable) meta;
                    stage = dmg.getDamage();
                } else {
                    stage = item.getDurability();
                }
            } catch (Throwable ignored) {}

            int maxStage = 2; // chipped & damaged
            double fraction = ((double) (maxStage - stage) / (double) maxStage);
            return clamp(fraction, 0.0, 1.0);
        }

        // --- Default (non-damageable items) ---
        return 1.0;
    }

    /**
     * Ensures a value stays within a given range.
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Provides a safe fallback durability lookup for older Minecraft versions.
     * Modern versions call Material#getMaxDurability() directly.
     */
    public static int getMaxDurability(Material m) {
        try {
            return Math.max(0, m.getMaxDurability());
        } catch (NoSuchMethodError | Exception e) {
            switch (m) {
                case WOODEN_SWORD:
                case WOODEN_PICKAXE:
                case WOODEN_AXE:
                case WOODEN_SHOVEL:
                case WOODEN_HOE:
                    return 59;

                case STONE_SWORD:
                case STONE_PICKAXE:
                case STONE_AXE:
                case STONE_SHOVEL:
                case STONE_HOE:
                    return 131;

                case IRON_SWORD:
                case IRON_PICKAXE:
                case IRON_AXE:
                case IRON_SHOVEL:
                case IRON_HOE:
                    return 250;

                case DIAMOND_SWORD:
                case DIAMOND_PICKAXE:
                case DIAMOND_AXE:
                case DIAMOND_SHOVEL:
                case DIAMOND_HOE:
                    return 1561;

                case GOLDEN_SWORD:
                case GOLDEN_PICKAXE:
                case GOLDEN_AXE:
                case GOLDEN_SHOVEL:
                case GOLDEN_HOE:
                    return 32;

                case NETHERITE_SWORD:
                case NETHERITE_PICKAXE:
                case NETHERITE_AXE:
                case NETHERITE_SHOVEL:
                case NETHERITE_HOE:
                    return 2031;

                default:
                    return 0;
            }
        }
    }
}