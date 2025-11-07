package me.mizfit.recycletable;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        RecycleSession s = SessionManager.getSession(id);
        if (s != null && s.isActive()) {
            long mins = s.getTimeLeft() / 1200L; // ticks -> minutes
            e.getPlayer().sendMessage(ChatColor.YELLOW + "Your recycling process has resumed. Time left: " + mins + " minutes.");
        }
    }
}

