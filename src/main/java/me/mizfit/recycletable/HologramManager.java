package me.mizfit.recycletable;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages floating hologram displays above placed Recycling Table blocks.
 *
 * Each hologram is made of 3 stacked invisible ArmorStands — one per text line.
 * Holograms are keyed by the same "world:x:y:z" string used in TablePersistence.
 *
 * Three display modes are supported:
 *   CURRENT_ITEM — item name, progress bar, and time left for the active item
 *   ALL_ITEMS    — item count + total time until the full queue finishes
 *   OFF          — hologram hidden (blank names)
 */
public class HologramManager {

    public enum HologramMode { CURRENT_ITEM, ALL_ITEMS, OFF }

    // 3 ArmorStands per table, indexed 0 (top) → 2 (bottom)
    private static final Map<String, List<ArmorStand>> holograms = new HashMap<>();
    private static final Map<String, HologramMode>     modes     = new HashMap<>();

    // Y offsets above the block surface for each line
    private static final double[] Y_OFFSETS = { 2.5, 2.25, 2.0 };

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Spawns a 3-line hologram above the given block location.
     * Removes any existing hologram at the same spot first.
     */
    public static void spawnHologram(Location blockLoc) {
        String key = keyFor(blockLoc);
        if (key == null) return;
        removeHologram(key);

        World world = blockLoc.getWorld();
        if (world == null) return;

        double cx = blockLoc.getBlockX() + 0.5;
        double by = blockLoc.getBlockY();
        double cz = blockLoc.getBlockZ() + 0.5;

        List<ArmorStand> stands = new ArrayList<>();
        for (double offset : Y_OFFSETS) {
            Location spawnLoc = new Location(world, cx, by + offset, cz);
            ArmorStand stand = (ArmorStand) world.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(" ");
            stand.setSmall(true);
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            // setMarker (no hitbox) was added in 1.9 — safe to ignore on older versions
            try { stand.setMarker(true); } catch (NoSuchMethodError ignored) {}
            // Prevent the entity from being saved to the world file (1.14+)
            try { stand.setPersistent(false); } catch (NoSuchMethodError ignored) {}
            stands.add(stand);
        }

        holograms.put(key, stands);
        modes.putIfAbsent(key, HologramMode.CURRENT_ITEM);
        renderIdle(key);
    }

    /**
     * Called every second from RecycleSession's BukkitRunnable.
     * Updates the hologram text based on the current mode.
     */
    public static void refresh(String key, RecycleSession session) {
        if (key == null || !holograms.containsKey(key)) return;

        switch (modes.getOrDefault(key, HologramMode.CURRENT_ITEM)) {
            case CURRENT_ITEM: renderCurrentItem(key, session); break;
            case ALL_ITEMS:    renderAllItems(key, session);    break;
            case OFF:          renderOff(key);                  break;
        }
    }

    /**
     * Renders the idle state (no active session).
     * Called when a session finishes or when the mode is cycled with no active session.
     */
    public static void refreshIdle(String key) {
        if (key == null || !holograms.containsKey(key)) return;
        if (modes.getOrDefault(key, HologramMode.CURRENT_ITEM) == HologramMode.OFF) {
            renderOff(key);
        } else {
            renderIdle(key);
        }
    }

    /**
     * Advances the hologram to the next mode and immediately refreshes the display.
     * If a session is active it will update on the next tick; otherwise shows idle/off.
     */
    public static void cycleMode(String key) {
        if (key == null) return;
        HologramMode current = modes.getOrDefault(key, HologramMode.CURRENT_ITEM);
        HologramMode next = HologramMode.values()[(current.ordinal() + 1) % HologramMode.values().length];
        modes.put(key, next);
    }

    /** Returns the current display mode for the given table key. */
    public static HologramMode getMode(String key) {
        return modes.getOrDefault(key, HologramMode.CURRENT_ITEM);
    }

    /** Removes and destroys the hologram for the given table key. */
    public static void removeHologram(String key) {
        List<ArmorStand> stands = holograms.remove(key);
        if (stands != null) killStands(stands);
        modes.remove(key);
    }

    /** Removes and destroys ALL active holograms. Called on server shutdown. */
    public static void removeAll() {
        for (List<ArmorStand> stands : holograms.values()) killStands(stands);
        holograms.clear();
        modes.clear();
    }

    // ── Renderers ─────────────────────────────────────────────────────────────

    private static void renderCurrentItem(String key, RecycleSession session) {
        if (!session.isActive() || session.getCurrentItem() == null) {
            renderIdle(key);
            return;
        }

        String itemName = formatName(session.getCurrentItem().getType().name());
        long   secsLeft = session.getTimeLeft() / 20L;
        String bar      = buildProgressBar(session.getProgress());
        String timeStr  = formatTime(secsLeft);

        setLine(key, 0, ChatColor.AQUA   + itemName);
        setLine(key, 1, ChatColor.YELLOW + bar);
        setLine(key, 2, ChatColor.WHITE  + "⏳ " + ChatColor.GREEN + timeStr);
    }

    private static void renderAllItems(String key, RecycleSession session) {
        if (!session.isActive()) {
            renderIdle(key);
            return;
        }

        // Start with time left on the current item
        long totalSecs = session.getTimeLeft() / 20L;

        // Add estimated time for every item still waiting in the queue
        for (ItemStack queued : session.getQueuedItems()) {
            if (queued == null || queued.getType() == org.bukkit.Material.AIR) continue;
            int  complexity = Math.max(1, Math.min(250, ComplexityCalculator.calculateComplexity(queued)));
            long itemSecs   = ComplexityCalculator.mapScoreToSeconds(complexity);
            itemSecs = (long) Math.ceil(itemSecs / ConfigManager.getSpeedMultiplier());
            itemSecs *= queued.getAmount();
            totalSecs += itemSecs;
        }

        int total = 1 + session.getQueuedItems().size(); // current item + queued items

        setLine(key, 0, ChatColor.AQUA  + "⚗ Recycling Queue");
        setLine(key, 1, ChatColor.WHITE + total + " item" + (total == 1 ? "" : "s") + " remaining");
        setLine(key, 2, ChatColor.WHITE + "⏳ " + ChatColor.GREEN + formatTime(totalSecs) + " total");
    }

    private static void renderIdle(String key) {
        setLine(key, 0, ChatColor.AQUA + "⚗ Recycling Table");
        setLine(key, 1, ChatColor.GRAY + "Idle");
        setLine(key, 2, " ");
    }

    private static void renderOff(String key) {
        setLine(key, 0, " ");
        setLine(key, 1, " ");
        setLine(key, 2, " ");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setLine(String key, int index, String text) {
        List<ArmorStand> stands = holograms.get(key);
        if (stands == null || index < 0 || index >= stands.size()) return;
        ArmorStand stand = stands.get(index);
        if (stand != null && !stand.isDead()) stand.setCustomName(text);
    }

    private static void killStands(List<ArmorStand> stands) {
        if (stands == null) return;
        for (ArmorStand s : stands) {
            if (s != null && !s.isDead()) s.remove();
        }
    }

    private static String buildProgressBar(double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        return sb + " " + (int)(progress * 100) + "%";
    }

    private static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static String formatName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /** Generates the same "world:x:y:z" key format used by TablePersistence. */
    public static String keyFor(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
