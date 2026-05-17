package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_NoEnter extends PlayerMovementFlagDefinition {

    // Map to store the last time a message was sent to a player
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MILLIS = 3000L; // 3 seconds

    public FlagDef_NoEnter(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    // Helper method to handle rate-limiting the message
    private void sendRateLimitedMessage(Player player) {
        long currentTime = System.currentTimeMillis();
        long lastTime = messageCooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - lastTime >= COOLDOWN_MILLIS) {
            MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoEnterMessage);
            messageCooldowns.put(player.getUniqueId(), currentTime);
        }
    }

    @Override
    public void onFlagSet(Claim claim, String string) {
        World world = claim.getLesserBoundaryCorner().getWorld();
        for (Player p : world.getPlayers()) {
            if (claim.contains(Util.getInBoundsLocation(p), false, false)) {
                if (!Util.canAccess(claim, p) && !p.hasPermission("gpflags.bypass.noenter")) {
                    GriefPrevention.instance.ejectPlayer(p);
                    sendRateLimitedMessage(p); // Added so they know why they were ejected
                }
            }
        }
    }

    @Override
    public boolean allowMovement(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo) {
        Flag flag = getEffectiveFlag(claimTo, to);
        if (isAllowed(flag, claimTo, player)) return true;

        sendRateLimitedMessage(player);
        return false;
    }

    @Override
    public void onChangeClaim(@NotNull Player player, @Nullable Location from, @NotNull Location to, @Nullable Claim claimFrom, @Nullable Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        if (isAllowed(flagTo, claimTo, player)) return;

        sendRateLimitedMessage(player);
        GriefPrevention.instance.ejectPlayer(player);
    }

    private boolean isAllowed(Flag flag, Claim claim, Player player) {
        if (flag == null) return true;
        if (Util.canAccess(claim, player)) return true;
        if (player.hasPermission("gpflags.bypass.noenter")) return true;
        return false;
    }

    @Override
    public String getName() {
        return "NoEnter";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledNoEnter, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoEnter);
    }

}