package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.UUID;

public class TableListener implements Listener {
    public static final String GUI_TITLE = ChatColor.DARK_GREEN + "Recycling Table";

    // Layout: columns 0-3 = input, column 4 = divider, columns 5-8 = output
    // Buttons are in the divider column, moved one row up from the old centre positions.
    public static final int RECYCLE_BUTTON_SLOT  = 13; // row 1, col 4
    public static final int HOLOGRAM_BUTTON_SLOT = 40; // row 4, col 4

    // Tracks all currently open recycling table inventories for reliable identification
    private static final Set<Inventory> activeInventories = new HashSet<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CRAFTING_TABLE || !TablePersistence.isRecyclingTableBlock(b)) return;

        // Respect any cancellation from protection plugins that ran before us
        if (e.isCancelled()) return;

        Player p = e.getPlayer();
        String tableKey = HologramManager.keyFor(b.getLocation());

        // Check ownership / protection plugin access
        if (!TablePersistence.isOwner(b, p) && !ProtectionChecker.isTrusted(p, b.getLocation())) {
            UUID ownerUuid = TablePersistence.getOwner(tableKey);
            String ownerName = ownerUuid != null ? Bukkit.getOfflinePlayer(ownerUuid).getName() : null;
            if (ownerName == null) ownerName = "someone else";
            p.sendMessage(ChatColor.RED + "This Recycling Table belongs to " + ownerName + ".");
            e.setCancelled(true);
            return;
        }
        Inventory inv = TablePersistence.getInventoryForBlock(b);
        if (inv == null) inv = TablePersistence.createInventoryForBlock(b);

        // Always restore the full divider column (buttons + glass panes) so it can't be corrupted
        RecycleSession openSess = SessionManager.getSessionByTableKey(tableKey);
        boolean processing = openSess != null && openSess.isActive();
        inv.setItem(RECYCLE_BUTTON_SLOT, processing ? makeStopButton() : makeRecycleButton());
        inv.setItem(HOLOGRAM_BUTTON_SLOT, makeHologramButton(HologramManager.getMode(tableKey)));
        ItemStack pane = UiHelpers.makeButton(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 54; i++) {
            if (isDividerSlot(i) && !isControlSlot(i)) inv.setItem(i, pane.clone());
        }

        activeInventories.add(inv);
        p.openInventory(inv);
        e.setCancelled(true);

        // Notify the player if overflow items are waiting to be returned
        if (OverflowStorage.hasOverflow(tableKey)) {
            int stacks = OverflowStorage.overflowStackCount(tableKey);
            p.sendMessage(ChatColor.GOLD + "⚠ " + stacks + " overflow stack(s) are waiting — "
                    + "take items from the output area to retrieve them.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;

        int raw = e.getRawSlot();

        // Block shift-clicks from the player's own inventory into the table while processing
        if (raw < 0 || raw >= 54) {
            if (e.getClick().isShiftClick()) {
                String tk = TablePersistence.getKeyForInventory(e.getInventory());
                RecycleSession sess = SessionManager.getSessionByTableKey(tk);
                if (sess != null && sess.isActive()) e.setCancelled(true);
            }
            return;
        }

        // Lock input slots while this table is actively processing
        if (isInputSlot(raw) && !isControlSlot(raw)) {
            String tk = TablePersistence.getKeyForInventory(e.getInventory());
            RecycleSession sess = SessionManager.getSessionByTableKey(tk);
            if (sess != null && sess.isActive()) {
                e.setCancelled(true);
                return;
            }
        }

        // Cancel all clicks on the divider column (glass panes + buttons)
        if (isDividerSlot(raw)) e.setCancelled(true);

        // Hologram cycle button
        if (raw == HOLOGRAM_BUTTON_SLOT) {
            e.setCancelled(true);
            Inventory inv = e.getInventory();
            String tableKey = TablePersistence.getKeyForInventory(inv);
            if (tableKey == null) return;

            HologramManager.cycleMode(tableKey);
            HologramManager.refreshIdle(tableKey); // show new state immediately
            inv.setItem(HOLOGRAM_BUTTON_SLOT, makeHologramButton(HologramManager.getMode(tableKey)));
            return;
        }

        // Recycle / Stop button
        if (raw == RECYCLE_BUTTON_SLOT) {
            e.setCancelled(true);
            if (e.getClick().isShiftClick() || (e.getCursor() != null && e.getCursor().getType() != Material.AIR)) return;

            Player p = (Player) e.getWhoClicked();
            Inventory inv = e.getInventory();
            String tableKey = TablePersistence.getKeyForInventory(inv);

            RecycleSession existing = SessionManager.getSessionByTableKey(tableKey);

            // ── STOP ────────────────────────────────────────────────────────
            if (existing != null && existing.isActive()) {
                existing.stop();
                inv.setItem(RECYCLE_BUTTON_SLOT, makeRecycleButton());
                p.sendMessage(ChatColor.YELLOW + "Recycling stopped. The current item will restart at full time.");
                return;
            }

            // ── START ────────────────────────────────────────────────────────
            List<ItemStack> inputs = new ArrayList<>();
            for (int i = 0; i < 54; i++) {
                if (!isInputSlot(i)) continue;
                ItemStack it = inv.getItem(i);
                if (it != null && it.getType() != Material.AIR) {
                    inputs.add(it.clone());
                }
            }

            if (inputs.isEmpty()) {
                p.sendMessage(ChatColor.RED + "Place items on the left side to recycle.");
                return;
            }

            UUID owner = p.getUniqueId();
            RecycleSession session = new RecycleSession(owner, inputs, inv);
            session.setTableKey(tableKey);

            SessionManager.registerSession(owner, session);
            session.start(RecycleTable.getInstance(), 0);
            // In dev mode start() finishes synchronously, so check active state before overwriting
            if (session.isActive()) inv.setItem(RECYCLE_BUTTON_SLOT, makeStopButton());
            p.sendMessage(ChatColor.GREEN + "Recycling started. Outputs will appear on the right.");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;

        // Prevent dragging items over any divider column slot (buttons or glass panes)
        for (int slot : e.getRawSlots()) {
            if (isDividerSlot(slot)) {
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

    /** Input slots: columns 0–3, all 6 rows (24 slots on the left side). */
    public static boolean isInputSlot(int slot) {
        return slot >= 0 && slot <= 53 && (slot % 9) <= 3;
    }

    /** Output slots: columns 5–8, all 6 rows (24 slots on the right side). */
    public static boolean isOutputSlot(int slot) {
        return slot >= 0 && slot <= 53 && (slot % 9) >= 5;
    }

    /** Divider column: column 4, all 6 rows — contains buttons and glass panes. */
    public static boolean isDividerSlot(int slot) {
        return slot >= 0 && slot <= 53 && (slot % 9) == 4;
    }

    /** Control slots: the two interactive buttons in the divider column. */
    public static boolean isControlSlot(int slot) {
        return slot == RECYCLE_BUTTON_SLOT || slot == HOLOGRAM_BUTTON_SLOT;
    }

    public static ItemStack makeRecycleButton() {
        return UiHelpers.makeButton(Material.ANVIL,
                ChatColor.YELLOW + "Recycle",
                Collections.singletonList(ChatColor.GRAY + "Click to start processing"));
    }

    public static ItemStack makeStopButton() {
        return UiHelpers.makeButton(Material.BARRIER,
                ChatColor.RED + "Stop Recycling",
                Collections.singletonList(ChatColor.GRAY + "Click to stop — current item restarts at full time"));
    }

    /** Called by RecycleSession when a session ends (finished or stopped). */
    public static void refreshRecycleButton(Inventory inv, boolean processing) {
        if (inv == null) return;
        inv.setItem(RECYCLE_BUTTON_SLOT, processing ? makeStopButton() : makeRecycleButton());
    }

    /** Creates the hologram-cycle button with the current mode shown in the name. */
    private static ItemStack makeHologramButton(HologramManager.HologramMode mode) {
        String modeName;
        switch (mode) {
            case CURRENT_ITEM: modeName = ChatColor.GREEN  + "Current Item";  break;
            case ALL_ITEMS:    modeName = ChatColor.YELLOW + "Full Queue";    break;
            case OFF:          modeName = ChatColor.RED    + "Off";           break;
            default:           modeName = ChatColor.GRAY   + "Unknown";       break;
        }
        return UiHelpers.makeButton(
            Material.ENDER_EYE,
            ChatColor.AQUA + "Hologram: " + modeName,
            Arrays.asList(ChatColor.GRAY + "Click to change display mode")
        );
    }
}