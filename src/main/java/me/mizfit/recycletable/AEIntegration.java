package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Soft integration with the AdvancedEnchantments plugin.
 *
 * All calls use reflection so the plugin works normally on servers that
 * don't have AE installed. If AE is present but its API changes in a future
 * version, every method fails silently rather than crashing the server.
 *
 * API target: net.advancedplugins.ae.api.AEAPI
 *   getEnchantmentsOnItem(ItemStack) → Map<String, Integer>
 *   getEnchantedBook(String, int)    → ItemStack
 */
public class AEIntegration {

    private static boolean loaded = false;
    private static Method methodGetEnchants = null;
    private static Method methodGetBook    = null;

    /** Called once during onEnable. Safe to call even if AE is not installed. */
    public static void initialize() {
        if (Bukkit.getPluginManager().getPlugin("AdvancedEnchantments") == null) return;

        try {
            Class<?> api = Class.forName("net.advancedplugins.ae.api.AEAPI");

            // Read enchantments off an item
            methodGetEnchants = api.getMethod("getEnchantmentsOnItem", ItemStack.class);

            // Create an enchanted book — try the names used across AE versions
            for (String name : new String[]{"getEnchantedBook", "getBookItemStack", "getBook", "createBook"}) {
                try {
                    methodGetBook = api.getMethod(name, String.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }

            loaded = true;
            Bukkit.getLogger().info("[RecycleTable] AdvancedEnchantments integration enabled.");

            if (methodGetBook == null) {
                Bukkit.getLogger().warning("[RecycleTable] AE detected but book-creation method not found — " +
                        "AE enchants will add processing time but won't return books on recycle.");
            }

        } catch (Exception e) {
            Bukkit.getLogger().warning("[RecycleTable] AdvancedEnchantments found but API hook failed — " +
                    "AE integration disabled. (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    /** Returns true if AE is installed and the API was hooked successfully. */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the AE enchantments on this item as a name → level map.
     * Returns an empty map if AE is not loaded or the item has none.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Integer> getEnchants(ItemStack item) {
        if (!loaded || methodGetEnchants == null || item == null) return Collections.emptyMap();
        try {
            Object result = methodGetEnchants.invoke(null, item);
            if (result instanceof Map) return (Map<String, Integer>) result;
        } catch (Exception ignored) {}
        return Collections.emptyMap();
    }

    /** Returns true if this item carries at least one AE enchantment. */
    public static boolean hasAEEnchants(ItemStack item) {
        return !getEnchants(item).isEmpty();
    }

    /**
     * Creates one enchanted book per AE enchantment on the item, at the same
     * level it was found (no level reduction — handled as a TODO for later).
     * Returns an empty list if AE is not loaded, the item has no AE enchants,
     * or the book-creation method could not be resolved.
     */
    public static List<ItemStack> getEnchantmentBooks(ItemStack item) {
        if (!loaded || methodGetBook == null) return Collections.emptyList();
        Map<String, Integer> enchants = getEnchants(item);
        if (enchants.isEmpty()) return Collections.emptyList();

        List<ItemStack> books = new ArrayList<>();
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            try {
                Object book = methodGetBook.invoke(null, e.getKey(), e.getValue());
                if (book instanceof ItemStack) books.add((ItemStack) book);
            } catch (Exception ignored) {}
        }
        return books;
    }
}
