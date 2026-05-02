package me.mizfit.recycletable;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces slot-aware hopper interactions with placed Recycling Tables.
 *
 * Input  hoppers  → may only push into input  slots (0–26, excluding control slots).
 * Output hoppers  → may only pull  from output slots (27–53, excluding control slots).
 * Overflow repopulation is triggered automatically after every successful output pull.
 * All protections are active regardless of whether a player has the GUI open.
 */
public class HopperListener implements Listener {

    /** Returns true if this inventory belongs to a registered recycling table. */
    private static boolean isTableInv(Inventory inv) {
        return inv != null && TablePersistence.getKeyForInventory(inv) != null;
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent e) {
        Inventory source = e.getSource();
        Inventory dest   = e.getDestination();

        boolean sourceIsTable = isTableInv(source);
        boolean destIsTable   = isTableInv(dest);

        if (!sourceIsTable && !destIsTable) return;

        // ── Hopper pushing INTO the table ────────────────────────────────────
        if (destIsTable) {
            // Always cancel — we replace Bukkit's slot-unaware transfer with our own
            e.setCancelled(true);
            if (ConfigManager.allowHopperInput()) {
                pushToInputSlots(dest, source, e.getItem());
            }
            return;
        }

        // ── Hopper pulling FROM the table ────────────────────────────────────
        if (sourceIsTable) {
            e.setCancelled(true);
            if (ConfigManager.allowHopperOutput()) {
                pullFromOutputSlots(source, dest);
            }
        }
    }

    /**
     * Pushes one item from sourceInv into the first available input slot
     * (0–26, excluding control slots) of tableInv.
     * Merges with partial stacks before using an empty slot.
     */
    private void pushToInputSlots(Inventory tableInv, Inventory sourceInv, ItemStack template) {
        // Pass 1: merge into an existing partial stack
        for (int i = 0; i <= 26; i++) {
            if (TableListener.isControlSlot(i)) continue;
            ItemStack slot = tableInv.getItem(i);
            if (slot == null || !slot.isSimilar(template) || slot.getAmount() >= slot.getMaxStackSize()) continue;
            slot.setAmount(slot.getAmount() + 1);
            tableInv.setItem(i, slot);
            removeOne(sourceInv, template);
            return;
        }

        // Pass 2: place in the first empty slot
        for (int i = 0; i <= 26; i++) {
            if (TableListener.isControlSlot(i)) continue;
            ItemStack slot = tableInv.getItem(i);
            if (slot != null && slot.getType() != Material.AIR) continue;
            ItemStack place = template.clone();
            place.setAmount(1);
            tableInv.setItem(i, place);
            removeOne(sourceInv, template);
            return;
        }
        // All input slots full — item stays in the hopper (event already cancelled)
    }

    /**
     * Pulls one item from the first occupied output slot (27–53, excluding
     * control slots) of tableInv and moves it into destInv.
     * Triggers overflow repopulation so freed output slots are refilled.
     */
    private void pullFromOutputSlots(Inventory tableInv, Inventory destInv) {
        for (int i = 27; i <= 53; i++) {
            if (TableListener.isControlSlot(i)) continue;
            ItemStack slot = tableInv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;

            // Attempt to move one unit into the destination
            ItemStack toMove = slot.clone();
            toMove.setAmount(1);
            if (!destInv.addItem(toMove).isEmpty()) continue; // destination full, try next slot

            // Successfully moved — update source slot
            slot.setAmount(slot.getAmount() - 1);
            tableInv.setItem(i, slot.getAmount() <= 0 ? null : slot);

            // A slot just opened up — let overflow fill it
            String tableKey = TablePersistence.getKeyForInventory(tableInv);
            if (tableKey != null) OverflowStorage.tryRepopulate(tableKey, tableInv, null);

            return;
        }
        // No moveable output items found — hopper stays idle this tick
    }

    /**
     * Removes exactly one item matching the template from the given inventory.
     */
    private void removeOne(Inventory inv, ItemStack template) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || !slot.isSimilar(template)) continue;
            if (slot.getAmount() > 1) {
                slot.setAmount(slot.getAmount() - 1);
                inv.setItem(i, slot);
            } else {
                inv.setItem(i, null);
            }
            return;
        }
    }
}
