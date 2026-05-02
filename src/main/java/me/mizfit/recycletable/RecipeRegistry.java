package me.mizfit.recycletable;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers all crafting recipes — the 8 intermediate components and the
 * final Recycling Table assembly recipe.
 *
 * Final assembly layout:
 *   [ Disassembler ] [ Crafter's Codex ] [ Analyzer      ]
 *   [ Resonator    ] [ Crafting Table  ] [ Sorting Chassis]
 *   [ Catalyst     ] [ Recall Chamber  ] [ Complexity Eng ]
 */
public class RecipeRegistry {

    public static void registerAll(JavaPlugin plugin) {
        registerDisassembler(plugin);
        registerAnalyzer(plugin);
        registerCodex(plugin);
        registerChassis(plugin);
        registerResonator(plugin);
        registerCatalyst(plugin);
        registerRecallChamber(plugin);
        registerComplexityEngine(plugin);
        registerRecyclingTable(plugin);
    }

    // ── Component recipes ─────────────────────────────────────────────────────

    /**
     * Mechanical Disassembler
     *   I P I
     *   H B H   I=Iron Ingot  P=Piston  H=Hopper  B=Iron Block
     *   I P I
     */
    private static void registerDisassembler(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "mechanical_disassembler"),
                CustomItems.createDisassembler());
        r.shape("IPI", "HBH", "IPI");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('P', Material.PISTON);
        r.setIngredient('H', Material.HOPPER);
        r.setIngredient('B', Material.IRON_BLOCK);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Component Analyzer
     *   F C F
     *   H R H   F=Flint  C=Comparator  H=Hopper  R=Redstone
     *   F C F
     */
    private static void registerAnalyzer(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "component_analyzer"),
                CustomItems.createAnalyzer());
        r.shape("FCF", "HRH", "FCF");
        r.setIngredient('F', Material.FLINT);
        r.setIngredient('C', Material.COMPARATOR);
        r.setIngredient('H', Material.HOPPER);
        r.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Crafter's Codex
     *   B S B
     *   G L G   B=Book  S=Bookshelf  G=Grindstone  L=Lectern
     *   B S B
     */
    private static void registerCodex(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "crafters_codex"),
                CustomItems.createCodex());
        r.shape("BSB", "GLG", "BSB");
        r.setIngredient('B', Material.BOOK);
        r.setIngredient('S', Material.BOOKSHELF);
        r.setIngredient('G', Material.GRINDSTONE);
        r.setIngredient('L', Material.LECTERN);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Sorting Chassis
     *   I D I
     *   H C H   I=Iron Ingot  D=Dropper  H=Hopper  C=Chest
     *   I D I
     */
    private static void registerChassis(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "sorting_chassis"),
                CustomItems.createChassis());
        r.shape("IDI", "HCH", "IDI");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('D', Material.DROPPER);
        r.setIngredient('H', Material.HOPPER);
        r.setIngredient('C', Material.CHEST);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Arcane Resonator
     *   E R E
     *   O P O   E=Ender Eye  R=Blaze Rod  O=Observer  P=Ender Pearl
     *   E R E
     */
    private static void registerResonator(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "arcane_resonator"),
                CustomItems.createResonator());
        r.shape("ERE", "OPO", "ERE");
        r.setIngredient('E', Material.ENDER_EYE);
        r.setIngredient('R', Material.BLAZE_ROD);
        r.setIngredient('O', Material.OBSERVER);
        r.setIngredient('P', Material.ENDER_PEARL);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Salvage Catalyst
     *   X I X
     *   I F I   X=Flint & Steel  I=Iron Ingot  F=Flint
     *   X I X
     */
    private static void registerCatalyst(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "salvage_catalyst"),
                CustomItems.createCatalyst());
        r.shape("XIX", "IFI", "XIX");
        r.setIngredient('X', Material.FLINT_AND_STEEL);
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('F', Material.FLINT);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Recall Chamber
     *   A K A
     *   P E P   A=Amethyst Shard  K=Shulker Shell  P=Ender Pearl  E=Ender Chest
     *   A K A
     */
    private static void registerRecallChamber(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "recall_chamber"),
                CustomItems.createRecallChamber());
        r.shape("AKA", "PEP", "AKA");
        r.setIngredient('A', Material.AMETHYST_SHARD);
        r.setIngredient('K', Material.SHULKER_SHELL);
        r.setIngredient('P', Material.ENDER_PEARL);
        r.setIngredient('E', Material.ENDER_CHEST);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Complexity Engine
     *   Q C Q
     *   C T C   Q=Quartz Block  C=Comparator  T=Target
     *   Q C Q
     */
    private static void registerComplexityEngine(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "complexity_engine"),
                CustomItems.createComplexityEngine());
        r.shape("QCQ", "CTC", "QCQ");
        r.setIngredient('Q', Material.QUARTZ_BLOCK);
        r.setIngredient('C', Material.COMPARATOR);
        r.setIngredient('T', Material.TARGET);
        plugin.getServer().addRecipe(r);
    }

    // ── Final assembly ────────────────────────────────────────────────────────

    /**
     * Recycling Table (final assembly)
     *   [ Disassembler ] [ Crafter's Codex ] [ Analyzer       ]
     *   [ Resonator    ] [ Crafting Table  ] [ Sorting Chassis ]
     *   [ Catalyst     ] [ Recall Chamber  ] [ Complexity Eng  ]
     *
     * All 8 surrounding slots require the exact custom component items.
     * The centre is a plain Crafting Table.
     */
    private static void registerRecyclingTable(JavaPlugin plugin) {
        ShapedRecipe r = new ShapedRecipe(
                new NamespacedKey(plugin, "recycling_table"),
                RecyclingTableItem.createItem());
        r.shape("DCA", "RXS", "LYE");
        r.setIngredient('D', new RecipeChoice.ExactChoice(CustomItems.createDisassembler()));
        r.setIngredient('C', new RecipeChoice.ExactChoice(CustomItems.createCodex()));
        r.setIngredient('A', new RecipeChoice.ExactChoice(CustomItems.createAnalyzer()));
        r.setIngredient('R', new RecipeChoice.ExactChoice(CustomItems.createResonator()));
        r.setIngredient('X', Material.CRAFTING_TABLE);
        r.setIngredient('S', new RecipeChoice.ExactChoice(CustomItems.createChassis()));
        r.setIngredient('L', new RecipeChoice.ExactChoice(CustomItems.createCatalyst()));
        r.setIngredient('Y', new RecipeChoice.ExactChoice(CustomItems.createRecallChamber()));
        r.setIngredient('E', new RecipeChoice.ExactChoice(CustomItems.createComplexityEngine()));
        plugin.getServer().addRecipe(r);
    }
}
