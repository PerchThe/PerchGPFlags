package me.ryanhamshire.GPFlags.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlaceholderApiHook extends PlaceholderExpansion {

    private final GPFlags plugin;

    public PlaceholderApiHook(GPFlags plugin) {
        this.plugin = plugin;
    }

    /** Used to add other plugin's placeholders to GPFlags messages */
    public static String addPlaceholders(OfflinePlayer player, String message) {
        return PlaceholderAPI.setPlaceholders(player, message);
    }

    @Override public @NotNull String getIdentifier() { return "gpflags"; }
    @Override public @NotNull String getAuthor() { return plugin.getDescription().getAuthors().toString(); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        identifier = identifier.toLowerCase(Locale.ROOT);
        if (!(offlinePlayer instanceof Player)) return null;
        Player player = (Player) offlinePlayer;

        // ---- Simple limit/count API
        if (identifier.equals("nomobspawns_limit")) {
            return String.valueOf(getCapFromPermissions(player));
        }
        if (identifier.equals("nomobspawns_count")) {
            return String.valueOf(listFlaggedClaims(player.getUniqueId()).size());
        }

        // ---- Booleans for DeluxeMenus (avoid number-vs-number compares)
        if (identifier.equals("nomobspawns_at_limit")) {
            int limit = getCapFromPermissions(player);
            if (limit == Integer.MAX_VALUE) return "No";
            int count = listFlaggedClaims(player.getUniqueId()).size();
            return (limit > 0 && count >= limit) ? "Yes" : "No";
        }
        if (identifier.equals("nomobspawns_has_limit")) {
            int limit = getCapFromPermissions(player);
            return (limit == Integer.MAX_VALUE || limit > 0) ? "Yes" : "No";
        }
        if (identifier.equals("nomobspawns_under_limit")) {
            int limit = getCapFromPermissions(player);
            if (limit == Integer.MAX_VALUE) return "Yes";
            int count = listFlaggedClaims(player.getUniqueId()).size();
            return (count < limit) ? "Yes" : "No";
        }

        // ---- Coords for first/second/third flagged claims
        if (identifier.equals("nomobspawns_claim_1")
                || identifier.equals("nomobspawns_claim_2")
                || identifier.equals("nomobspawns_claim_3")) {

            int index = identifier.charAt(identifier.length() - 1) - '1'; // 0..2
            List<Claim> claims = listFlaggedClaims(player.getUniqueId());
            if (index < 0 || index >= claims.size()) return "";
            return niceLoc(getClaimCenter(claims.get(index)));
        }

        // ---- Existing GPFlags placeholders (unchanged)
        if (identifier.startsWith("cansetclaimflag_")) {
            String flagName = identifier.substring(identifier.indexOf('_') + 1);

            if (!player.hasPermission("gpflags.flag." + flagName)) {
                MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoFlagPermission, flagName);
                return "No";
            }

            FlagDefinition def = plugin.getFlagManager().getFlagDefinitionByName(flagName);
            if (def == null || !def.getFlagType().contains(FlagDefinition.FlagType.CLAIM)) {
                MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoFlagInClaim);
                return "No";
            }

            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            if (claim == null) return "No";

            if (!Util.canEdit(player, claim)) {
                MessagingUtil.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
                return "No";
            }
            return "Yes";
        }

        if (identifier.startsWith("isflagactive_")) {
            String flagName = identifier.substring(identifier.indexOf('_') + 1);
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            Flag flag = plugin.getFlagManager().getEffectiveFlag(player.getLocation(), flagName, claim);
            return (flag == null) ? "No" : "Yes";
        }

        return null;
    }

    // ===== Helpers =====

    private List<Claim> listFlaggedClaims(UUID owner) {
        List<Claim> out = new ArrayList<Claim>();
        PlayerData pd = GriefPrevention.instance.dataStore.getPlayerData(owner);
        if (pd == null) return out;
        for (Claim c : pd.getClaims()) {
            if (c != null && isNoMobSpawnsActiveIn(c)) out.add(c);
        }
        return out;
    }

    private boolean isNoMobSpawnsActiveIn(Claim claim) {
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

    // NON-HIERARCHICAL: each permission adds +1 slot
    private int getCapFromPermissions(Player subject) {
        if (subject == null) return 0;
        if (subject.hasPermission("nomobspawns.unlimited") || subject.hasPermission("nomobspawns.bypass"))
            return Integer.MAX_VALUE;

        int cap = 0;
        if (subject.hasPermission("nomobspawns.claim.1")) cap++;
        if (subject.hasPermission("nomobspawns.claim.2")) cap++;
        if (subject.hasPermission("nomobspawns.claim.3")) cap++;
        return cap;
    }

    private String niceLoc(Location l) {
        return l.getWorld().getName() + ": " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private Location getClaimCenter(Claim c) {
        World w = c.getLesserBoundaryCorner().getWorld();
        int x = (c.getLesserBoundaryCorner().getBlockX() + c.getGreaterBoundaryCorner().getBlockX()) / 2;
        int y = (c.getLesserBoundaryCorner().getBlockY() + c.getGreaterBoundaryCorner().getBlockY()) / 2;
        int z = (c.getLesserBoundaryCorner().getBlockZ() + c.getGreaterBoundaryCorner().getBlockZ()) / 2;
        return new Location(w, x, y, z);
    }
}
