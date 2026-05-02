package me.mizfit.recycletable;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;

public class PlaceListener implements Listener {

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!RecyclingTableItem.isRecyclingTable(e.getItemInHand())) return;
        Block b = e.getBlockPlaced();
        Inventory inv = TablePersistence.createInventoryForBlock(b);
        TablePersistence.registerInventoryForBlock(b, inv);
        TablePersistence.setOwner(b, e.getPlayer().getUniqueId());
        HologramManager.spawnHologram(b.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!TablePersistence.isRecyclingTableBlock(b)) return;

        // Respect any earlier protection plugin cancellation
        if (e.isCancelled()) return;

        Player p = e.getPlayer();

        // Only the owner can break their own table — claim trust does not apply here
        if (!TablePersistence.isOwner(b, p)) {
            e.setCancelled(true);
            String key = HologramManager.keyFor(b.getLocation());
            java.util.UUID ownerUuid = TablePersistence.getOwner(key);
            String ownerName = ownerUuid != null
                    ? org.bukkit.Bukkit.getOfflinePlayer(ownerUuid).getName()
                    : "someone else";
            if (ownerName == null) ownerName = "someone else";
            p.sendMessage(ChatColor.RED + "This Recycling Table belongs to " + ownerName + ".");
            return;
        }

        b.getWorld().dropItemNaturally(b.getLocation(), RecyclingTableItem.createItem());
        HologramManager.removeHologram(HologramManager.keyFor(b.getLocation()));
        TablePersistence.unregisterBlock(b);
    }
}
