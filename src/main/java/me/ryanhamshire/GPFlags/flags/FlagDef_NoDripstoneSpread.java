package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockSpreadEvent;

public class FlagDef_NoDripstoneSpread extends FlagDefinition {

    public FlagDef_NoDripstoneSpread(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Block targetBlock = event.getBlock();

        boolean createsPointedDripstone =
                event.getNewState().getType() == Material.POINTED_DRIPSTONE;

        boolean affectsExistingPointedDripstone =
                targetBlock.getType() == Material.POINTED_DRIPSTONE;

        boolean comesFromPointedDripstone =
                event.getSource().getType() == Material.POINTED_DRIPSTONE;

        if (!createsPointedDripstone && !affectsExistingPointedDripstone && !comesFromPointedDripstone) {
            return;
        }

        Flag flag = this.getFlagInstanceAtLocation(targetBlock.getLocation(), null);
        if (flag == null) return;

        event.setCancelled(true);
    }

    @Override
    public String getName() {
        return "NoDripstoneSpread";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoDripstoneSpread);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoDripstoneSpread);
    }
}