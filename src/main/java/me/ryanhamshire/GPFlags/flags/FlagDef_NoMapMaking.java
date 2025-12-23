package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Arrays;
import java.util.List;

public class FlagDef_NoMapMaking extends FlagDefinition {

    public FlagDef_NoMapMaking(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler
    public void onMapMake(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), player);
        if (flag == null) return;

        if (event.getAction() == Action.LEFT_CLICK_AIR) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) return;
        if (event.getMaterial() != Material.MAP) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, null);
        if (Util.shouldBypass(player, claim, flag)) return;
        if (claim != null) {
            try {
                boolean isTrusted =
                        claim.allowBuild(player, event.getMaterial()) == null || // full build trust
                                claim.allowContainers(player) == null ||                 // container trust
                                claim.allowAccess(player) == null;                       // access trust

                if (isTrusted) return;
            } catch (NoSuchMethodError | AbstractMethodError ignored) {
            }
        }

        MessagingUtil.sendMessage(player, TextMode.Err, Messages.MapMakingDisabled);
        event.setCancelled(true);
    }

    @Override
    public String getName() {
        return "NoMapMaking";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoMapMaking);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoMapMaking);
    }

}
