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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_RequireEmptyInvOnEnter extends PlayerMovementFlagDefinition {

    public static final String BYPASS_PERMISSION = "gpflags.bypass.requireemptyenter";
    public static final String DEBUG_PERMISSION = "gpflags.debug.requireemptyenter";

    private static final long WARMUP_TICKS = 100L;
    private static final long JOIN_GRACE_MS = 5000L;
    private static final long TELEPORT_GRACE_MS = 1500L;

    private final Map<UUID, Long> lastRootClaimId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> graceUntil = new ConcurrentHashMap<>();

    private volatile boolean isWarmingUp = true;

    public FlagDef_RequireEmptyInvOnEnter(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> isWarmingUp = false, WARMUP_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        graceUntil.put(p.getUniqueId(), System.currentTimeMillis() + JOIN_GRACE_MS);
        seedPlayerRoot(p, p.getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastRootClaimId.remove(id);
        graceUntil.remove(id);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Player p = event.getPlayer();
        graceUntil.put(p.getUniqueId(), System.currentTimeMillis() + TELEPORT_GRACE_MS);

        Bukkit.getScheduler().runTask(this.plugin, () -> seedPlayerRoot(p, to));
    }

    private void seedPlayerRoot(Player player, Location loc) {
        Location inBounds = Util.getInBoundsLocation(loc);
        Claim here = GriefPrevention.instance.dataStore.getClaimAt(inBounds, false, null);
        Claim root = getRootClaim(here);
        if (root == null) lastRootClaimId.remove(player.getUniqueId());
        else lastRootClaimId.put(player.getUniqueId(), root.getID());
    }

    @Override
    public void onFlagSet(Claim claim, String string) {
        Claim rootSet = getRootClaim(claim);
        if (rootSet == null) return;

        Location lesser = rootSet.getLesserBoundaryCorner();
        for (Player p : lesser.getWorld().getPlayers()) {
            Location loc = Util.getInBoundsLocation(p);
            Claim here = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
            if (here == null) continue;

            Claim rootHere = getRootClaim(here);
            if (rootHere == null) continue;

            if (!Objects.equals(rootHere.getID(), rootSet.getID())) continue;

            lastRootClaimId.put(p.getUniqueId(), rootSet.getID());
        }
    }

    @Override
    public boolean allowMovement(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo) {
        Claim rootFrom = getRootClaim(claimFrom);
        Claim rootTo = getRootClaim(claimTo);

        if (isWarmingUp || isInGrace(player)) {
            updateCache(player, rootTo);
            return true;
        }

        boolean enteringRoot = rootTo != null && (rootFrom == null || !Objects.equals(rootFrom.getID(), rootTo.getID()));
        if (!enteringRoot) {
            updateCache(player, rootTo);
            return true;
        }

        Flag effective = getEffectiveFlag(rootTo, to);
        if (isAllowed(effective, rootTo, player)) {
            lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
            return true;
        }

        MessagingUtil.sendMessage(player, TextMode.Err, Messages.RequireEmptyInvOnEnterDenied);
        if (player.hasPermission(DEBUG_PERMISSION)) dumpInventoryState(player, "allowMovement");
        return false;
    }

    @Override
    public void onChangeClaim(@Nullable Player player, @Nullable Location from, @Nullable Location to,
                              @Nullable Claim claimFrom, @Nullable Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        if (player == null || to == null) return;

        Claim rootFrom = getRootClaim(claimFrom);
        Claim rootTo = getRootClaim(claimTo);

        if (isWarmingUp || isInGrace(player)) {
            updateCache(player, rootTo);
            return;
        }

        boolean enteringRoot = rootTo != null && (rootFrom == null || !Objects.equals(rootFrom.getID(), rootTo.getID()));
        if (!enteringRoot) {
            updateCache(player, rootTo);
            return;
        }

        Flag effective = getEffectiveFlag(rootTo, to);
        if (isAllowed(effective, rootTo, player)) {
            lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
            return;
        }

        MessagingUtil.sendMessage(player, TextMode.Err, Messages.RequireEmptyInvOnEnterDenied);
        if (player.hasPermission(DEBUG_PERMISSION)) dumpInventoryState(player, "onChangeClaim");
        lastRootClaimId.remove(player.getUniqueId());
        GriefPrevention.instance.ejectPlayer(player);
    }

    private void updateCache(Player player, @Nullable Claim rootTo) {
        if (rootTo != null) lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
        else lastRootClaimId.remove(player.getUniqueId());
    }

    private boolean isInGrace(Player player) {
        Long until = graceUntil.get(player.getUniqueId());
        if (until == null) return false;
        long now = System.currentTimeMillis();
        if (now <= until) return true;
        graceUntil.remove(player.getUniqueId());
        return false;
    }

    private boolean isAllowed(@Nullable Flag flag, Claim claim, Player player) {
        if (flag == null) return true;
        if (Util.canAccess(claim, player)) return true;
        if (player.hasPermission(BYPASS_PERMISSION)) return true;
        return !storageHasAnyItem(player);
    }

    private boolean storageHasAnyItem(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        if (storage == null) return false;
        for (ItemStack is : storage) {
            if (isRealItem(is)) return true;
        }
        return false;
    }

    private boolean isRealItem(@Nullable ItemStack is) {
        if (is == null) return false;
        Material t = is.getType();
        if (t == null || t.isAir()) return false;
        return is.getAmount() > 0;
    }

    private void dumpInventoryState(Player player, String where) {
        StringBuilder sb = new StringBuilder();
        sb.append("RequireEmptyInvOnEnter deny dump [").append(where).append("] player=").append(player.getName()).append("\n");
        sb.append("storage=").append(describeArray(player.getInventory().getStorageContents())).append("\n");
        this.plugin.getLogger().warning(sb.toString());
    }

    private String describeArray(@Nullable ItemStack[] items) {
        if (items == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (int i = 0; i < items.length; i++) {
            ItemStack is = items[i];
            if (!isRealItem(is)) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(i).append(":").append(describe(is));
        }
        sb.append("]");
        return sb.toString();
    }

    private String describe(@Nullable ItemStack is) {
        if (is == null) return "null";
        Material t = is.getType();
        if (t == null || t.isAir()) return "AIR";
        String meta = is.hasItemMeta() ? "meta" : "nometa";
        return t.name() + "x" + is.getAmount() + ":" + meta;
    }

    private Claim getRootClaim(@Nullable Claim claim) {
        if (claim == null) return null;
        Claim cur = claim;
        while (true) {
            Claim parent = getParentClaim(cur);
            if (parent == null) return cur;
            cur = parent;
        }
    }

    private Claim getParentClaim(Claim claim) {
        try {
            Method m = claim.getClass().getMethod("getParent");
            Object o = m.invoke(claim);
            if (o instanceof Claim) return (Claim) o;
        } catch (Throwable ignored) {
        }

        try {
            Method m = claim.getClass().getMethod("getParentClaim");
            Object o = m.invoke(claim);
            if (o instanceof Claim) return (Claim) o;
        } catch (Throwable ignored) {
        }

        try {
            Field f = claim.getClass().getDeclaredField("parent");
            f.setAccessible(true);
            Object o = f.get(claim);
            if (o instanceof Claim) return (Claim) o;
        } catch (Throwable ignored) {
        }

        try {
            Field f = claim.getClass().getField("parent");
            Object o = f.get(claim);
            if (o instanceof Claim) return (Claim) o;
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Override
    public String getName() {
        return "RequireEmptyInvOnEnter";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.RequireEmptyInvOnEnterEnabled, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.RequireEmptyInvOnEnterDisabled);
    }
}
