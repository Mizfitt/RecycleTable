package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Handles item-by-item recycling with full offline-time compensation,
 * ordered queue processing, integrated overflow management,
 * and automatic analytics tracking.
 */
public class RecycleSession {
    private final UUID owner;
    private final LinkedList<ItemStack> queue = new LinkedList<>();
    private final Inventory guiInventory;

    private volatile boolean active = false;
    private long timeLeftTicks = 0L;
    private int currentComplexity = 1;
    private double progress = 0.0;
    private long lastActiveTime;

    /** The item currently being processed (used by HologramManager). */
    private ItemStack currentItem = null;
    /** Key of the placed table block this session is attached to. */
    private String tableKey = null;
    /** Handle to the running timer so it can be cancelled on stop. */
    private BukkitTask currentTask = null;

    public RecycleSession(UUID owner, List<ItemStack> inputs, Inventory guiInventory) {
        this.owner = owner;
        this.guiInventory = guiInventory;
        this.lastActiveTime = System.currentTimeMillis();

        // Queue full stacks — time and output are scaled by amount in processing
        for (ItemStack is : inputs) {
            if (is != null && is.getType() != Material.AIR && is.getAmount() > 0) {
                queue.add(is.clone());
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

    /** Returns the item currently being processed, or null if idle. */
    public ItemStack getCurrentItem() { return currentItem; }

    /** Returns a snapshot of items waiting in the queue (excludes the current item). */
    public List<ItemStack> getQueuedItems() { return new ArrayList<>(queue); }

    /** Links this session to a placed table block so its hologram and overflow can be updated. */
    public void setTableKey(String key) { this.tableKey = key; }
    public String getTableKey() { return tableKey; }

    /**
     * Start or resume a session, applying offline progress.
     */
    public void start(JavaPlugin plugin, long offlineSeconds) {
        if (queue.isEmpty()) {
            finish();
            return;
        }

        while (!queue.isEmpty() && offlineSeconds > 0) {
            ItemStack peek = queue.peek();
            int complexity = Math.max(1, Math.min(250, ComplexityCalculator.calculateComplexity(peek)));
            long totalSeconds = ComplexityCalculator.mapScoreToSeconds(complexity);
            totalSeconds = (long) Math.ceil(totalSeconds / ConfigManager.getSpeedMultiplier());
            totalSeconds *= peek.getAmount(); // scale time by stack size

            if (offlineSeconds >= totalSeconds) {
                processSingleItem(peek);
                queue.poll();
                offlineSeconds -= totalSeconds;
            } else {
                startProcessing(plugin, peek, totalSeconds - offlineSeconds);
                offlineSeconds = 0;
                return;
            }
        }

        if (queue.isEmpty()) {
            finish();
        } else {
            startProcessing(plugin, queue.poll(), 0);
        }
    }

    /**
     * Stops the current session immediately. The item that was being processed
     * is left in its input slot (it was never cleared) so it will be re-queued
     * at full time if the player clicks Recycle again.
     */
    public void stop() {
        if (!active) return;
        active = false;
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        currentItem = null;
        timeLeftTicks = 0;
        progress = 0.0;
        HologramManager.refreshIdle(tableKey);
        // Button is updated by TableListener immediately after calling stop()
    }

    private void startProcessing(JavaPlugin plugin, ItemStack item, long remainingSeconds) {
        active = true;
        currentItem = item;
        currentComplexity = Math.max(1, Math.min(250, ComplexityCalculator.calculateComplexity(item)));
        long totalSeconds = ComplexityCalculator.mapScoreToSeconds(currentComplexity);
        totalSeconds = (long) Math.ceil(totalSeconds / ConfigManager.getSpeedMultiplier());
        totalSeconds *= item.getAmount(); // scale time by stack size

        // Dev mode: process instantly without scheduling a timer
        if (ConfigManager.isInstantProcessing()) {
            processSingleItem(item);
            clearProcessedInputSlot(item);
            active = false;
            HologramManager.refresh(tableKey, this);
            if (!queue.isEmpty()) startProcessing(plugin, queue.poll(), 0);
            else finish();
            return;
        }

        long secondsToRun = (remainingSeconds > 0 ? remainingSeconds : totalSeconds);
        this.timeLeftTicks = secondsToRun * 20L;
        this.progress = 1.0 - (secondsToRun / (double) totalSeconds);
        this.lastActiveTime = System.currentTimeMillis();

        final long total = totalSeconds;
        final long[] remaining = { timeLeftTicks };

        currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    this.cancel();
                    return;
                }
                if (remaining[0] <= 0) {
                    processSingleItem(item);
                    clearProcessedInputSlot(item);
                    this.cancel();
                    currentTask = null;
                    active = false;

                    if (!queue.isEmpty()) startProcessing(plugin, queue.poll(), 0);
                    else finish();

                    HologramManager.refresh(tableKey, RecycleSession.this);
                    return;
                }

                remaining[0] -= 20L;
                timeLeftTicks = remaining[0];
                progress = 1.0 - (remaining[0] / (double) (total * 20L));
                lastActiveTime = System.currentTimeMillis();

                HologramManager.refresh(tableKey, RecycleSession.this);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void processSingleItem(ItemStack item) {
        List<ItemStack> decomposed = RecipeManager.decomposeToRaw(item);
        double durabilityFactor = CompatibilityUtils.getDurabilityFactor(item);
        Map<Material, Integer> aggregated = new HashMap<>();
        Map<Material, Double>  scaledAmounts = new HashMap<>();

        for (ItemStack raw : decomposed) {
            int baseAmt = raw.getAmount() * item.getAmount(); // scale by stack size
            double scaled = baseAmt * durabilityFactor;
            int floored = (int) Math.floor(scaled);
            if (floored > 0) aggregated.merge(raw.getType(), floored, Integer::sum);
            // Track scaled values for probabilistic fallback
            scaledAmounts.merge(raw.getType(), scaled, Double::sum);
        }

        if (aggregated.isEmpty()) {
            // Each material gets its own independent probability roll.
            // e.g. 12% durability diamond pickaxe: diamonds scaled=0.36 → 36% chance of 1 diamond,
            //                                       sticks scaled=0.24  → 24% chance of 1 stick.
            for (Map.Entry<Material, Double> e : scaledAmounts.entrySet()) {
                if (Math.random() < e.getValue()) {
                    aggregated.put(e.getKey(), 1);
                }
            }
            // Absolute last resort (extremely low durability): give 1 of the most weighted material
            if (aggregated.isEmpty() && !scaledAmounts.isEmpty()) {
                Material best = Collections.max(scaledAmounts.entrySet(), Map.Entry.comparingByValue()).getKey();
                aggregated.put(best, 1);
            }
        }

        Player pl = Bukkit.getPlayer(owner);
        Map<Material, Integer> overflow = new HashMap<>();

        for (Map.Entry<Material, Integer> out : aggregated.entrySet()) {
            int remaining = out.getValue();
            for (int i = 0; i < 54 && remaining > 0; i++) {
                if (!TableListener.isOutputSlot(i)) continue;
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

            if (remaining > 0) overflow.put(out.getKey(), remaining);
        }

        // Collect all overflow items into one list and write to disk a single time
        List<ItemStack> overflowBatch = new ArrayList<>();
        if (!overflow.isEmpty()) {
            for (Map.Entry<Material, Integer> e : overflow.entrySet()) {
                overflowBatch.add(new ItemStack(e.getKey(), e.getValue()));
            }
            if (pl != null)
                pl.sendMessage(ChatColor.YELLOW + "Output full — excess items saved to overflow storage.");
        }

        if (ConfigManager.enchantmentsEnabled()) {
            // ── Vanilla enchantment books (level reduced by one tier) ────────
            if (EnchantUtils.hasAnyEnchants(item)) {
                Map<org.bukkit.enchantments.Enchantment, Integer> returned = EnchantUtils.getReturnedEnchantments(item);
                List<ItemStack> books = EnchantUtils.generateEnchantmentBooks(returned);
                for (ItemStack book : books) {
                    boolean placed = false;
                    for (int i = 27; i <= 53; i++) {
                        if (!TableListener.isOutputSlot(i)) continue;
                        ItemStack slot = guiInventory.getItem(i);
                        if (slot == null || slot.getType() == Material.AIR) {
                            guiInventory.setItem(i, book);
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) overflowBatch.add(book);
                }
            }

            // ── AdvancedEnchantments books (exact level, no reduction) ───────
            if (AEIntegration.isLoaded()) {
                List<ItemStack> aeBooks = AEIntegration.getEnchantmentBooks(item);
                for (ItemStack book : aeBooks) {
                    boolean placed = false;
                    for (int i = 27; i <= 53; i++) {
                        if (!TableListener.isOutputSlot(i)) continue;
                        ItemStack slot = guiInventory.getItem(i);
                        if (slot == null || slot.getType() == Material.AIR) {
                            guiInventory.setItem(i, book);
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) overflowBatch.add(book);
                }
            }
        }

        // Single batched write — avoids repeated file saves for the same item
        if (!overflowBatch.isEmpty()) {
            OverflowStorage.addItems(tableKey, overflowBatch);
        }

        // ✅ Hook: adaptive learning analytics (auto-balances future complexity)
        AnalyticsManager.logRecycle(item);

        if (pl != null)
            pl.sendMessage(ChatColor.GREEN + "Processed " + item.getAmount() + "x " + item.getType().name());
    }

    /**
     * Removes the first input slot that exactly matches the processed item (type + amount).
     * Falls back to a type-only match in case amounts drifted, so slots don't silently linger.
     */
    private void clearProcessedInputSlot(ItemStack item) {
        // First pass — exact match
        for (int i = 0; i < 54; i++) {
            if (!TableListener.isInputSlot(i) || TableListener.isControlSlot(i)) continue;
            ItemStack slot = guiInventory.getItem(i);
            if (slot != null && slot.getType() == item.getType() && slot.getAmount() == item.getAmount()) {
                guiInventory.setItem(i, null);
                return;
            }
        }
        // Second pass — type-only fallback
        for (int i = 0; i < 54; i++) {
            if (!TableListener.isInputSlot(i) || TableListener.isControlSlot(i)) continue;
            ItemStack slot = guiInventory.getItem(i);
            if (slot != null && slot.getType() == item.getType()) {
                guiInventory.setItem(i, null);
                return;
            }
        }
    }

    private void finish() {
        active = false;
        currentTask = null;
        currentItem = null;
        timeLeftTicks = 0;
        progress = 1.0;
        HologramManager.refreshIdle(tableKey);
        // Safety net: clear any input slots that weren't cleaned up during processing
        for (int i = 0; i < 54; i++) {
            if (TableListener.isInputSlot(i) && !TableListener.isControlSlot(i)) {
                guiInventory.setItem(i, null);
            }
        }
        // Restore the Recycle button now that processing is done
        TableListener.refreshRecycleButton(guiInventory, false);
        Player pl = Bukkit.getPlayer(owner);
        if (pl != null)
            pl.sendMessage(ChatColor.GREEN + "Recycling session completed.");
    }
}