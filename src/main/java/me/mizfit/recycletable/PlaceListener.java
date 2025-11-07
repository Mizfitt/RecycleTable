package me.mizfit.recycletable;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class PlaceListener implements Listener {
    private static final NamespacedKey PDC_KEY = new NamespacedKey(RecycleTable.getInstance(), "recycling_table");

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!RecyclingTableItem.isRecyclingTable(e.getItemInHand())) return;
        Block b = e.getBlockPlaced();

        // Only tile entities (e.g. chests, signs, furnaces) have PDC
        if (b.getState() instanceof org.bukkit.block.TileState) {
            org.bukkit.block.TileState tile = (org.bukkit.block.TileState) b.getState();
            tile.getPersistentDataContainer().set(
                    PDC_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
            tile.update(true);
        }

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
