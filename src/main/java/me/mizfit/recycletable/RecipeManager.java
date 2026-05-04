package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import java.util.*;

/**
 * Handles recipe discovery and decomposition for recyclable items.
 *
 * Recipe indexing uses Bukkit.recipeIterator() rather than getRecipesFor().
 * getRecipesFor() can return unexpected recipes (datapack, smithing, repair)
 * before the vanilla crafting recipe in newer Paper versions. The iterator
 * gives every registered recipe in load order so we can take the first
 * shaped/shapeless recipe per material reliably.
 *
 * Smelting, blasting, smoking, stonecutting, and smithing recipes are
 * skipped entirely — they describe transformations that run backwards for
 * recycling (ore → ingot would make recycling return ore instead of ingot).
 *
 * Shaped recipes count character occurrences in the shape grid so that
 * e.g. a pickaxe correctly indexes diamond×3 + stick×2, not 1 of each.
 *
 * Recipe yield (result amount) is recorded and used to normalise ingredient
 * fractions, so 2 planks → 4 sticks means 1 stick costs 0.5 planks.
 *
 * Decomposition depth is capped at MAX_DECOMPOSE_DEPTH so natural-drop items
 * (leather, string, …) are not chased to their sub-crafting ingredients.
 */
public class RecipeManager {

    private static final int MAX_DECOMPOSE_DEPTH = 2;

    private static final Map<Material, RecipeData>           recipes   = new HashMap<>();
    private static final Set<Material>                       blacklist = new HashSet<>();
    private static final Map<Material, Map<Material, Double>> fracCache = new HashMap<>();

    // ── Init ─────────────────────────────────────────────────────────────────

    public static void initialize() {
        loadBlacklist();
        generateAllRecipes();
    }

    private static void loadBlacklist() {
        blacklist.clear();
        for (String s : RecycleTable.getInstance().getConfig().getStringList("blacklist")) {
            try { blacklist.add(Material.valueOf(s.toUpperCase(Locale.ROOT))); }
            catch (Exception ex) {
                Bukkit.getLogger().warning("[RecycleTable] Unknown blacklist material: " + s);
            }
        }
    }

    // ── Recipe indexing ───────────────────────────────────────────────────────

    private static void generateAllRecipes() {
        recipes.clear();
        fracCache.clear();

        // Iterate every registered recipe in load order.
        // Vanilla recipes are registered first, so the first shaped/shapeless
        // recipe we encounter for a given material is the canonical vanilla one.
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe rec;
            try { rec = iter.next(); }
            catch (Throwable ignored) { continue; }
            if (rec == null) continue;

            Material resultMat = rec.getResult().getType();
            if (resultMat == Material.AIR || !resultMat.isItem()) continue;
            if (blacklist.contains(resultMat)) continue;
            if (recipes.containsKey(resultMat)) continue; // first recipe wins

            // Skip all non-crafting recipe types
            if (rec instanceof FurnaceRecipe) continue;
            String cls = rec.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (cls.contains("blast") || cls.contains("smok") || cls.contains("campfire")
                    || cls.contains("stonecutt") || cls.contains("smithing")) continue;

            List<ItemStack> ingredients = new ArrayList<>();
            int resultAmount = Math.max(1, rec.getResult().getAmount());

            try {
                if (rec instanceof ShapedRecipe) {
                    ShapedRecipe shaped = (ShapedRecipe) rec;
                    String[] shape = shaped.getShape();
                    Map<Character, ItemStack> map = shaped.getIngredientMap();

                    // Count how many times each character appears in the shape grid.
                    // getIngredientMap() gives char→item but NOT the per-slot count,
                    // so we tally occurrences ourselves (e.g. "DDD" gives D→3).
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

                } else if (rec instanceof ShapelessRecipe) {
                    for (ItemStack i : ((ShapelessRecipe) rec).getIngredientList())
                        if (i != null && i.getType() != Material.AIR) ingredients.add(i);

                } else {
                    continue; // unknown type — skip
                }
            } catch (Throwable ignored) { continue; }

            if (ingredients.isEmpty()) continue;
            recipes.put(resultMat, new RecipeData(ingredients, resultAmount));
        }

        // Every item without a crafting recipe becomes a "base material"
        // represented as a self-reference so decomposition terminates cleanly.
        for (Material mat : Material.values()) {
            if (mat == Material.AIR || !mat.isItem() || blacklist.contains(mat)) continue;
            if (!recipes.containsKey(mat))
                recipes.put(mat, new RecipeData(
                        Collections.singletonList(new ItemStack(mat, 1)), 1));
        }

        Bukkit.getLogger().info("[RecycleTable] Indexed " + recipes.size() + " recipes.");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isBlacklisted(Material m) { return blacklist.contains(m); }
    public static RecipeData getRecipeFor(Material m) {
        return blacklist.contains(m) ? null : recipes.get(m);
    }
    public static int getRecipeCount() { return recipes.size(); }

    /**
     * Returns the raw crafting ingredients for ONE unit of item.getType().
     * processSingleItem multiplies by stack size separately.
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
     * Returns fractional ingredient amounts to produce exactly 1 unit of mat.
     * Doubles are used throughout to avoid compounding rounding errors across
     * multi-level chains (e.g. sticks cost 0.5 planks each, not 1).
     */
    private static Map<Material, Double> toFractional(Material mat,
                                                       Set<Material> seen,
                                                       int depth) {
        if (blacklist.contains(mat) || seen.contains(mat) || depth >= MAX_DECOMPOSE_DEPTH)
            return singleFrac(mat, 1.0);

        RecipeData recipe = getRecipeFor(mat);
        if (recipe == null || isSelfRef(recipe, mat))
            return singleFrac(mat, 1.0);

        // Only cache top-level results (empty seen set) to avoid storing
        // context-dependent results that would be wrong on re-use.
        if (seen.isEmpty() && fracCache.containsKey(mat))
            return fracCache.get(mat);

        seen.add(mat);
        int yield = recipe.getResultAmount();
        Map<Material, Double> result = new HashMap<>();

        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;
            double perUnit = (double) ingredient.getAmount() / yield;

            RecipeData sub = getRecipeFor(ingredient.getType());
            boolean canRecurse = sub != null
                    && !isSelfRef(sub, ingredient.getType())
                    && !seen.contains(ingredient.getType())
                    && depth + 1 < MAX_DECOMPOSE_DEPTH;

            if (canRecurse) {
                Map<Material, Double> subFrac =
                        toFractional(ingredient.getType(), new HashSet<>(seen), depth + 1);
                for (Map.Entry<Material, Double> e : subFrac.entrySet())
                    result.merge(e.getKey(), e.getValue() * perUnit, Double::sum);
            } else {
                result.merge(ingredient.getType(), perUnit, Double::sum);
            }
        }

        if (seen.size() == 1) fracCache.put(mat, result); // only cache root calls
        return result;
    }

    private static Map<Material, Double> singleFrac(Material mat, double v) {
        Map<Material, Double> m = new HashMap<>(); m.put(mat, v); return m;
    }

    private static boolean isSelfRef(RecipeData r, Material mat) {
        List<ItemStack> ing = r.getIngredients();
        return ing.size() == 1 && ing.get(0).getType() == mat;
    }

    // ── RecipeData ────────────────────────────────────────────────────────────

    public static class RecipeData {
        private final List<ItemStack> ingredients;
        private final int resultAmount;
        RecipeData(List<ItemStack> i, int r) { ingredients = i; resultAmount = r; }
        public List<ItemStack> getIngredients() { return ingredients; }
        public int getResultAmount()            { return resultAmount; }
    }
}
