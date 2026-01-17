package me.ryanhamshire.GPFlags.listener;

import io.papermc.paper.event.entity.EntityMoveEvent;
import me.ryanhamshire.GPFlags.util.Util;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Set;

public class EntityMoveListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    private void onMove(EntityMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Set<Player> group = Util.getMovementGroup(event.getEntity());
        if (PlayerListener.flagsPreventMovement(to, from, group)) {
            event.setCancelled(true);
        }
    }
}