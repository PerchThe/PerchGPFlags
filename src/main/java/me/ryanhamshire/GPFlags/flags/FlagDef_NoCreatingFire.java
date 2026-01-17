package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockIgniteEvent;

public class FlagDef_NoCreatingFire extends FlagDefinition {

    public FlagDef_NoCreatingFire(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();

        Flag flag = this.getFlagInstanceAtLocation(block.getLocation(), null);
        if (flag == null) return;

        event.setCancelled(true);
    }

    @Override
    public String getName() {
        return "NoCreatingFire";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoCreatingFire);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoCreatingFire);
    }
}