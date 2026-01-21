package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import me.ryanhamshire.GPFlags.util.MessagingUtil;

public class FlagDef_NoSetBedSpawn extends FlagDefinition {

    public FlagDef_NoSetBedSpawn(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSpawnChange(PlayerSetSpawnEvent event) {
        // Only handle bed respawn point changes
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED) return;

        Player player = event.getPlayer();

        Flag flag = this.getFlagInstanceAtLocation(event.getLocation(), player);
        if (flag == null) return;

        MessagingUtil.sendMessage(player, TextMode.Err, Messages.SetBedSpawnDisabled);
        event.setCancelled(true);
    }

    @Override
    public String getName() {
        return "NoSetBedSpawn";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoSetBedSpawn);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoSetBedSpawn);
    }

}