package me.mizfit.recycletable;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class HopperListener implements Listener {

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent e) {
        Inventory source = e.getSource();
        Inventory dest = e.getDestination();
        ItemStack item = e.getItem();

        // Prevent hoppers from taking the recycle button or control slots
        if (TableListener.isRecyclingTableInventory(source) || TableListener.isRecyclingTableInventory(dest)) {
            if (item != null && item.getType() == org.bukkit.Material.ANVIL) {
                e.setCancelled(true);
                return;
            }
        }

        // Output restriction
        if (TableListener.isRecyclingTableInventory(source)) {
            if (!ConfigManager.allowHopperOutput()) e.setCancelled(true);
        }

        // Input restriction
        if (TableListener.isRecyclingTableInventory(dest)) {
            if (!ConfigManager.allowHopperInput()) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(InventoryPickupItemEvent e) {
        Inventory inv = e.getInventory();
        if (TableListener.isRecyclingTableInventory(inv)) {
            ItemStack item = e.getItem().getItemStack();
            if (item != null && item.getType() == org.bukkit.Material.ANVIL) {
                e.setCancelled(true);
            }
        }
    }
}
