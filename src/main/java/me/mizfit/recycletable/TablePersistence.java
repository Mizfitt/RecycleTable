package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles creation, storage, and persistence of Recycling Table block inventories.
 */
public class TablePersistence {
    // Keeps track of all placed recycling tables by their block location
    private static final Map<String, Inventory> inventoryMap = new HashMap<>();
    private static final Map<String, Long>      placedAt     = new HashMap<>();
    private static final Map<String, UUID>      ownerMap     = new HashMap<>();
    // Reverse lookup so TableListener can find the key from an open inventory
    private static final Map<Inventory, String> invToKey     = new HashMap<>();

    /**
     * Generates a unique string key for a given block location.
     */
    private static String keyFor(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            throw new IllegalArgumentException("Tried to generate key for null or invalid location!");
        }
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }


    /**
     * Creates a new 54-slot inventory for a placed recycling table block.
     */
    public static Inventory createInventoryForBlock(Block b) {
        String key = keyFor(b.getLocation());
        Inventory inv = Bukkit.createInventory(null, 54, TableListener.GUI_TITLE);
        inventoryMap.put(key, inv);
        invToKey.put(inv, key);
        placedAt.put(key, System.currentTimeMillis());
        return inv;
    }

    /**
     * Registers an already-created inventory for a block.
     */
    public static void registerInventoryForBlock(Block b, Inventory inv) {
        String key = keyFor(b.getLocation());
        inventoryMap.put(key, inv);
        invToKey.put(inv, key);
        placedAt.put(key, System.currentTimeMillis());
    }

    /**
     * Checks if a block is a registered recycling table.
     */
    public static boolean isRecyclingTableBlock(Block b) {
        return inventoryMap.containsKey(keyFor(b.getLocation()));
    }

    /**
     * Gets the inventory linked to a recycling table block.
     */
    public static Inventory getInventoryForBlock(Block b) {
        return inventoryMap.get(keyFor(b.getLocation()));
    }

    /**
     * Removes a recycling table block and its stored inventory.
     */
    public static void unregisterBlock(Block b) {
        String key = keyFor(b.getLocation());
        Inventory inv = inventoryMap.remove(key);
        if (inv != null) invToKey.remove(inv);
        placedAt.remove(key);
        ownerMap.remove(key);
    }

    /** Records the owner of a placed recycling table. */
    public static void setOwner(Block b, UUID uuid) {
        ownerMap.put(keyFor(b.getLocation()), uuid);
    }

    /** Returns the UUID of whoever placed this table, or null if unknown. */
    public static UUID getOwner(String key) {
        return ownerMap.get(key);
    }

    /** Returns true if the given player is the owner of the table at this block. */
    public static boolean isOwner(Block b, org.bukkit.entity.Player p) {
        UUID owner = ownerMap.get(keyFor(b.getLocation()));
        return owner != null && owner.equals(p.getUniqueId());
    }

    /**
     * Returns the "world:x:y:z" key for the given inventory, or null if it is not a placed table.
     * Used by TableListener to link a RecycleSession to its hologram.
     */
    public static String getKeyForInventory(Inventory inv) {
        return invToKey.get(inv);
    }

    /**
     * Parses a "world:x:y:z" key back into a Bukkit Location.
     * Returns null if the world is not loaded or the key is malformed.
     */
    public static Location getLocationForKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Saves all placed recycling table inventories to a YAML file.
     */
    public static void savePlacedTables(RecycleTable plugin) {
        try {
            File file = new File(plugin.getDataFolder(), "placed_tables.yml");
            YamlConfiguration yaml = new YamlConfiguration();

            for (Map.Entry<String, Inventory> entry : inventoryMap.entrySet()) {
                String key = entry.getKey();
                Inventory inv = entry.getValue();

                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null) {
                        yaml.set(key + ".items." + i, item);
                    }
                }

                yaml.set(key + ".placedAt", placedAt.getOrDefault(key, System.currentTimeMillis()));
                UUID owner = ownerMap.get(key);
                if (owner != null) yaml.set(key + ".owner", owner.toString());
            }

            yaml.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Loads previously saved placed tables from placed_tables.yml.
     */
    public static void loadPlacedTables(RecycleTable plugin) {
        try {
            File f = new File(plugin.getDataFolder(), "placed_tables.yml");
            if (!f.exists()) return;

            YamlConfiguration yc = YamlConfiguration.loadConfiguration(f);
            Set<String> keys = yc.getKeys(false);
            if (keys.isEmpty()) return;

            for (String key : keys) {
                Inventory inv = Bukkit.createInventory(null, 54, TableListener.GUI_TITLE);

                if (yc.isConfigurationSection(key + ".items")) {
                    org.bukkit.configuration.ConfigurationSection section = yc.getConfigurationSection(key + ".items");
                    if (section != null) {
                        for (String idx : section.getKeys(false)) {
                            ItemStack it = yc.getItemStack(key + ".items." + idx);
                            if (it != null) inv.setItem(Integer.parseInt(idx), it);
                        }
                    }
                }

                inventoryMap.put(key, inv);
                invToKey.put(inv, key);
                long placed = yc.getLong(key + ".placedAt", System.currentTimeMillis());
                placedAt.put(key, placed);

                String ownerStr = yc.getString(key + ".owner");
                if (ownerStr != null) {
                    try { ownerMap.put(key, UUID.fromString(ownerStr)); } catch (Exception ignored) {}
                }

                // Spawn hologram above this table
                Location loc = getLocationForKey(key);
                if (loc != null) HologramManager.spawnHologram(loc);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }}


