package me.mizfit.recycletable;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
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
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!TablePersistence.isRecyclingTableBlock(b)) return;
        b.getWorld().dropItemNaturally(b.getLocation(), RecyclingTableItem.createItem());
        TablePersistence.unregisterBlock(b);
    }
}