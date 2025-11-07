package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles item-by-item recycling with full offline-time compensation,
 * ordered queue processing, and integrated overflow management.
 */
public class RecycleSession {
    private final UUID owner;
    private final Queue<ItemStack> queue = new ConcurrentLinkedQueue<>();
    private final Inventory guiInventory;

    private volatile boolean active = false;
    private long timeLeftTicks = 0L;
    private int currentComplexity = 1;
    private double progress = 0.0;
    private long lastActiveTime;

    public RecycleSession(UUID owner, List<ItemStack> inputs, Inventory guiInventory) {
        this.owner = owner;
        this.guiInventory = guiInventory;
        this.lastActiveTime = System.currentTimeMillis();

        // Preserve left-to-right, top-to-bottom order
        for (ItemStack is : inputs) {
            int amt = Math.max(1, is.getAmount());
            for (int i = 0; i < amt; i++) {
                ItemStack single = is.clone();
                single.setAmount(1);
                queue.add(single);
            }
        }
    }

    public RecycleSession(UUID owner, List<ItemStack> queuedItems, Inventory guiInventory, long lastActiveTime) {
        this.owner = owner;
        this.guiInventory = guiInventory;
        this.lastActiveTime = lastActiveTime;
        if (queuedItems != null) queue.addAll(queuedItems);
    }

    public boolean isActive() { return active; }
    public long getTimeLeft() { return timeLeftTicks; }
    public double getProgress() { return progress; }
    public List<ItemStack> serializeItems() { return new ArrayList<>(queue); }

    public long getLastActiveTime() { return lastActiveTime; }

    /**
     * Start or resume a session, applying offline progress.
     */
    public void start(JavaPlugin plugin, long offlineSeconds) {
        if (queue.isEmpty()) {
            finish();
            return;
        }

        // Apply offline progress to queued items
        while (!queue.isEmpty() && offlineSeconds > 0) {
            ItemStack peek = queue.peek();
            int complexity = Math.max(1, Math.min(250, ComplexityCalculator.calculateComplexity(peek)));
            long totalSeconds = ComplexityCalculator.mapScoreToSeconds(complexity);
            totalSeconds = (long) Math.ceil(totalSeconds / ConfigManager.getSpeedMultiplier());

            if (offlineSeconds >= totalSeconds) {
                processSingleItem(peek);
                queue.poll(); // remove processed item
                offlineSeconds -= totalSeconds;
            } else {
                // Partially processed
                startProcessing(plugin, peek, totalSeconds - offlineSeconds);
                offlineSeconds = 0;
                return;
            }
        }

        // If all items finished offline, mark session finished
        if (queue.isEmpty()) {
            finish();
        } else {
            startProcessing(plugin, queue.poll(), 0);
        }
    }

    /**
     * Resumes a paused or loaded recycling session after restart.
     * Called by RecycleTable.onEnable() when restoring sessions.
     */
    public void resumeTask(JavaPlugin plugin) {
        if (!active && !queue.isEmpty()) {
            start(plugin, 0L);
        }
    }

    private void startProcessing(JavaPlugin plugin, ItemStack item, long remainingSeconds) {
        active = true;
        currentComplexity = Math.max(1, Math.min(250, ComplexityCalculator.calculateComplexity(item)));
        long totalSeconds = ComplexityCalculator.mapScoreToSeconds(currentComplexity);
        totalSeconds = (long) Math.ceil(totalSeconds / ConfigManager.getSpeedMultiplier());

        long secondsToRun = (remainingSeconds > 0 ? remainingSeconds : totalSeconds);
        this.timeLeftTicks = secondsToRun * 20L;
        this.progress = 1.0 - (secondsToRun / (double) totalSeconds);
        this.lastActiveTime = System.currentTimeMillis();

        final long total = totalSeconds;
        final long[] remaining = { timeLeftTicks };

        new BukkitRunnable() {
            @Override
            public void run() {
                if (remaining[0] <= 0) {
                    processSingleItem(item);
                    this.cancel();
                    active = false;

                    if (!queue.isEmpty()) startProcessing(plugin, queue.poll(), 0);
                    else finish();
                    return;
                }

                remaining[0] -= 20L;
                timeLeftTicks = remaining[0];
                progress = 1.0 - (remaining[0] / (double) (total * 20L));
                lastActiveTime = System.currentTimeMillis();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Processes one item into raw materials and deposits them.
     * If output inventory is full, results go into OverflowStorage.
     */
    private void processSingleItem(ItemStack item) {
        List<ItemStack> decomposed = RecipeManager.decomposeToRaw(item);
        double durabilityFactor = CompatibilityUtils.getDurabilityFactor(item);
        Map<Material, Integer> aggregated = new HashMap<>();

        for (ItemStack raw : decomposed) {
            int baseAmt = raw.getAmount();
            double scaled = baseAmt * durabilityFactor;
            int floored = (int) Math.floor(scaled);
            if (floored > 0) aggregated.merge(raw.getType(), floored, Integer::sum);
        }

        if (aggregated.isEmpty()) {
            Map<Material, Integer> ratio = new HashMap<>();
            int total = 0;
            for (ItemStack raw : decomposed) {
                ratio.merge(raw.getType(), raw.getAmount(), Integer::sum);
                total += raw.getAmount();
            }
            double r = Math.random(), cumulative = 0.0;
            for (Map.Entry<Material, Integer> e : ratio.entrySet()) {
                cumulative += e.getValue() / (double) total;
                if (r <= cumulative) { aggregated.put(e.getKey(), 1); break; }
            }
            if (aggregated.isEmpty() && !ratio.isEmpty()) aggregated.put(ratio.keySet().iterator().next(), 1);
        }

        // --- Attempt to place into output slots (27–53)
        Player pl = Bukkit.getPlayer(owner);
        Map<Material, Integer> overflow = new HashMap<>();

        for (Map.Entry<Material, Integer> out : aggregated.entrySet()) {
            int remaining = out.getValue();
            for (int i = 27; i <= 53 && remaining > 0; i++) {
                ItemStack slot = guiInventory.getItem(i);
                if (slot == null || slot.getType() == Material.AIR) {
                    int place = Math.min(remaining, out.getKey().getMaxStackSize());
                    guiInventory.setItem(i, new ItemStack(out.getKey(), place));
                    remaining -= place;
                } else if (slot.getType() == out.getKey() && slot.getAmount() < slot.getMaxStackSize()) {
                    int canAdd = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
                    slot.setAmount(slot.getAmount() + canAdd);
                    guiInventory.setItem(i, slot);
                    remaining -= canAdd;
                }
            }

            // Anything left becomes overflow
            if (remaining > 0) overflow.put(out.getKey(), remaining);
        }

        // --- NEW: Save remaining items into OverflowStorage ---
        if (!overflow.isEmpty()) {
            for (Map.Entry<Material, Integer> e : overflow.entrySet()) {
                OverflowStorage.addItem(owner, new ItemStack(e.getKey(), e.getValue()));
            }
            if (pl != null)
                pl.sendMessage(ChatColor.YELLOW + "Output full — excess items saved to overflow storage.");
        }

        // --- Enchantment book recovery ---
        if (ConfigManager.enchantmentsEnabled() && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            Map<org.bukkit.enchantments.Enchantment, Integer> returned = EnchantUtils.getReturnedEnchantments(item);
            List<ItemStack> books = EnchantUtils.generateEnchantmentBooks(returned);
            for (ItemStack book : books) {
                boolean placed = false;
                for (int i = 27; i <= 53; i++) {
                    ItemStack slot = guiInventory.getItem(i);
                    if (slot == null || slot.getType() == Material.AIR) {
                        guiInventory.setItem(i, book);
                        placed = true;
                        break;
                    }
                }
                if (!placed) OverflowStorage.addItem(owner, book);
            }
        }

        if (pl != null)
            pl.sendMessage(ChatColor.GREEN + "Processed 1x " + item.getType().name());
    }

    private void finish() {
        active = false;
        timeLeftTicks = 0;
        progress = 1.0;
        Player pl = Bukkit.getPlayer(owner);
        if (pl != null)
            pl.sendMessage(ChatColor.GREEN + "Recycling session completed.");
    }
}
