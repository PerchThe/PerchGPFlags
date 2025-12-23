package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GriefPrevention.Claim;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FlagDef_ExitActionbar extends PlayerMovementFlagDefinition {

    public static final String PERM_DISABLE_EXIT_ACTIONBAR = "gpflags.actionbar.disable.exit";

    public FlagDef_ExitActionbar(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public void onChangeClaim(
            Player player,
            Location lastLocation,
            Location to,
            Claim claimFrom,
            Claim claimTo,
            @Nullable Flag flagFrom,
            @Nullable Flag flagTo
    ) {
        if (player.hasPermission(PERM_DISABLE_EXIT_ACTIONBAR)) return;

        if (flagFrom == null) return;
        if (flagTo != null && Objects.equals(flagTo.parameters, flagFrom.parameters)) return;
        sendActionbar(flagFrom, player, claimFrom);
    }

    private void sendRepeated(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        int repeats = 4;
        int interval = 2;
        for (int i = 1; i <= repeats; i++) {
            int delay = i * interval;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                }
            }, delay);
        }
    }

    public void sendActionbar(Flag flag, Player player, @Nullable Claim claim) {
        // Extra safeguard to prevent sending from any direct calls
        if (player.hasPermission(PERM_DISABLE_EXIT_ACTIONBAR)) return;

        String message = flag.parameters == null ? "" : flag.parameters;
        if (claim != null) {
            String owner = claim.getOwnerName();
            message = message.replace("%owner%", owner != null ? owner : "");
        }
        message = message.replace("%name%", player.getName());
        if (!message.isEmpty()) {
            String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
            Bukkit.getScheduler().runTask(plugin, () -> sendRepeated(player, colored));
        }
    }

    @Override
    public String getName() {
        return "ExitActionbar";
    }

    @Override
    public SetFlagResult validateParameters(String parameters, CommandSender sender) {
        if (parameters == null || parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.ActionbarRequired));
        }
        return new SetFlagResult(true, getSetMessage(parameters));
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.AddedExitActionbar, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.RemovedExitActionbar);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT, FlagType.WORLD, FlagType.SERVER);
    }
}
