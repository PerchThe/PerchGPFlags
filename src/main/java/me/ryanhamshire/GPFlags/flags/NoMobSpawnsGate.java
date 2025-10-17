package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.UUID;

public class NoMobSpawnsGate implements Listener {

    private final GPFlags plugin;

    public NoMobSpawnsGate(GPFlags plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCmd(PlayerCommandPreprocessEvent e) {
        handleSetFlagCommand(e.getPlayer(), e.getMessage(), e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCmd(ServerCommandEvent e) {
        handleSetFlagCommand(e.getSender(), e.getCommand(), e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRconCmd(RemoteServerCommandEvent e) {
        handleSetFlagCommand(e.getSender(), e.getCommand(), e);
    }

    private void handleSetFlagCommand(CommandSender actor, String raw, Cancellable cancellable) {
        if (raw == null) return;

        String msg = raw.trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        msg = msg.replaceAll("\\s+", " ");

        String lower = msg.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("setclaimflagplayer ")) return;

        String[] parts = msg.split(" ");
        if (parts.length < 3) return;

        String subjectName = parts[1];
        String flagName = parts[2];

        if (!flagName.equalsIgnoreCase("NoMonsterSpawns") && !flagName.equalsIgnoreCase("NoMobSpawns")) return;

        // Optional slot index (purely cosmetic for messages; cap logic does not require it)
        Integer requestedIndex = null;
        if (parts.length >= 4) {
            try { requestedIndex = Integer.parseInt(parts[3]); } catch (NumberFormatException ignored) {}
        }

        Player subject = Bukkit.getPlayerExact(subjectName);
        if (subject == null) {
            if (actor != null) actor.sendMessage("§cPlayer must be online to enable §eNo Monster Spawns§c.");
            cancellable.setCancelled(true);
            return;
        }

        Claim claim = getClaimAtPlayer(subject);
        if (claim == null) {
            subject.sendMessage("§cYou are not inside a claim.");
            if (actor != null && actor != subject) actor.sendMessage("§cPlayer is not inside a claim.");
            cancellable.setCancelled(true);
            return;
        }

        UUID ownerId = claim.getOwnerID();
        if (ownerId == null || !ownerId.equals(subject.getUniqueId())) {
            subject.sendMessage("§cOnly the claim owner can enable §eNo Monster Spawns§c here.");
            if (actor != null && actor != subject) actor.sendMessage("§cOnly the claim owner can enable it.");
            cancellable.setCancelled(true);
            return;
        }

        // Unlimited/bypass short-circuit
        if (subject.hasPermission("nomobspawns.unlimited") || subject.hasPermission("nomobspawns.bypass")) {
            return;
        }

        int cap = getCapFromPermissions(subject); // 0/1/2/3 or MAX_VALUE
        if (cap <= 0) {
            subject.sendMessage("§cYou are not allowed to enable §eNo Monster Spawns§c on any claim.");
            if (actor != null && actor != subject) actor.sendMessage("§cThat player has no allowance for this flag.");
            cancellable.setCancelled(true);
            return;
        }

        int already = countOtherFlaggedClaims(ownerId, claim);
        if (already >= cap) {
            String extra = "";
            if (requestedIndex != null) extra = " (slot #" + requestedIndex + ")";
            String msgCap = "§cYou already have §f" + already + "§c claim" + (already == 1 ? "" : "s")
                    + " with §eNo Monster Spawns§c enabled (limit: §f" + cap + "§c)" + extra + ".";
            subject.sendMessage(msgCap);
            if (actor != null && actor != subject) actor.sendMessage(msgCap);
            cancellable.setCancelled(true);
        }
    }

    private Claim getClaimAtPlayer(Player p) {
        PlayerData pd = GriefPrevention.instance.dataStore.getPlayerData(p.getUniqueId());
        return GriefPrevention.instance.dataStore.getClaimAt(p.getLocation(), false, pd.lastClaim);
    }

    private int getCapFromPermissions(Player subject) {
        if (subject == null) return 0;
        if (subject.hasPermission("nomobspawns.unlimited") || subject.hasPermission("nomobspawns.bypass"))
            return Integer.MAX_VALUE;

        int cap = 0;

        if (subject.hasPermission("nomobspawns.claim.1")) cap++;
        if (subject.hasPermission("nomobspawns.claim.2")) cap++;
        if (subject.hasPermission("nomobspawns.claim.3")) cap++;

        return 0;
    }

    private int countOtherFlaggedClaims(UUID owner, Claim except) {
        PlayerData pd = GriefPrevention.instance.dataStore.getPlayerData(owner);
        if (pd == null) return 0;
        int count = 0;
        for (Claim c : pd.getClaims()) {
            if (c == null || c.equals(except)) continue;
            if (isFlagActiveInClaim(c)) count++;
        }
        return count;
    }

    private boolean isFlagActiveInClaim(Claim claim) {
        World w = claim.getLesserBoundaryCorner().getWorld();
        if (w == null) return false;

        Location center = getClaimCenter(claim);
        Location lb = claim.getLesserBoundaryCorner().clone().add(1, 1, 1);
        Location gb = claim.getGreaterBoundaryCorner().clone().add(-1, 1, -1);

        if (plugin.getFlagManager().getEffectiveFlag(center, "NoMonsterSpawns", claim) != null) return true;
        if (plugin.getFlagManager().getEffectiveFlag(lb,     "NoMonsterSpawns", claim) != null) return true;
        if (plugin.getFlagManager().getEffectiveFlag(gb,     "NoMonsterSpawns", claim) != null) return true;

        if (plugin.getFlagManager().getEffectiveFlag(center, "NoMobSpawns", claim) != null) return true;
        if (plugin.getFlagManager().getEffectiveFlag(lb,     "NoMobSpawns", claim) != null) return true;
        if (plugin.getFlagManager().getEffectiveFlag(gb,     "NoMobSpawns", claim) != null) return true;

        return false;
    }

    private Location getClaimCenter(Claim c) {
        World w = c.getLesserBoundaryCorner().getWorld();
        int x = (c.getLesserBoundaryCorner().getBlockX() + c.getGreaterBoundaryCorner().getBlockX()) / 2;
        int y = (c.getLesserBoundaryCorner().getBlockY() + c.getGreaterBoundaryCorner().getBlockY()) / 2;
        int z = (c.getLesserBoundaryCorner().getBlockZ() + c.getGreaterBoundaryCorner().getBlockZ()) / 2;
        return new Location(w, x, y, z);
    }
}
