package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TableListener implements Listener {
    public static final String GUI_TITLE = ChatColor.DARK_GREEN + "Recycling Table";

    // Left 0..26, Right 27..53, Button 22
    private static final int LEFT_START = 0, LEFT_END = 26;
    private static final int RIGHT_START = 27, RIGHT_END = 53;
    private static final int RECYCLE_BUTTON_SLOT = 22;

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();

        // Using the item in air
        if (e.getClickedBlock() == null && p.getInventory().getItemInMainHand() != null && RecyclingTableItem.isRecyclingTable(p.getInventory().getItemInMainHand())) {
            Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
            inv.setItem(RECYCLE_BUTTON_SLOT, UiHelpers.makeButton(Material.ANVIL, ChatColor.YELLOW + "Recycle", Collections.singletonList(ChatColor.GRAY + "Click to start processing")));
            p.openInventory(inv);
            e.setCancelled(true);
            return;
        }

        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.CRAFTING_TABLE && TablePersistence.isRecyclingTableBlock(b)) {
            Inventory inv = TablePersistence.getInventoryForBlock(b);
            if (inv == null) inv = TablePersistence.createInventoryForBlock(b);
            p.openInventory(inv);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView() == null || e.getView().getTitle() == null) return;
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;

        // Allow normal interaction by default; we only intercept the recycle button
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 54) return; // player inventory

        // If this inventory is a temporary one (from item), it will have a button at slot 22
        if (raw == RECYCLE_BUTTON_SLOT) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            Inventory inv = e.getInventory();

            // Collect inputs from left area
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

            // Create and register a session bound to player UUID
            UUID owner = p.getUniqueId();
            RecycleSession session = new RecycleSession(owner, inputs, inv);
            SessionManager.registerSession(owner, session);
            session.start(RecycleTable.getInstance());
            p.sendMessage(ChatColor.GREEN + "Recycling started. Outputs will appear on the right.");
        }
        // Otherwise, allow taking from right and placing into left: do not cancel
    }

    public static boolean isRecyclingTableInventory(Inventory inv) {
        if (inv == null) return false;
        if (inv.getViewers().isEmpty()) return false;
        return GUI_TITLE.equals(inv.getViewers().get(0).getOpenInventory().getTitle());
    }


    public static boolean isInputSlot(int slot) { return slot >= LEFT_START && slot <= LEFT_END; }
    public static boolean isOutputSlot(int slot) { return slot >= RIGHT_START && slot <= RIGHT_END; }
    public static boolean isControlSlot(int slot) { return slot == RECYCLE_BUTTON_SLOT; }
}

