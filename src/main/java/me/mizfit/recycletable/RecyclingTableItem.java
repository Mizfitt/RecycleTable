package me.mizfit.recycletable;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class RecyclingTableItem {
    public static final String DISPLAY_NAME = ChatColor.GOLD + "Recycling Table";

    public static ItemStack createItem() {
        ItemStack table = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = table.getItemMeta();
        meta.setDisplayName(DISPLAY_NAME);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click to open the Recycling Table GUI",
                ChatColor.GRAY + "Place items on the left; results appear on the right",
                ChatColor.YELLOW + "Press Recycle to start processing",
                ChatColor.AQUA + "Place this block to make it permanent"
        ));
        table.setItemMeta(meta);
        return table;
    }

    public static boolean isRecyclingTable(ItemStack i) {
        if (i == null) return false;
        if (i.getType() != Material.CRAFTING_TABLE) return false;
        if (!i.hasItemMeta()) return false;
        if (!i.getItemMeta().hasDisplayName()) return false;
        return DISPLAY_NAME.equals(i.getItemMeta().getDisplayName());
    }

    public static void giveTo(Player p, int amount) {
        ItemStack i = createItem();
        i.setAmount(amount);
        p.getInventory().addItem(i);
    }
}