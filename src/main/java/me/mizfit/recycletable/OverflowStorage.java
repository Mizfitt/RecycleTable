package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Global overflow storage system for each player.
 * Stores excess recycled items when output slots are full,
 * and automatically refills when space becomes available.
 */
public class OverflowStorage {
    private static final Map<UUID, List<ItemStack>> storage = new HashMap<>();
    private static File file;
    private static YamlConfiguration yaml;
    private static long repopulateDelayTicks;

    public static void initialize(File dataFolder) {
        file = new File(dataFolder, "overflow.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        repopulateDelayTicks = (long) (RecycleTable.getInstance()
                .getConfig().getDouble("overflow.repopulate-delay", 3.0) * 20L);
        load();
    }

    public static void addItem(UUID uuid, ItemStack item) {
        List<ItemStack> items = storage.computeIfAbsent(uuid, k -> new ArrayList<>());
        mergeIntoList(items, item);
        save();
    }

    private static void mergeIntoList(List<ItemStack> list, ItemStack add) {
        for (ItemStack i : list) {
            if (i.isSimilar(add) && i.getAmount() < i.getMaxStackSize()) {
                int canAdd = Math.min(add.getAmount(), i.getMaxStackSize() - i.getAmount());
                i.setAmount(i.getAmount() + canAdd);
                add.setAmount(add.getAmount() - canAdd);
                if (add.getAmount() <= 0) return;
            }
        }
        list.add(add);
    }

    public static void save() {
        try {
            yaml.set("overflow", null);
            for (Map.Entry<UUID, List<ItemStack>> e : storage.entrySet()) {
                yaml.set("overflow." + e.getKey(), e.getValue());
            }
            yaml.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void load() {
        if (yaml.contains("overflow")) {
            for (String key : yaml.getConfigurationSection("overflow").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> list = (List<ItemStack>) yaml.get("overflow." + key);
                    if (list != null) storage.put(id, list);
                } catch (Exception ex) {
                    Bukkit.getLogger().warning("[OverflowStorage] Failed to load entry for: " + key);
                }
            }
        }
    }

    public static void tryRepopulate(Player player, Inventory recyclerInv) {
        UUID id = player.getUniqueId();
        if (!storage.containsKey(id)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                List<ItemStack> overflowItems = storage.get(id);
                if (overflowItems == null || overflowItems.isEmpty()) return;

                Iterator<ItemStack> iterator = overflowItems.iterator();
                while (iterator.hasNext()) {
                    ItemStack next = iterator.next();
                    boolean placed = false;

                    for (int i = 27; i <= 53; i++) {
                        ItemStack slot = recyclerInv.getItem(i);
                        if (slot == null || slot.getType() == Material.AIR) {
                            recyclerInv.setItem(i, next);
                            iterator.remove();
                            placed = true;
                            break;
                        } else if (slot.isSimilar(next) && slot.getAmount() < slot.getMaxStackSize()) {
                            int canAdd = Math.min(next.getAmount(), slot.getMaxStackSize() - slot.getAmount());
                            slot.setAmount(slot.getAmount() + canAdd);
                            next.setAmount(next.getAmount() - canAdd);
                            if (next.getAmount() <= 0) {
                                iterator.remove();
                                placed = true;
                                break;
                            }
                        }
                    }

                    if (!placed) break; // output full again
                }

                if (overflowItems.isEmpty()) {
                    storage.remove(id);
                    player.sendMessage(ChatColor.GREEN + "All overflow items have been returned.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Repopulated available slots from overflow.");
                }

                save();
            }
        }.runTaskLater(RecycleTable.getInstance(), repopulateDelayTicks);
    }

    public static void clear(UUID uuid) {
        storage.remove(uuid);
        save();
    }

    public static List<ItemStack> getItems(UUID uuid) {
        return storage.getOrDefault(uuid, Collections.emptyList());
    }
}
