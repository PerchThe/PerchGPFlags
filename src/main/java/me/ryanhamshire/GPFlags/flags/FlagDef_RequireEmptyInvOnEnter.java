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

    private final Map<UUID, Long> lastRootClaimId = new ConcurrentHashMap<>();

    public FlagDef_RequireEmptyInvOnEnter(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        seedPlayerRoot(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastRootClaimId.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        seedPlayerRoot(event.getPlayer(), event.getTo());
    }

    private void seedPlayerRoot(Player player, Location loc) {
        Claim here = GriefPrevention.instance.dataStore.getClaimAt(Util.getInBoundsLocation(player), false, null);
        Claim root = getRootClaim(here);
        if (root == null) {
            lastRootClaimId.remove(player.getUniqueId());
        } else {
            lastRootClaimId.put(player.getUniqueId(), root.getID());
        }
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

            Flag effective = getEffectiveFlag(rootSet, loc);
            if (!isAllowed(effective, rootSet, p)) {
                if (p.hasPermission(DEBUG_PERMISSION)) dumpInventoryState(p, "onFlagSet");
                GriefPrevention.instance.ejectPlayer(p);
            }
        }
    }

    @Override
    public boolean allowMovement(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo) {
        Claim rootFrom = getRootClaim(claimFrom);
        Claim rootTo = getRootClaim(claimTo);

        if (rootFrom == null) {
            Long cached = lastRootClaimId.get(player.getUniqueId());
            if (cached != null && rootTo != null && Objects.equals(cached, rootTo.getID())) {
                lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
                return true;
            }
        }

        boolean enteringRoot = rootTo != null && (rootFrom == null || !Objects.equals(rootFrom.getID(), rootTo.getID()));
        if (!enteringRoot) {
            if (rootTo != null) lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
            else lastRootClaimId.remove(player.getUniqueId());
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

        if (rootFrom == null) {
            Long cached = lastRootClaimId.get(player.getUniqueId());
            if (cached != null && rootTo != null && Objects.equals(cached, rootTo.getID())) {
                lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
                return;
            }
        }

        boolean enteringRoot = rootTo != null && (rootFrom == null || !Objects.equals(rootFrom.getID(), rootTo.getID()));
        if (!enteringRoot) {
            if (rootTo != null) lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
            else lastRootClaimId.remove(player.getUniqueId());
            return;
        }

        Flag effective = getEffectiveFlag(rootTo, to);
        if (isAllowed(effective, rootTo, player)) {
            lastRootClaimId.put(player.getUniqueId(), rootTo.getID());
            return;
        }

        MessagingUtil.sendMessage(player, TextMode.Err, Messages.RequireEmptyInvOnEnterDenied);
        if (player.hasPermission(DEBUG_PERMISSION)) dumpInventoryState(player, "onChangeClaim");
        GriefPrevention.instance.ejectPlayer(player);
    }

    private boolean isAllowed(@Nullable Flag flag, Claim claim, Player player) {
        if (flag == null) return true;
        if (Util.canAccess(claim, player)) return true;
        if (player.hasPermission(BYPASS_PERMISSION)) return true;
        if (!playerHasAnyItem(player)) return true;
        return false;
    }

    private boolean playerHasAnyItem(Player player) {
        if (hasAny(player.getInventory().getStorageContents())) return true;
        if (hasAny(player.getInventory().getArmorContents())) return true;
        if (hasAny(player.getInventory().getExtraContents())) return true;

        ItemStack off = player.getInventory().getItemInOffHand();
        if (isRealItem(off)) return true;

        ItemStack cursor = player.getItemOnCursor();
        if (isRealItem(cursor)) return true;

        ItemStack[] bottom = player.getOpenInventory().getBottomInventory().getContents();
        if (hasAny(bottom)) return true;

        return false;
    }

    private boolean hasAny(@Nullable ItemStack[] items) {
        if (items == null) return false;
        for (ItemStack is : items) {
            if (isRealItem(is)) return true;
        }
        return false;
    }

    private boolean isRealItem(@Nullable ItemStack is) {
        return is != null && is.getType() != Material.AIR && is.getAmount() > 0;
    }

    private void dumpInventoryState(Player player, String where) {
        StringBuilder sb = new StringBuilder();
        sb.append("RequireEmptyInvOnEnter deny dump [").append(where).append("] player=").append(player.getName()).append("\n");
        sb.append("cursor=").append(describe(player.getItemOnCursor())).append("\n");
        sb.append("offhand=").append(describe(player.getInventory().getItemInOffHand())).append("\n");
        sb.append("storage=").append(describeArray(player.getInventory().getStorageContents())).append("\n");
        sb.append("armor=").append(describeArray(player.getInventory().getArmorContents())).append("\n");
        sb.append("extra=").append(describeArray(player.getInventory().getExtraContents())).append("\n");
        sb.append("bottomView=").append(describeArray(player.getOpenInventory().getBottomInventory().getContents())).append("\n");
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
        if (is.getType() == Material.AIR) return "AIR";
        String meta = is.hasItemMeta() ? "meta" : "nometa";
        return is.getType().name() + "x" + is.getAmount() + ":" + meta;
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
