package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import java.util.*;

/**
 * Handles all recipe discovery and decomposition for recyclable items.
 * Supports shaped, shapeless, and furnace-based recipes across Minecraft versions (1.8–1.20+).
 */
public class RecipeManager {

    private static final Map<Material, RecipeData> recipes = new HashMap<>();
    private static final Set<Material> blacklist = new HashSet<>();
    private static final Map<Material, List<ItemStack>> cache = new HashMap<>();

    /** Initializes all recipes and blacklist */
    public static void initialize() {
        loadBlacklist();
        generateAllRecipes();
    }

    /** Reads blacklisted materials from config.yml */
    private static void loadBlacklist() {
        List<String> list = RecycleTable.getInstance().getConfig().getStringList("blacklist");
        for (String s : list) {
            try {
                Material m = Material.valueOf(s.toUpperCase(Locale.ROOT));
                blacklist.add(m);
            } catch (Exception ex) {
                Bukkit.getLogger().warning("[RecycleTable] Invalid material in blacklist: " + s);
            }
        }
    }

    /** Indexes all Bukkit recipes for decomposition */
    private static void generateAllRecipes() {
        recipes.clear();
        cache.clear();

        for (Material mat : Material.values()) {
            if (blacklist.contains(mat)) continue;

            // Skip uncraftable or air
            if (mat == Material.AIR) continue;

            ItemStack stack = new ItemStack(mat);
            List<ItemStack> ingredients = new ArrayList<>();
            Collection<Recipe> found = Bukkit.getRecipesFor(stack);

            if (found != null && !found.isEmpty()) {
                for (Recipe rec : found) {
                    try {
                        if (rec instanceof ShapedRecipe) {
                            Map<Character, ItemStack> map = ((ShapedRecipe) rec).getIngredientMap();
                            for (ItemStack i : map.values()) if (i != null) ingredients.add(i);
                        } else if (rec instanceof ShapelessRecipe) {
                            ingredients.addAll(((ShapelessRecipe) rec).getIngredientList());
                        } else if (rec instanceof FurnaceRecipe) {
                            FurnaceRecipe fr = (FurnaceRecipe) rec;
                            if (fr.getInput() != null) ingredients.add(fr.getInput());
                        } else if (isModernFurnace(rec)) {
                            // 1.13+ Blasting/Smoking/Campfire
                            ItemStack input = getModernFurnaceInput(rec);
                            if (input != null) ingredients.add(input);
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (ingredients.isEmpty()) {
                // Fallback: treat as base material
                ingredients.add(new ItemStack(mat));
            }

            recipes.put(mat, new RecipeData(ingredients, 1));
        }

        Bukkit.getLogger().info("[RecycleTable] Loaded " + recipes.size() + " recipes.");
    }

    /** Helper: detects modern furnace-like recipes without hard dependency */
    private static boolean isModernFurnace(Recipe rec) {
        String name = rec.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("blasting") || name.contains("smoking") || name.contains("campfire");
    }

    /** Reflection fallback for modern recipes (1.13+) */
    private static ItemStack getModernFurnaceInput(Recipe rec) {
        try {
            Object obj = rec.getClass().getMethod("getInput").invoke(rec);
            if (obj instanceof ItemStack) return (ItemStack) obj;
        } catch (Exception ignored) {}
        return null;
    }

    /** Checks if item is blacklisted */
    public static boolean isBlacklisted(Material m) {
        return blacklist.contains(m);
    }

    /** Returns recipe data for material */
    public static RecipeData getRecipeFor(Material mat) {
        if (blacklist.contains(mat)) return null;
        return recipes.get(mat);
    }

    /** Fully decomposes a material into base ingredients (recursive) */
    public static List<ItemStack> decomposeToRaw(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return Collections.emptyList();
        Material type = item.getType();

        if (blacklist.contains(type)) return Collections.singletonList(item.clone());

        // Cache check
        if (cache.containsKey(type)) return cloneList(cache.get(type));

        RecipeData recipe = getRecipeFor(type);
        if (recipe == null) {
            cache.put(type, Collections.singletonList(item.clone()));
            return Collections.singletonList(item.clone());
        }

        List<ItemStack> result = new ArrayList<>();
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient == null) continue;

            if (blacklist.contains(ingredient.getType())) {
                result.add(ingredient.clone());
                continue;
            }

            RecipeData sub = getRecipeFor(ingredient.getType());
            if (sub != null && !ingredient.getType().equals(type)) {
                List<ItemStack> subItems = decomposeToRaw(ingredient);
                for (ItemStack si : subItems) {
                    ItemStack clone = si.clone();
                    clone.setAmount(clone.getAmount() * ingredient.getAmount());
                    result.add(clone);
                }
            } else {
                result.add(ingredient.clone());
            }
        }

        cache.put(type, cloneList(result));
        return result;
    }

    /** Helper to deep-clone a list of ItemStacks */
    private static List<ItemStack> cloneList(List<ItemStack> src) {
        List<ItemStack> clone = new ArrayList<>();
        for (ItemStack s : src) clone.add(s.clone());
        return clone;
    }

    public static int getRecipeCount() {
        return recipes.size();
    }

    /** RecipeData container */
    public static class RecipeData {
        private final List<ItemStack> ingredients;
        private final int resultAmount;

        public RecipeData(List<ItemStack> ingredients, int resultAmount) {
            this.ingredients = ingredients;
            this.resultAmount = resultAmount;
        }

        public List<ItemStack> getIngredients() {
            return ingredients;
        }

        public int getResultAmount() {
            return resultAmount;
        }
    }
}
