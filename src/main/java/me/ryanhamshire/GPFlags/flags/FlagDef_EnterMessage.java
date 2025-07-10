package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.SetFlagResult;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class FlagDef_EnterMessage extends PlayerMovementFlagDefinition {

    private final String prefix;

    public FlagDef_EnterMessage(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        this.prefix = plugin.getFlagsDataStore().getMessage(Messages.EnterExitPrefix);
    }

    @Override
    public void onChangeClaim(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        if (flagTo == null) return;
        if (flagFrom != null && flagFrom.parameters.equals(flagTo.parameters)) return;
        sendMessage(flagTo, player, claimTo);
    }

    public void sendMessage(Flag flag, Player player, Claim claim) {
        String message = flag.parameters;
        if (claim != null) {
            String ownerName = claim.getOwnerName();
            if (ownerName != null) {
                message = message.replace("%owner%", ownerName);
            }
        }
        message = message.replace("%name%", player.getName());
        String converted = MessagingUtil.reserialize(TextMode.Info + prefix + message);
        MessagingUtil.sendMessage(player, converted);
    }

    @Override
    public String getName() {
        return "EnterMessage";
    }

    @Override
    public SetFlagResult validateParameters(String parameters, CommandSender sender) {
        if (parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MessageRequired));
        }
        return new SetFlagResult(true, this.getSetMessage(parameters));
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.AddedEnterMessage, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.RemovedEnterMessage);
    }

    public static String legacyToMiniMessage(String input) {
        if (input == null) return "";

        // Convert hex codes (&#RRGGBB or #RRGGBB) to MiniMessage
        input = input.replaceAll("(?i)(?:&|§)?#([A-Fa-f0-9]{6})", "<#$1>");

        // Remove all unsupported formatting codes
        input = input.replaceAll("(?i)&[k-mnr]", "");

        // Convert color codes
        input = input.replaceAll("(?i)&0", "<black>");
        input = input.replaceAll("(?i)&1", "<dark_blue>");
        input = input.replaceAll("(?i)&2", "<dark_green>");
        input = input.replaceAll("(?i)&3", "<dark_aqua>");
        input = input.replaceAll("(?i)&4", "<dark_red>");
        input = input.replaceAll("(?i)&5", "<dark_purple>");
        input = input.replaceAll("(?i)&6", "<gold>");
        input = input.replaceAll("(?i)&7", "<gray>");
        input = input.replaceAll("(?i)&8", "<dark_gray>");
        input = input.replaceAll("(?i)&9", "<blue>");
        input = input.replaceAll("(?i)&a", "<green>");
        input = input.replaceAll("(?i)&b", "<aqua>");
        input = input.replaceAll("(?i)&c", "<red>");
        input = input.replaceAll("(?i)&d", "<light_purple>");
        input = input.replaceAll("(?i)&e", "<yellow>");
        input = input.replaceAll("(?i)&f", "<white>");

        // Convert &l and &o to MiniMessage tags
        input = input.replaceAll("(?i)&l", "<bold>");
        input = input.replaceAll("(?i)&o", "<italic>");

        return input;
    }
}