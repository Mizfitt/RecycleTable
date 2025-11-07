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

public class RecycleSession {
    private final UUID owner;
    private final Queue<ItemStack> queue = new ConcurrentLinkedQueue<>();
    private final Inventory guiInventory;

    private volatile boolean active = false;
    private long timeLeftTicks = 0L; // ticks remaining for current item
    private int currentComplexity = 1;
    private double progress = 0.0;

    public RecycleSession(UUID owner, List<ItemStack> inputs, Inventory guiInventory) {
        this.owner = owner;
        for (ItemStack is : inputs) {
            int amount = Math.max(1, is.getAmount());
            for (int i = 0; i < amount; i++) {
                ItemStack single = is.clone();
                single.setAmount(1);
                queue.add(single);
            }
        }
        this.guiInventory = guiInventory;
    }

    // For loading from storage
    public RecycleSession(UUID owner) { this.owner = owner; this.guiInventory = null; }

    public boolean isActive() { return active; }
    public long getTimeLeft() { return timeLeftTicks; }
    public int getComplexity() { return currentComplexity; }
    public double getProgress() { return progress; }

    public List<ItemStack> serializeItems() { return new ArrayList<>(queue); }

    public void start(JavaPlugin plugin) { startTask(plugin, 0L); }

    public void resumeTask(JavaPlugin plugin) {
        if (!active || timeLeftTicks <= 0) return;
        startTask(plugin, timeLeftTicks / 20L); // seconds
    }

    private void startTask(JavaPlugin plugin, long delaySeconds) {
        if (queue.isEmpty()) {
            finish();
            return;
        }

        ItemStack current = queue.poll();
        this.active = true;
        int complexity = ComplexityCalculator.calculateComplexity(current);
        currentComplexity = Math.max(1, Math.min(250, complexity));

        final long seconds = (long) Math.ceil(
                ComplexityCalculator.mapScoreToSeconds(currentComplexity) / ConfigManager.getSpeedMultiplier()
        );

        this.timeLeftTicks = seconds * 20L;
        this.progress = 0.0;

        new BukkitRunnable() {
            long remaining = timeLeftTicks;
            @Override
            public void run() {
                if (remaining <= 0) {
                    processSingleItem(current);
                    this.cancel();
                    active = false;
                    if (!queue.isEmpty()) startTask(plugin, 0L);
                    else finish();
                    return;
                }
                remaining -= 20L;
                timeLeftTicks = remaining;
                progress = 1.0 - (remaining / (double)(seconds * 20L));
            }
        }.runTaskTimer(plugin, Math.max(0L, delaySeconds * 20L), 20L);
    }

    private void processSingleItem(ItemStack item) {
        List<ItemStack> decomposed = RecipeManager.decomposeToRaw(item);
        double durabilityFactor = CompatibilityUtils.getDurabilityFactor(item);
        Map<Material, Integer> aggregated = new HashMap<>();

        for (ItemStack raw : decomposed) {
            int baseAmt = raw.getAmount();
            double rawScaled = baseAmt * durabilityFactor;
            int floored = (int) Math.floor(rawScaled);
            if (floored > 0) aggregated.merge(raw.getType(), floored, Integer::sum);
        }

        if (aggregated.isEmpty()) {
            Map<Material, Integer> ratio = new HashMap<>();
            int total = 0;
            for (ItemStack raw : decomposed) { ratio.merge(raw.getType(), raw.getAmount(), Integer::sum); total += raw.getAmount(); }
            double r = Math.random(); double cumulative = 0.0;
            for (Map.Entry<Material, Integer> e : ratio.entrySet()) {
                cumulative += e.getValue() / (double) total;
                if (r <= cumulative) { aggregated.put(e.getKey(), 1); break; }
            }
            if (aggregated.isEmpty() && !ratio.isEmpty()) aggregated.put(ratio.keySet().iterator().next(), 1);
        }

        // Deposit to output area
        for (Map.Entry<Material, Integer> out : aggregated.entrySet()) {
            int remaining = out.getValue();
            for (int i = 27; i <= 53 && remaining > 0; i++) {
                ItemStack slot = guiInventory.getItem(i);
                if (slot == null || slot.getType() == Material.AIR) {
                    int place = Math.min(remaining, out.getKey().getMaxStackSize());
                    ItemStack put = new ItemStack(out.getKey(), place);
                    guiInventory.setItem(i, put);
                    remaining -= place;
                } else if (slot.getType() == out.getKey() && slot.getAmount() < slot.getMaxStackSize()) {
                    int canAdd = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
                    slot.setAmount(slot.getAmount() + canAdd);
                    remaining -= canAdd;
                    guiInventory.setItem(i, slot);
                }
            }
            if (remaining > 0) {
                Player pl = Bukkit.getPlayer(owner);
                if (pl != null) {
                    Map<Integer, ItemStack> rem = pl.getInventory().addItem(new ItemStack(out.getKey(), remaining));
                    if (!rem.isEmpty()) for (ItemStack r : rem.values()) pl.getWorld().dropItemNaturally(pl.getLocation(), r);
                }
            }
        }

        // Enchantment extraction (optional)
        if (ConfigManager.enchantmentsEnabled() && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            Map<org.bukkit.enchantments.Enchantment, Integer> returned = EnchantUtils.getReturnedEnchantments(item);
            List<ItemStack> books = EnchantUtils.generateEnchantmentBooks(returned);
            for (ItemStack book : books) {
                boolean placed = false;
                for (int i = 27; i <= 53; i++) {
                    ItemStack slot = guiInventory.getItem(i);
                    if (slot == null || slot.getType() == Material.AIR) { guiInventory.setItem(i, book); placed = true; break; }
                }
                if (!placed) {
                    Player pl = Bukkit.getPlayer(owner);
                    if (pl != null) {
                        Map<Integer, ItemStack> rem = pl.getInventory().addItem(book);
                        if (!rem.isEmpty()) for (ItemStack r : rem.values()) pl.getWorld().dropItemNaturally(pl.getLocation(), r);
                    }
                }
            }
        }

        Player pl = Bukkit.getPlayer(owner);
        if (pl != null) pl.sendMessage(ChatColor.GREEN + "Processed 1x " + item.getType().name());
    }

    private void finish() {
        active = false;
        timeLeftTicks = 0;
        progress = 1.0;
        Player pl = Bukkit.getPlayer(owner);
        if (pl != null) pl.sendMessage(ChatColor.GREEN + "Recycling session completed.");
    }
}

