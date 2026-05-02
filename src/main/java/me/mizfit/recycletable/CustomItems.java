package me.mizfit.recycletable;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Factory for all 8 Recycling Table crafting components.
 * Each item is tagged with a PDC key so it can be reliably identified
 * regardless of display name or lore changes.
 */
public class CustomItems {

    // Unique IDs stored in each item's PersistentDataContainer
    public static final String ID_DISASSEMBLER = "mechanical_disassembler";
    public static final String ID_ANALYZER     = "component_analyzer";
    public static final String ID_CODEX        = "crafters_codex";
    public static final String ID_CHASSIS      = "sorting_chassis";
    public static final String ID_RESONATOR    = "arcane_resonator";
    public static final String ID_CATALYST     = "salvage_catalyst";
    public static final String ID_RECALL       = "recall_chamber";
    public static final String ID_COMPLEXITY   = "complexity_engine";

    private static NamespacedKey key;

    /** Lazily initialised — safe to call after onEnable. */
    public static NamespacedKey getKey() {
        if (key == null) key = new NamespacedKey(RecycleTable.getInstance(), "component_id");
        return key;
    }

    /** Returns true if the given ItemStack is the specified component (by PDC tag). */
    public static boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String stored = item.getItemMeta()
                .getPersistentDataContainer()
                .get(getKey(), PersistentDataType.STRING);
        return id.equals(stored);
    }

    // ── Private builder ───────────────────────────────────────────────────────

    private static ItemStack make(String id, Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.getPersistentDataContainer().set(getKey(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    // ── The 8 components ─────────────────────────────────────────────────────

    public static ItemStack createDisassembler() {
        return make(ID_DISASSEMBLER, Material.PISTON,
            ChatColor.GOLD + "Mechanical Disassembler",
            ChatColor.GRAY  + "A marvel of iron and motion,",
            ChatColor.GRAY  + "its pistons hunger to pull apart",
            ChatColor.GRAY  + "what hands once carefully joined.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createAnalyzer() {
        return make(ID_ANALYZER, Material.COMPARATOR,
            ChatColor.GREEN + "Component Analyzer",
            ChatColor.GRAY  + "Redstone logic courses through",
            ChatColor.GRAY  + "its circuits, reading the hidden",
            ChatColor.GRAY  + "blueprint of every crafted thing.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createCodex() {
        return make(ID_CODEX, Material.BOOK,
            ChatColor.AQUA  + "Crafter's Codex",
            ChatColor.GRAY  + "Every recipe ever written lives",
            ChatColor.GRAY  + "within these pages — read not to",
            ChatColor.GRAY  + "build, but to unbuild.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createChassis() {
        return make(ID_CHASSIS, Material.IRON_INGOT,
            ChatColor.WHITE + "Sorting Chassis",
            ChatColor.GRAY  + "The iron skeleton that holds all",
            ChatColor.GRAY  + "other components in place, guiding",
            ChatColor.GRAY  + "each fragment to its rightful slot.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createResonator() {
        return make(ID_RESONATOR, Material.ENDER_EYE,
            ChatColor.DARK_PURPLE + "Arcane Resonator",
            ChatColor.GRAY  + "Drawn from the heart of the Nether",
            ChatColor.GRAY  + "and the silence of the End, it hums",
            ChatColor.GRAY  + "with energy that unravels matter.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createCatalyst() {
        return make(ID_CATALYST, Material.FIRE_CHARGE,
            ChatColor.RED   + "Salvage Catalyst",
            ChatColor.GRAY  + "The first spark in a long chain",
            ChatColor.GRAY  + "of reactions. Without it, the other",
            ChatColor.GRAY  + "seven components are just metal.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createRecallChamber() {
        return make(ID_RECALL, Material.AMETHYST_SHARD,
            ChatColor.LIGHT_PURPLE + "Recall Chamber",
            ChatColor.GRAY  + "When the output overflows, nothing",
            ChatColor.GRAY  + "is lost. This crystal lattice holds",
            ChatColor.GRAY  + "what the table cannot yet release.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }

    public static ItemStack createComplexityEngine() {
        return make(ID_COMPLEXITY, Material.TARGET,
            ChatColor.YELLOW + "Complexity Engine",
            ChatColor.GRAY   + "Four comparators orbit a scoring",
            ChatColor.GRAY   + "dial that weighs every item against",
            ChatColor.GRAY   + "the universe of things ever made.",
            "",
            ChatColor.DARK_GRAY + "[ Recycling Component ]"
        );
    }
}
