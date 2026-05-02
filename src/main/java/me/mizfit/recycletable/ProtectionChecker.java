package me.mizfit.recycletable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Checks whether a player has trusted access to a location via installed protection plugins.
 *
 * Uses soft-dependency reflection so RecycleTable compiles and runs with no protection
 * plugin installed at all.
 *
 * Strategy:
 *   GriefPrevention  — actively queried via allowBuild() reflection.
 *   WorldGuard /
 *   Towny / Lands /
 *   GriefDefender    — handled implicitly: TableListener and PlaceListener register at
 *                      EventPriority.HIGH. If one of these plugins cancelled the event
 *                      before our handler fires, we return early and respect the denial.
 *                      If the event was NOT cancelled, the protection plugin has implicitly
 *                      approved the interaction, so isTrusted() returns true.
 */
public class ProtectionChecker {

    /**
     * Returns true if a supported protection plugin confirms the player has access.
     * Should only be called after verifying the event was NOT already cancelled.
     */
    public static boolean isTrusted(Player player, Location location) {
        // GriefPrevention: actively query claim trust
        Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (gp != null && gp.isEnabled()) {
            return isGriefPreventionTrusted(player, location);
        }

        // All other supported plugins are handled by event-priority cancellation.
        // Reaching here with a non-cancelled event means they approved.
        return hasOtherProtectionPlugin();
    }

    /** Returns true if any recognized protection plugin is loaded on this server. */
    public static boolean isAnyPluginActive() {
        return Bukkit.getPluginManager().getPlugin("GriefPrevention") != null
                || hasOtherProtectionPlugin();
    }

    private static boolean hasOtherProtectionPlugin() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard")      != null
                || Bukkit.getPluginManager().getPlugin("Towny")       != null
                || Bukkit.getPluginManager().getPlugin("Lands")       != null
                || Bukkit.getPluginManager().getPlugin("GriefDefender") != null;
    }

    /**
     * Queries GriefPrevention's allowBuild() via reflection.
     * Returns true (trusted) if GP says the player may build at the location.
     * Fails open on reflection errors so a GP API change never hard-locks players out.
     */
    private static boolean isGriefPreventionTrusted(Player player, Location location) {
        try {
            Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (gp == null) return false;
            // GriefPrevention.instance.allowBuild(player, location, material)
            // → returns null if the player is allowed, or a denial reason String if not
            Object instance = gp.getClass().getField("instance").get(null);
            Method allowBuild = instance.getClass().getMethod(
                    "allowBuild", Player.class, Location.class, Material.class);
            Object result = allowBuild.invoke(instance, player, location, Material.AIR);
            return result == null;
        } catch (Throwable t) {
            // Reflection failed (GP updated its API, etc.) — fail open
            return true;
        }
    }
}
