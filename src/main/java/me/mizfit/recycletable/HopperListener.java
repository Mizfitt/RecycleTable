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

        if (TableListener.isRecyclingTableInventory(source)) {
            if (ConfigManager.allowHopperOutput()) {
                return; // permit extraction
            } else {
                e.setCancelled(true);
                return;
            }
        }
        if (TableListener.isRecyclingTableInventory(dest)) {
            if (!ConfigManager.allowHopperInput()) { e.setCancelled(true); return; }
            return; // allow insertion (best-effort into input area)
        }
    }

    @EventHandler
    public void onPickup(InventoryPickupItemEvent e) {
        Inventory inv = e.getInventory();
        if (TableListener.isRecyclingTableInventory(inv)) {
            ItemStack it = e.getItem().getItemStack();
            boolean recyclable = RecipeManager.getRecipeFor(it.getType()) != null && !RecipeManager.isBlacklisted(it.getType());
            if (recyclable) { e.setCancelled(true); }
        }
    }
}

