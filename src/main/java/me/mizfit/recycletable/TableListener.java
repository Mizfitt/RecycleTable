package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TableListener implements Listener {
    public static final String GUI_TITLE = ChatColor.DARK_GREEN + "Recycling Table";
    private static final int LEFT_START = 0, LEFT_END = 26;
    private static final int RIGHT_START = 27, RIGHT_END = 53;
    private static final int RECYCLE_BUTTON_SLOT = 22;

    // Tracks all currently open recycling table inventories for reliable identification
    private static final Set<Inventory> activeInventories = new HashSet<>();

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();

        if (e.getClickedBlock() == null) {
            if (RecyclingTableItem.isRecyclingTable(p.getInventory().getItemInMainHand())) {
                Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
                inv.setItem(RECYCLE_BUTTON_SLOT, UiHelpers.makeButton(Material.ANVIL, ChatColor.YELLOW + "Recycle",
                        Collections.singletonList(ChatColor.GRAY + "Click to start processing")));
                activeInventories.add(inv);
                p.openInventory(inv);
                e.setCancelled(true);
                return;
            }
        }

        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.CRAFTING_TABLE && TablePersistence.isRecyclingTableBlock(b)) {
            Inventory inv = TablePersistence.getInventoryForBlock(b);
            if (inv == null) inv = TablePersistence.createInventoryForBlock(b);

            // Ensure the button always exists
            if (inv.getItem(RECYCLE_BUTTON_SLOT) == null ||
                    Objects.requireNonNull(inv.getItem(RECYCLE_BUTTON_SLOT)).getType() == Material.AIR) {
                inv.setItem(RECYCLE_BUTTON_SLOT,
                        UiHelpers.makeButton(Material.ANVIL, ChatColor.YELLOW + "Recycle",
                                Collections.singletonList(ChatColor.GRAY + "Click to start processing")));
            }

            activeInventories.add(inv);
            p.openInventory(inv);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 54) return;

        // Prevent any modification of the recycle button
        if (raw == RECYCLE_BUTTON_SLOT) {
            e.setCancelled(true);
            if (e.getClick().isShiftClick() || e.getCursor() != null) return;

            Player p = (Player) e.getWhoClicked();
            Inventory inv = e.getInventory();

            // Collect items to recycle
            List<ItemStack> inputs = new ArrayList<>();
            for (int i = LEFT_START; i <= LEFT_END; i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && it.getType() != Material.AIR) {
                    inputs.add(it.clone());
                    inv.setItem(i, null);
                }
            }

            if (inputs.isEmpty()) {
                p.sendMessage(ChatColor.RED + "Place items on the left side to recycle.");
                return;
            }

            UUID owner = p.getUniqueId();
            RecycleSession session = new RecycleSession(owner, inputs, inv);
            SessionManager.registerSession(owner, session);
            session.start(RecycleTable.getInstance(), 0);
            p.sendMessage(ChatColor.GREEN + "Recycling started. Outputs will appear on the right.");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;

        // Prevent dragging items over the recycle button slot
        for (int slot : e.getRawSlots()) {
            if (slot == RECYCLE_BUTTON_SLOT) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        activeInventories.remove(e.getInventory());
    }

    /**
     * Returns true if the given inventory is an active recycling table GUI.
     * Uses a tracked Set instead of fragile viewer inspection.
     */
    public static boolean isRecyclingTableInventory(Inventory inv) {
        return inv != null && activeInventories.contains(inv);
    }

    /** Output slots are 27-53 (right side of the GUI). */
    public static boolean isOutputSlot(int slot) {
        return slot >= RIGHT_START && slot <= RIGHT_END;
    }

    /** Input slots are 0-26 (left side of the GUI). */
    public static boolean isInputSlot(int slot) {
        return slot >= LEFT_START && slot <= LEFT_END;
    }

    /** The recycle button slot. */
    public static boolean isControlSlot(int slot) {
        return slot == RECYCLE_BUTTON_SLOT;
    }
}