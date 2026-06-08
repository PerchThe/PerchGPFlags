package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

public class FlagDef_NoBlockGravity extends FlagDefinition {

    public FlagDef_NoBlockGravity(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler
    private void onBlockFall(EntityChangeBlockEvent event){
        if (this.getFlagInstanceAtLocation(event.getBlock().getLocation(), null) == null) return;
        if (event.getEntityType() == EntityType.FALLING_BLOCK) {

            Entity entity = event.getEntity();
            if (entity.hasMetadata("GP_FALLINGBLOCK")) {
                Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
                if (gp != null) {
                    entity.removeMetadata("GP_FALLINGBLOCK", gp);
                }
            }

            event.setCancelled(true);
            event.getBlock().getState().update(false, false);
        }
    }

    @Override
    public String getName() {
        return "NoBlockGravity";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoBlockGravity);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoBlockGravity);
    }

}