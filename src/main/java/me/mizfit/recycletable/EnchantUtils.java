package me.mizfit.recycletable;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EnchantUtils {

    /**
     * Returns the enchantments that should be handed back as books when this item is recycled.
     * Handles both regular enchanted gear and enchanted books (which store enchants differently).
     *
     * Rules (unchanged):
     *  - Single-level enchant (e.g. Mending, Silk Touch): return level 1 if return-single-level is on.
     *  - Multi-level enchant at level > 1: return one level lower (Sharpness V → IV).
     *  - Multi-level enchant at level 1: return nothing (by design).
     */
    public static Map<Enchantment, Integer> getReturnedEnchantments(ItemStack item) {
        Map<Enchantment, Integer> returned = new HashMap<>();
        if (item == null || !item.hasItemMeta()) return returned;

        ItemMeta meta = item.getItemMeta();
        Map<Enchantment, Integer> enchants;

        if (meta instanceof EnchantmentStorageMeta) {
            // Enchanted books use a separate stored-enchants API — hasEnchants() is always false for them
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) meta;
            if (!esm.hasStoredEnchants()) return returned;
            enchants = esm.getStoredEnchants();
        } else {
            if (!meta.hasEnchants()) return returned;
            enchants = meta.getEnchants();
        }

        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            Enchantment ench = e.getKey();
            int level = e.getValue();
            int max   = ench.getMaxLevel();

            if (max == 1) {
                // Single-level enchant (Mending, Silk Touch, Infinity, etc.)
                if (ConfigManager.enchantReturnSingleLevel()) returned.put(ench, 1);
            } else if (level > 1 && ConfigManager.enchantReturnMultiLevel()) {
                // Multi-level: return one tier lower
                returned.put(ench, level - 1);
            }
            // Multi-level at level 1 intentionally returns nothing
        }
        return returned;
    }

    public static List<ItemStack> generateEnchantmentBooks(Map<Enchantment, Integer> enchants) {
        List<ItemStack> books = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(e.getKey(), e.getValue(), false);
            book.setItemMeta(meta);
            books.add(book);
        }
        return books;
    }

    /**
     * Returns true if this item has any enchantments worth checking —
     * works for both regular gear and enchanted books.
     */
    public static boolean hasAnyEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            return ((EnchantmentStorageMeta) meta).hasStoredEnchants();
        }
        return meta.hasEnchants();
    }
}
