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
    // Tracks which table keys already have a repopulate task queued (prevents stacking)
    private static final Set<String> pendingRepopulate = new HashSet<>();

    private static File file;
    private static YamlConfiguration yaml;
    private static long repopulateDelayTicks;
    private static int maxOverflowItems;

    // Table keys use "world:x:y:z" — colons must be escaped for YAML path separators (dots).
    // We replace : with | which is safe in YAML keys.
    private static String toSafeKey(String tableKey)  { return tableKey.replace(":", "|"); }
    private static String fromSafeKey(String safeKey) { return safeKey.replace("|", ":"); }

    public static void initialize(File dataFolder) {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        file = new File(dataFolder, "overflow.yml");
        yaml = YamlConfiguration.loadConfiguration(file);

        repopulateDelayTicks = (long) (RecycleTable.getInstance()
                .getConfig().getDouble("overflow.repopulate-delay", 3.0) * 20L);
        maxOverflowItems = RecycleTable.getInstance()
                .getConfig().getInt("overflow.max-items", 500);

        load();
    }

    /**
     * Adds a single item to overflow storage and saves immediately.
     * Prefer {@link #addItems(String, List)} when adding multiple items at once.
     */
    public static void addItem(String tableKey, ItemStack item) {
        if (tableKey == null || item == null || item.getAmount() <= 0) return;
        List<ItemStack> list = storage.computeIfAbsent(tableKey, k -> new ArrayList<>());
        if (totalItemCount(list) >= maxOverflowItems) {
            Bukkit.getLogger().warning("[RecycleTable] Overflow cap (" + maxOverflowItems +
                    ") reached for " + tableKey + " — item could not be stored.");
            return;
        }
        mergeIntoList(list, item);
        save();
    }

    /**
     * Adds a batch of items to overflow storage, saving only once at the end.
     * Use this instead of calling addItem() in a loop to avoid repeated disk writes.
     */
    public static void addItems(String tableKey, List<ItemStack> items) {
        if (tableKey == null || items == null || items.isEmpty()) return;
        List<ItemStack> list = storage.computeIfAbsent(tableKey, k -> new ArrayList<>());
        boolean capped = false;
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            if (totalItemCount(list) >= maxOverflowItems) {
                capped = true;
                break;
            }
            mergeIntoList(list, item);
        }
        if (capped) {
            Bukkit.getLogger().warning("[RecycleTable] Overflow cap (" + maxOverflowItems +
                    ") reached for " + tableKey + " — some items could not be stored.");
        }
        save();
    }

    /** Returns true if this table has any items waiting in overflow. */
    public static boolean hasOverflow(String tableKey) {
        List<ItemStack> items = storage.get(tableKey);
        return items != null && !items.isEmpty();
    }

    /** Returns the number of distinct stacks sitting in overflow for this table. */
    public static int overflowStackCount(String tableKey) {
        List<ItemStack> items = storage.get(tableKey);
        return items == null ? 0 : items.size();
    }

    /** Returns the total number of individual items (sum of all stack sizes) in a list. */
    private static int totalItemCount(List<ItemStack> list) {
        int total = 0;
        for (ItemStack i : list) if (i != null) total += i.getAmount();
        return total;
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
            // Wipe both sections so stale or legacy data doesn't linger
            yaml.set("overflow", null);
            yaml.set("overflow-keys", null); // remove legacy section from old format

            for (Map.Entry<String, List<ItemStack>> e : storage.entrySet()) {
                // Escape colons so YAML doesn't interpret "world:x:y:z" as nested keys
                String safeKey = toSafeKey(e.getKey());
                yaml.set("overflow." + safeKey, e.getValue());
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
                // Restore the original "world:x:y:z" key by reversing the escape
                String tableKey = fromSafeKey(safeKey);
                List<ItemStack> list = (List<ItemStack>) yaml.get("overflow." + safeKey);
                if (list != null) storage.put(tableKey, list);
            } catch (Exception ex) {
                Bukkit.getLogger().warning("[OverflowStorage] Failed to load overflow for: " + safeKey);
            }
        }
    }

    /**
     * Attempts to push overflow items back into the table's output slots.
     * Only one repopulate task is ever scheduled per table at a time — rapid
     * item-take clicks won't stack up duplicate tasks.
     * The player parameter is used only for feedback messages.
     */
    public static void tryRepopulate(String tableKey, Inventory recyclerInv, Player player) {
        if (tableKey == null || !storage.containsKey(tableKey)) return;

        // De-duplicate: if a task is already pending for this table, skip
        if (pendingRepopulate.contains(tableKey)) return;
        pendingRepopulate.add(tableKey);

        new BukkitRunnable() {
            @Override
            public void run() {
                pendingRepopulate.remove(tableKey);

                List<ItemStack> overflowItems = storage.get(tableKey);
                if (overflowItems == null || overflowItems.isEmpty()) return;

                // Work on a snapshot so concurrent access can't cause ConcurrentModificationException.
                // Clear the live list now; we'll add back anything that doesn't fit.
                List<ItemStack> snapshot = new ArrayList<>(overflowItems);
                overflowItems.clear();

                Iterator<ItemStack> iterator = snapshot.iterator();
                while (iterator.hasNext()) {
                    ItemStack next = iterator.next();
                    boolean placed = false;

                    for (int i = 27; i <= 53; i++) {
                        if (!TableListener.isOutputSlot(i)) continue;
                        ItemStack slot = recyclerInv.getItem(i);
                        if (slot == null || slot.getType() == Material.AIR) {
                            recyclerInv.setItem(i, next.clone());
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

                    if (!placed) break; // output still full; stop trying
                }

                // Anything left in snapshot couldn't fit — put it back into the live list
                overflowItems.addAll(snapshot);

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
