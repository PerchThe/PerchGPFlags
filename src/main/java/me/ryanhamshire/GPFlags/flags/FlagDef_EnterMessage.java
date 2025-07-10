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
import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        MessagingUtil.sendMessage(player, colorize(TextMode.Info + prefix + message));
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

    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fA-FlLoO])");

    public static String colorize(String input) {
        if (input == null) return null;
        Matcher hexMatcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder legacy = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                legacy.append('§').append(c);
            }
            hexMatcher.appendReplacement(buffer, legacy.toString());
        }
        hexMatcher.appendTail(buffer);
        String processed = buffer.toString();
        processed = COLOR_CODE_PATTERN.matcher(processed).replaceAll(match -> {
            String code = match.group(1).toLowerCase();
            if ("l".equals(code)) return ChatColor.BOLD.toString();
            if ("o".equals(code)) return ChatColor.ITALIC.toString();
            return ChatColor.getByChar(code).toString();
        });
        processed = processed.replaceAll("&[kKmMnNrR]", "");
        return processed;
    }
}