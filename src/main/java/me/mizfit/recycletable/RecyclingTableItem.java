package me.mizfit.recycletable;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class RecyclingTableItem {
    public static final String DISPLAY_NAME = ChatColor.GOLD + "Recycling Table";
    private static final String TABLE_ID    = "recycling_table";

    private static NamespacedKey tableKey;

    private static NamespacedKey getTableKey() {
        if (tableKey == null)
            tableKey = new NamespacedKey(RecycleTable.getInstance(), "item_id");
        return tableKey;
    }

    public static ItemStack createItem() {
        ItemStack table = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta   = table.getItemMeta();
        meta.setDisplayName(DISPLAY_NAME);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY   + "Right-click to open the Recycling Table GUI",
                ChatColor.GRAY   + "Place items on the left; results appear on the right",
                ChatColor.YELLOW + "Press Recycle to start processing",
                ChatColor.AQUA   + "Place this block to make it permanent"
        ));
        meta.getPersistentDataContainer().set(getTableKey(), PersistentDataType.STRING, TABLE_ID);
        table.setItemMeta(meta);
        return table;
    }

    public static boolean isRecyclingTable(ItemStack i) {
        if (i == null || i.getType() != Material.CRAFTING_TABLE || !i.hasItemMeta()) return false;
        // PDC check first (reliable for items crafted after this version)
        String stored = i.getItemMeta().getPersistentDataContainer()
                .get(getTableKey(), PersistentDataType.STRING);
        if (TABLE_ID.equals(stored)) return true;
        // Fallback: display-name check for tables placed before PDC was added
        return i.getItemMeta().hasDisplayName() && DISPLAY_NAME.equals(i.getItemMeta().getDisplayName());
    }

    public static void giveTo(Player p, int amount) {
        ItemStack i = createItem();
        i.setAmount(amount);
        p.getInventory().addItem(i);
    }
}
