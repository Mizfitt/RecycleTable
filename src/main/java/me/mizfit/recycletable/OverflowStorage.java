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
 * Stores overflow items per table (keyed by "world:x:y:z") rather than per player.
 * This ensures items are always returned to the correct table regardless of who
 * opened it or triggered the overflow.
 */
public class OverflowStorage {
    private static final Map<String, List<ItemStack>> storage = new HashMap<>();
    private static File file;
    private static YamlConfiguration yaml;
    private static long repopulateDelayTicks;

    public static void initialize(File dataFolder) {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        file = new File(dataFolder, "overflow.yml");
        yaml = YamlConfiguration.loadConfiguration(file);

        repopulateDelayTicks = (long) (RecycleTable.getInstance()
                .getConfig().getDouble("overflow.repopulate-delay", 3.0) * 20L);

        load();
    }

    public static void addItem(String tableKey, ItemStack item) {
        if (tableKey == null || item == null || item.getAmount() <= 0) return;
        List<ItemStack> items = storage.computeIfAbsent(tableKey, k -> new ArrayList<>());
        mergeIntoList(items, item);
        save();
    }

    private static void mergeIntoList(List<ItemStack> list, ItemStack add) {
        if (add == null || add.getAmount() <= 0) return;

        ItemStack remaining = add.clone();
        for (ItemStack i : list) {
            if (!i.isSimilar(remaining) || i.getAmount() >= i.getMaxStackSize()) continue;
            int canAdd = Math.min(remaining.getAmount(), i.getMaxStackSize() - i.getAmount());
            i.setAmount(i.getAmount() + canAdd);
            remaining.setAmount(remaining.getAmount() - canAdd);
            if (remaining.getAmount() <= 0) return;
        }
        while (remaining.getAmount() > 0) {
            int split = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack stack = remaining.clone();
            stack.setAmount(split);
            list.add(stack);
            remaining.setAmount(remaining.getAmount() - split);
        }
    }

    public static void save() {
        try {
            yaml.set("overflow", null);
            for (Map.Entry<String, List<ItemStack>> e : storage.entrySet()) {
                // Replace colons in the key with | so YAML path separators aren't confused
                String safeKey = e.getKey().replace(".", "_");
                yaml.set("overflow." + safeKey, e.getValue());
                // Store the original key separately so we can restore it on load
                yaml.set("overflow-keys." + safeKey, e.getKey());
            }
            yaml.save(file);
        } catch (IOException ex) {
            Bukkit.getLogger().severe("[OverflowStorage] Failed to save overflow.yml");
            ex.printStackTrace();
        }
    }

    public static void load() {
        storage.clear();
        if (!yaml.contains("overflow")) return;
        org.bukkit.configuration.ConfigurationSection section =
                yaml.getConfigurationSection("overflow");
        if (section == null) return;

        for (String safeKey : section.getKeys(false)) {
            try {
                // Retrieve the original table key
                String tableKey = yaml.getString("overflow-keys." + safeKey, safeKey);
                List<ItemStack> list = (List<ItemStack>) yaml.get("overflow." + safeKey);
                if (list != null) storage.put(tableKey, list);
            } catch (Exception ex) {
                Bukkit.getLogger().warning("[OverflowStorage] Failed to load overflow for: " + safeKey);
            }
        }
    }

    /**
     * Attempts to push overflow items back into the table's output slots.
     * The player parameter is used only for feedback messages.
     */
    public static void tryRepopulate(String tableKey, Inventory recyclerInv, Player player) {
        if (tableKey == null || !storage.containsKey(tableKey)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                List<ItemStack> overflowItems = storage.get(tableKey);
                if (overflowItems == null || overflowItems.isEmpty()) return;

                Iterator<ItemStack> iterator = overflowItems.iterator();
                while (iterator.hasNext()) {
                    ItemStack next = iterator.next();
                    boolean placed = false;

                    for (int i = 27; i <= 53; i++) {
                        if (!TableListener.isOutputSlot(i)) continue;
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

                    if (!placed) break;
                }

                if (overflowItems.isEmpty()) {
                    storage.remove(tableKey);
                    if (player != null && player.isOnline())
                        player.sendMessage(ChatColor.GREEN + "All overflow items have been returned!");
                } else {
                    if (player != null && player.isOnline())
                        player.sendMessage(ChatColor.YELLOW + "Repopulated available slots from overflow.");
                }

                save();
            }
        }.runTaskLater(RecycleTable.getInstance(), repopulateDelayTicks);
    }
}
