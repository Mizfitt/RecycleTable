package me.mizfit.recycletable;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Handles the automatic repopulation of overflow items when the player
 * removes items from the output section of their Recycling Table.
 */
public class OverflowListener implements Listener {

    /**
     * Detects when a player removes an item from the recycling output slots.
     * If space becomes available, the player's overflow storage will repopulate it.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        if (!TableListener.GUI_TITLE.equals(e.getView().getTitle())) return;

        int slot = e.getRawSlot();
        if (TableListener.isOutputSlot(slot)) {
            Player player = (Player) e.getWhoClicked();

            // Delay repopulation slightly to ensure the slot is actually empty
            OverflowStorage.tryRepopulate(player, inv);
        }
    }

    /**
     * When a player closes the GUI, attempt one last overflow repopulation pass.
     * This helps fill available slots before the session ends.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!TableListener.GUI_TITLE.equals(e.getView().getTitle())) return;

        Player player = (Player) e.getPlayer();
        OverflowStorage.tryRepopulate(player, inv);
    }
}
