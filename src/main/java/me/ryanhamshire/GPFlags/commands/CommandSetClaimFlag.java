package me.ryanhamshire.GPFlags.commands;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.flags.FlagDef_ChangeBiome;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CommandSetClaimFlag implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!commandSender.hasPermission("gpflags.command.setclaimflag")) {
            MessagingUtil.sendMessage(commandSender, TextMode.Err, Messages.NoCommandPermission, command.toString());
            return true;
        }
        if (!(commandSender instanceof Player)) {
            MessagingUtil.sendMessage(commandSender, TextMode.Warn, Messages.PlayerOnlyCommand, command.toString());
            return true;
        }
        Player player = (Player) commandSender;

        if (args.length < 1) return false;
        String flagName = args[0];

        GPFlags gpflags = GPFlags.getInstance();
        FlagDefinition def = gpflags.getFlagManager().getFlagDefinitionByName(flagName);
        if (def == null) {
            MessagingUtil.sendMessage(player, TextMode.Warn, Messages.InvalidFlagDefName, Util.getAvailableFlags(player));
            return true;
        }

        if (!player.hasPermission("gpflags.flag." + flagName)) {
            MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoFlagPermission, flagName);
            return true;
        }

        if (!def.getFlagType().contains(FlagDefinition.FlagType.CLAIM)) {
            MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoFlagInClaim);
            return true;
        }

        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        Location loc = player.getLocation();

        // IMPORTANT: resolve the *innermost* subclaim at the player's location (3D-aware)
        Claim claim = getInnermostClaimAt(loc, playerData.lastClaim);
        if (claim == null) {
            MessagingUtil.sendMessage(commandSender, TextMode.Err, Messages.StandInAClaim);
            return true;
        }

        if (!Util.canEdit(player, claim)) {
            MessagingUtil.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
            return true;
        }

        String[] params = new String[args.length - 1];
        System.arraycopy(args, 1, params, 0, args.length - 1);

        Collection<Flag> flags = gpflags.getFlagManager().getFlags(claim.getID().toString());
        for (Flag flag : flags) {
            if (flagName.equalsIgnoreCase("OwnerFly")) {
                if (flag.getFlagDefinition().getName().equalsIgnoreCase("OwnerMemberFly")) {
                    MessagingUtil.sendMessage(player, TextMode.Warn, Messages.NoOwnerFlag);
                    return true;
                }
            }
            if (flagName.equalsIgnoreCase("OwnerMemberFly")) {
                if (flag.getFlagDefinition().getName().equalsIgnoreCase("OwnerFly")) {
                    MessagingUtil.sendMessage(player, TextMode.Warn, Messages.NoOwnerFlag);
                    return true;
                }
            }
        }

        if (flagName.equalsIgnoreCase("ChangeBiome")) {
            if (args.length < 2) return false;
            FlagDef_ChangeBiome flagD = ((FlagDef_ChangeBiome) gpflags.getFlagManager().getFlagDefinitionByName("changebiome"));
            String biome = params[0].toUpperCase().replace(" ", "_");
            if (!flagD.changeBiome(commandSender, claim, biome)) return true;
        }

        if (flagName.equalsIgnoreCase("NoMobSpawnsType")) {
            if (params.length == 0) return false;
            for (String type : params[0].split(";")) {
                if (!player.hasPermission("gpflags.flag.nomobspawnstype." + type)) {
                    MessagingUtil.sendMessage(player, TextMode.Err, Messages.MobTypePerm, type);
                    return true;
                }
            }
        }

        Long claimID = claim.getID();
        if (claimID == null || claimID == -1) {
            MessagingUtil.sendMessage(player, TextMode.Err, Messages.UpdateGPForSubdivisionFlags);
            return true;
        }

        SetFlagResult result = gpflags.getFlagManager().setFlag(claimID.toString(), def, true, commandSender, params);
        String color = result.isSuccess() ? TextMode.Success : TextMode.Err;
        MessagingUtil.sendMessage(player, color, result.getMessage().getMessageID(), result.getMessage().getMessageParams());
        if (result.isSuccess()) gpflags.getFlagManager().save();

        return true;
    }

    /**
     * Resolve the deepest subclaim that contains the given location.
     * Uses 4-arg getClaimAt(loc, ignoreHeight, excludeSubdivisions, cache) when available,
     * otherwise falls back to 3-arg and then walks children.
     */
    private Claim getInnermostClaimAt(@NotNull Location loc, @Nullable Claim cache) {
        Claim base;
        try {
            // Prefer signature that explicitly INCLUDES subdivisions and respects 3D height
            base = GriefPrevention.instance.dataStore.getClaimAt(loc, false, false, cache);
        } catch (Throwable t) {
            // Fallback signature
            base = GriefPrevention.instance.dataStore.getClaimAt(loc, false, cache);
        }
        if (base == null) return null;

        Claim current = base;
        while (true) {
            Claim childHit = null;
            if (current.children != null && !current.children.isEmpty()) {
                for (Claim child : current.children) {
                    if (child != null && child.inDataStore && child.contains(loc, false, false)) {
                        childHit = child;
                        break;
                    }
                }
            }
            if (childHit == null) break;
            current = childHit;
        }
        return current;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 1) {
            return Util.flagTab(commandSender, args[0]);
        } else if (args.length == 2) {
            return Util.paramTab(commandSender, args);
        }
        return Collections.emptyList();
    }
}
