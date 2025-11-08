package me.mizfit.recycletable;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
        int maxDurability = getMaxDurability(type);

        // --- Normal items with durability ---
        if (maxDurability > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable) {
                Damageable dmg = (Damageable) meta;
                int damage = Math.max(0, dmg.getDamage());
                int remaining = Math.max(0, maxDurability - damage);
                return clamp((double) remaining / (double) maxDurability);
            }

            Integer legacyDamage = getLegacyDurability(item);
            if (legacyDamage != null) {
                int remaining = Math.max(0, maxDurability - legacyDamage);
                return clamp((double) remaining / (double) maxDurability);
            }
        }

        // --- Special handling for anvils (different "damage stages") ---
        if (type == Material.ANVIL) {
            int stage = 0;

            try {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof BlockDataMeta) {
                    BlockData blockData = getBlockDataSafe((BlockDataMeta) meta);
                    if (blockData != null && blockData.getClass().getSimpleName().equals("Anvil")) {
                        Method getDamage = blockData.getClass().getMethod("getDamage");
                        Object result = getDamage.invoke(blockData);
                        if (result instanceof Integer) stage = (int) result;
                    }
                } else {
                    Integer legacyDamage = getLegacyDurability(item);
                    if (legacyDamage != null) stage = legacyDamage;
                }
            } catch (Throwable ignored) {}

            int maxStage = 2; // chipped & damaged
            double fraction = ((double) (maxStage - stage) / (double) maxStage);
            return clamp(fraction);
        }

        // --- Default (non-damageable items) ---
        return 1.0;
    }

    /**
     * Version-safe retrieval of BlockData from BlockDataMeta
     * Works across all API variants (with or without parameters).
     */
    private static BlockData getBlockDataSafe(BlockDataMeta meta) {
        try {
            // Try modern no-arg version first
            Method method = meta.getClass().getMethod("getBlockData");
            Object result = method.invoke(meta);
            if (result instanceof BlockData) return (BlockData) result;
        } catch (Throwable ignored) {
            // fallback if newer versions require args
            try {
                Method method = meta.getClass().getMethod("getBlockData", Material.class);
                Object result = method.invoke(meta, Material.ANVIL);
                if (result instanceof BlockData) return (BlockData) result;
            } catch (Throwable ignoreAgain) {}
        }
        return null;
    }

    /** Ensures a value stays within a given range. */
    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Provides a safe fallback durability lookup for older Minecraft versions. */
    public static int getMaxDurability(Material m) {
        try {
            return Math.max(0, m.getMaxDurability());
        } catch (Throwable e) {
            switch (m) {
                case WOODEN_SWORD: case WOODEN_PICKAXE: case WOODEN_AXE:
                case WOODEN_SHOVEL: case WOODEN_HOE:
                    return 59;
                case STONE_SWORD: case STONE_PICKAXE: case STONE_AXE:
                case STONE_SHOVEL: case STONE_HOE:
                    return 131;
                case IRON_SWORD: case IRON_PICKAXE: case IRON_AXE:
                case IRON_SHOVEL: case IRON_HOE:
                    return 250;
                case DIAMOND_SWORD: case DIAMOND_PICKAXE: case DIAMOND_AXE:
                case DIAMOND_SHOVEL: case DIAMOND_HOE:
                    return 1561;
                case GOLDEN_SWORD: case GOLDEN_PICKAXE: case GOLDEN_AXE:
                case GOLDEN_SHOVEL: case GOLDEN_HOE:
                    return 32;
                case NETHERITE_SWORD: case NETHERITE_PICKAXE: case NETHERITE_AXE:
                case NETHERITE_SHOVEL: case NETHERITE_HOE:
                    return 2031;
                default: return 0;
            }
        }
    }

    private static final Method LEGACY_GET_DURABILITY = resolveLegacyDurabilityMethod();

    private static Method resolveLegacyDurabilityMethod() {
        try {
            Method method = ItemStack.class.getMethod("getDurability");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Integer getLegacyDurability(ItemStack item) {
        if (LEGACY_GET_DURABILITY == null) return null;
        try {
            Object result = LEGACY_GET_DURABILITY.invoke(item);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {}
        return null;
    }
}
