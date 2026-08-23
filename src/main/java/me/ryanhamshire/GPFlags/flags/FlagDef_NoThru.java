package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.List;

public class FlagDef_NoThru extends FlagDefinition {

    private static final List<String> BLOCKED_COMMANDS = Arrays.asList(
            "/j",
            "/jump",
            "/thru",
            "/essentials:jump",
            "/essentials:j",
            "/worldedit:thru",
            "/ejump",
            "/ejumpto",
            "/essentials:ejump",
            "/essentials:ejumpto"
    );

    public FlagDef_NoThru(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), player);
        if (flag == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) return;

        if (claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId())) {
            return;
        }

        if (player.hasPermission("gpflags.bypass.NoThru")) {
            return;
        }

        if (isBlockedCommand(event.getMessage())) {
            event.setCancelled(true);
            MessagingUtil.sendMessage(player, TextMode.Err, Messages.CommandBlockedHere);
        }
    }

    private boolean isBlockedCommand(String message) {
        if (message == null || message.isEmpty()) return false;

        String command = message.toLowerCase().trim();

        int spaceIndex = command.indexOf(' ');
        if (spaceIndex != -1) {
            command = command.substring(0, spaceIndex);
        }

        return BLOCKED_COMMANDS.contains(command);
    }

    @Override
    public String getName() {
        return "NoThru";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableCommandBlackList);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableCommandBlackList);
    }
}