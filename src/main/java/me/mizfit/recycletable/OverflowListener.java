package me.mizfit.recycletable;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Triggers overflow repopulation when a player takes items from the output section
 * or closes the Recycling Table GUI, freeing up slots for overflow items to return.
 */
public class OverflowListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!TableListener.GUI_TITLE.equals(e.getView().getTitle())) return;
        if (!TableListener.isOutputSlot(e.getRawSlot())) return;

        Inventory inv = e.getInventory();
        String tableKey = TablePersistence.getKeyForInventory(inv);
        if (tableKey == null) return;

        OverflowStorage.tryRepopulate(tableKey, inv, (Player) e.getWhoClicked());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!TableListener.GUI_TITLE.equals(e.getView().getTitle())) return;

        Inventory inv = e.getInventory();
        String tableKey = TablePersistence.getKeyForInventory(inv);
        if (tableKey == null) return;

        OverflowStorage.tryRepopulate(tableKey, inv, (Player) e.getPlayer());
    }
}
