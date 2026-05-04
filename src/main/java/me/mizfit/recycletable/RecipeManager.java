package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import java.util.*;

/**
 * Handles recipe discovery and decomposition for recyclable items.
 *
 * Key design decisions:
 *  - Only the first shaped or shapeless recipe is used (no recipe mixing).
 *  - Smelting/furnace recipes are skipped (they run backwards for recycling).
 *  - Shaped recipes count character occurrences in the shape grid so that
 *    e.g. a pickaxe correctly indexes 3 diamonds + 2 sticks, not 1 of each.
 *  - Recipe result amount (yield) is recorded so decomposition can normalise
 *    correctly (2 planks → 4 sticks means 1 stick costs 0.5 planks).
 *  - Decomposition is limited to MAX_DECOMPOSE_DEPTH levels so materials
 *    that are primarily obtained as drops (leather, string, …) are returned
 *    as-is rather than being decomposed to their crafting sub-ingredients.
 */
public class RecipeManager {

    /** How many crafting layers deep to follow when decomposing an item. */
    private static final int MAX_DECOMPOSE_DEPTH = 2;

    private static final Map<Material, RecipeData> recipes   = new HashMap<>();
    private static final Set<Material>             blacklist = new HashSet<>();
    /** Cache: fractional ingredients for exactly 1 unit of a material. */
    private static final Map<Material, Map<Material, Double>> fracCache = new HashMap<>();

    // ── Initialisation ────────────────────────────────────────────────────────

    public static void initialize() {
        loadBlacklist();
        generateAllRecipes();
    }

    private static void loadBlacklist() {
        blacklist.clear();
        List<String> list = RecycleTable.getInstance().getConfig().getStringList("blacklist");
        for (String s : list) {
            try {
                blacklist.add(Material.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (Exception ex) {
                Bukkit.getLogger().warning("[RecycleTable] Invalid blacklist material: " + s);
            }
        }
    }

    // ── Recipe indexing ───────────────────────────────────────────────────────

    private static void generateAllRecipes() {
        recipes.clear();
        fracCache.clear();

        for (Material mat : Material.values()) {
            if (blacklist.contains(mat) || mat == Material.AIR || !mat.isItem()) continue;

            ItemStack stack = new ItemStack(mat);
            Collection<Recipe> found = Bukkit.getRecipesFor(stack);
            List<ItemStack> ingredients = new ArrayList<>();
            int resultAmount = 1;

            if (found != null) {
                for (Recipe rec : found) {
                    try {
                        if (rec instanceof ShapedRecipe) {
                            ShapedRecipe shaped = (ShapedRecipe) rec;
                            String[] shape = shaped.getShape();
                            Map<Character, ItemStack> map = shaped.getIngredientMap();

                            // Count how many times each character appears in the shape
                            // so "DDD / _S_ / _S_" gives diamond×3, stick×2 — not 1 of each.
                            Map<Character, Integer> charCount = new HashMap<>();
                            for (String row : shape)
                                for (char c : row.toCharArray())
                                    if (c != ' ') charCount.merge(c, 1, Integer::sum);

                            for (Map.Entry<Character, Integer> e : charCount.entrySet()) {
                                ItemStack base = map.get(e.getKey());
                                if (base == null || base.getType() == Material.AIR) continue;
                                ItemStack counted = base.clone();
                                counted.setAmount(e.getValue());
                                ingredients.add(counted);
                            }
                            resultAmount = rec.getResult().getAmount();
                            break;

                        } else if (rec instanceof ShapelessRecipe) {
                            for (ItemStack i : ((ShapelessRecipe) rec).getIngredientList())
                                if (i != null && i.getType() != Material.AIR) ingredients.add(i);
                            resultAmount = rec.getResult().getAmount();
                            break;
                        }
                        // Furnace / blasting / smoking — skipped
                    } catch (Throwable ignored) {}
                }
            }

            if (ingredients.isEmpty()) ingredients.add(new ItemStack(mat)); // self-reference base

            recipes.put(mat, new RecipeData(ingredients, Math.max(1, resultAmount)));
        }

        Bukkit.getLogger().info("[RecycleTable] Indexed " + recipes.size() + " recipes.");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isBlacklisted(Material m) { return blacklist.contains(m); }

    public static RecipeData getRecipeFor(Material mat) {
        if (blacklist.contains(mat)) return null;
        return recipes.get(mat);
    }

    public static int getRecipeCount() { return recipes.size(); }

    /**
     * Decomposes the given item into its base crafting ingredients (for 1 unit).
     * The caller (processSingleItem) multiplies by stack size separately.
     */
    public static List<ItemStack> decomposeToRaw(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return Collections.emptyList();
        Map<Material, Double> fractions = toFractional(item.getType(), new HashSet<>(), 0);
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<Material, Double> e : fractions.entrySet()) {
            int amt = (int) Math.max(1, Math.round(e.getValue()));
            result.add(new ItemStack(e.getKey(), amt));
        }
        return result;
    }

    // ── Fractional decomposition ──────────────────────────────────────────────

    /**
     * Returns the fractional ingredient amounts needed to produce exactly
     * 1 unit of {@code mat}, following recipes up to MAX_DECOMPOSE_DEPTH layers.
     *
     * Using doubles internally avoids integer-rounding errors mid-chain.
     * (e.g. stick recipe: 2 planks → 4 sticks, so 1 stick costs 0.5 planks;
     *  a pickaxe with 2 sticks therefore costs 1.0 plank — not 2.)
     */
    private static Map<Material, Double> toFractional(Material mat, Set<Material> seen, int depth) {
        // Base cases
        if (blacklist.contains(mat) || seen.contains(mat) || depth >= MAX_DECOMPOSE_DEPTH)
            return singleFrac(mat, 1.0);

        RecipeData recipe = getRecipeFor(mat);
        if (recipe == null || isSelfRef(recipe, mat))
            return singleFrac(mat, 1.0);

        // Cache hit
        if (fracCache.containsKey(mat)) return fracCache.get(mat);

        seen.add(mat);
        int yield = recipe.getResultAmount(); // how many items one recipe run produces
        Map<Material, Double> result = new HashMap<>();

        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;

            // Amount of this ingredient per 1 output unit
            double perUnit = (double) ingredient.getAmount() / yield;

            RecipeData sub = getRecipeFor(ingredient.getType());
            boolean canRecurse = sub != null
                    && !isSelfRef(sub, ingredient.getType())
                    && !seen.contains(ingredient.getType())
                    && depth + 1 < MAX_DECOMPOSE_DEPTH;

            if (canRecurse) {
                Map<Material, Double> subFrac = toFractional(ingredient.getType(), new HashSet<>(seen), depth + 1);
                for (Map.Entry<Material, Double> e : subFrac.entrySet())
                    result.merge(e.getKey(), e.getValue() * perUnit, Double::sum);
            } else {
                result.merge(ingredient.getType(), perUnit, Double::sum);
            }
        }

        fracCache.put(mat, result);
        return result;
    }

    private static Map<Material, Double> singleFrac(Material mat, double amount) {
        Map<Material, Double> m = new HashMap<>();
        m.put(mat, amount);
        return m;
    }

    /** True if the recipe's only ingredient is the item itself (fallback base material). */
    private static boolean isSelfRef(RecipeData recipe, Material mat) {
        List<ItemStack> ing = recipe.getIngredients();
        return ing.size() == 1 && ing.get(0).getType() == mat;
    }

    // ── RecipeData ────────────────────────────────────────────────────────────

    public static class RecipeData {
        private final List<ItemStack> ingredients;
        private final int resultAmount;

        public RecipeData(List<ItemStack> ingredients, int resultAmount) {
            this.ingredients  = ingredients;
            this.resultAmount = resultAmount;
        }

        public List<ItemStack> getIngredients() { return ingredients; }
        public int getResultAmount()            { return resultAmount; }
    }
}
