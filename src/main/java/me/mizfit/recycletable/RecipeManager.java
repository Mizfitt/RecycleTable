package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.*;

public class RecipeManager {
    private static final Map<Material, RecipeData> recipes = new HashMap<>();
    private static final Set<Material> blacklist = new HashSet<>();

    public static void initialize() {
        loadBlacklist();
        generateAllRecipes();
    }

    private static void loadBlacklist() {
        List<String> list = RecycleTable.getInstance().getConfig().getStringList("blacklist");
        for (String s : list) {
            try { Material m = Material.valueOf(s.toUpperCase()); blacklist.add(m);} catch (Exception ex) {
                Bukkit.getLogger().warning("Invalid material in blacklist: " + s);
            }
        }
    }

    private static void generateAllRecipes() {
        recipes.clear();
        for (Material mat : Material.values()) {
            if (blacklist.contains(mat)) continue;
            if (mat.getMaxDurability() > 0 || isCraftable(mat) || mat == Material.ANVIL) {
                ItemStack stack = new ItemStack(mat);
                List<ItemStack> ingredients = new ArrayList<>();
                Collection<Recipe> r = Bukkit.getRecipesFor(stack);
                if (!r.isEmpty()) {
                    for (Recipe rec : r) {
                        if (rec instanceof ShapedRecipe) {
                            ShapedRecipe sr = (ShapedRecipe) rec;
                            try { sr.getIngredientMap().forEach((k, v) -> { if (v != null) ingredients.add(new ItemStack(v)); }); } catch (Throwable ignored) {}
                            break;
                        }
                    }
                }
                if (ingredients.isEmpty()) ingredients.add(new ItemStack(mat));
                recipes.put(mat, new RecipeData(ingredients, 1));
            }
        }
    }

    public static RecipeData getRecipeFor(Material mat) { if (blacklist.contains(mat)) return null; return recipes.get(mat); }
    public static boolean isBlacklisted(Material m) { return blacklist.contains(m); }

    private static boolean isCraftable(Material mat) { Collection<Recipe> r = Bukkit.getRecipesFor(new ItemStack(mat)); return !r.isEmpty(); }

    public static List<ItemStack> decomposeToRaw(ItemStack item) {
        if (item == null) return Collections.emptyList();
        if (blacklist.contains(item.getType())) return Collections.singletonList(item.clone());
        RecipeData recipe = getRecipeFor(item.getType());
        if (recipe == null) return Collections.singletonList(item.clone());
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (blacklist.contains(ingredient.getType())) { result.add(ingredient.clone()); continue; }
            RecipeData sub = getRecipeFor(ingredient.getType());
            if (sub != null && !ingredient.getType().equals(item.getType())) {
                List<ItemStack> subItems = decomposeToRaw(ingredient);
                for (ItemStack si : subItems) { ItemStack c = si.clone(); c.setAmount(c.getAmount() * ingredient.getAmount()); result.add(c); }
            } else result.add(ingredient.clone());
        }
        return result;
    }

    public static int getRecipeCount() { return recipes.size(); }

    public static class RecipeData {
        private final List<ItemStack> ingredients; private final int resultAmount;
        public RecipeData(List<ItemStack> ingredients, int resultAmount) { this.ingredients = ingredients; this.resultAmount = resultAmount; }
        public List<ItemStack> getIngredients() { return ingredients; }
        public int getResultAmount() { return resultAmount; }
    }
}