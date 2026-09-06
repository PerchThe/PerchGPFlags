package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
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
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED) return;

        Player player = event.getPlayer();
        Location location = event.getLocation();

        if (location == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);

        if (claim != null && player.getUniqueId().equals(claim.getOwnerID())) {
            return;
        }

        Flag flag = this.getFlagInstanceAtLocation(location, player);
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