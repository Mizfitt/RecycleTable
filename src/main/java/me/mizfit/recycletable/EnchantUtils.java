package me.mizfit.recycletable;


import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.*;

public class EnchantUtils {
    public static Map<Enchantment, Integer> getReturnedEnchantments(ItemStack item) {
        Map<Enchantment, Integer> returned = new HashMap<>();
        if (item == null || !item.hasItemMeta()) return returned;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (!meta.hasEnchants()) return returned;

        Map<Enchantment, Integer> enchants = meta.getEnchants();
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            Enchantment ench = e.getKey();
            int level = e.getValue();
            int max = ench.getMaxLevel();
            if (max == 1) {
                if (ConfigManager.enchantReturnSingleLevel()) returned.put(ench, 1);
                continue;
            }
            if (level > 1 && ConfigManager.enchantReturnMultiLevel()) {
                returned.put(ench, level - 1);
            }
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
}
