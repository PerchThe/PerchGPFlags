package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.FlightManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_AllowFlyForEveryone extends FlagDefinition {

    private static final String FLAG_NAME = "AllowFlyForEveryone";

    private static final String[] PERMS = {
            "claimfly.fly", "claimfly.bypass",
            "claimflight.fly", "claimflight.bypass"
    };

    private final LuckPerms lp;
    private final GPFlags plugin;

    private final ConcurrentHashMap<UUID, Boolean> inAreaState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> appliedPermState = new ConcurrentHashMap<>();

    public FlagDef_AllowFlyForEveryone(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        this.plugin = plugin;
        this.lp = LuckPermsProvider.get();

        Bukkit.getPluginManager().registerEvents(new ClaimFlyListener(), plugin);

        for (Player p : Bukkit.getOnlinePlayers()) updateAndApply(p, p.getLocation(), true, true);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) updateAndApply(p, p.getLocation(), false, false);
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    @Override
    public void onFlagSet(Claim claim, String param) {
        for (Player p : Util.getPlayersIn(claim)) updateAndApply(p, p.getLocation(), true, true);
    }

    @Override
    public void onFlagUnset(Claim claim) {
        for (Player p : Util.getPlayersIn(claim)) updateAndApply(p, p.getLocation(), true, true);
    }

    @Override
    public String getName() {
        return FLAG_NAME;
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.AllowFlyForEveryoneEnabled);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.AllowFlyForEveryoneDisabled);
    }

    public static boolean letPlayerFly(Player player, Location location, Claim claim) {
        Flag flag = GPFlags.getInstance().getFlagManager().getEffectiveFlag(location, FLAG_NAME, claim);
        return flag != null;
    }

    private static boolean inFlyArea(Location loc) {
        if (loc == null) return false;
        Claim c = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
        if (c == null) return false;
        return GPFlags.getInstance().getFlagManager().getEffectiveFlag(loc, FLAG_NAME, c) != null;
    }

    private void updateAndApply(Player p, Location loc, boolean adjustFlight, boolean forceApply) {
        if (p == null || loc == null) return;

        UUID uuid = p.getUniqueId();
        boolean shouldHave = inFlyArea(loc);

        Boolean oldInArea = inAreaState.put(uuid, shouldHave);
        boolean areaChanged = oldInArea == null || oldInArea != shouldHave;

        if (forceApply || areaChanged) {
            applyPermStateIfNeeded(p, shouldHave, forceApply);
        }

        if (adjustFlight && (forceApply || areaChanged)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (shouldHave) FlightManager.managePlayerFlight(p, null, loc);
                else FlightManager.manageFlightLater(p, 1, loc);
            }, 1L);
        }
    }

    private void applyPermStateIfNeeded(Player p, boolean shouldHave, boolean forceApply) {
        UUID uuid = p.getUniqueId();
        Boolean applied = appliedPermState.get(uuid);
        if (!forceApply && applied != null && applied == shouldHave) return;

        appliedPermState.put(uuid, shouldHave);

        if (shouldHave) grantPerms(p);
        else revokePerms(p);
    }

    private void grantPerms(Player p) {
        UUID uuid = p.getUniqueId();
        lp.getUserManager().loadUser(uuid).thenAcceptAsync(u -> {
            Arrays.stream(PERMS).forEach(perm -> u.data().add(Node.builder(perm).value(true).build()));
            lp.getUserManager().saveUser(u);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) p.recalculatePermissions();
            });
        });
    }

    private void revokePerms(Player p) {
        UUID uuid = p.getUniqueId();
        lp.getUserManager().loadUser(uuid).thenAcceptAsync(u -> {
            u.data().clear(n -> {
                String k = n.getKey().toLowerCase(Locale.ROOT);
                for (String perm : PERMS) if (k.equals(perm)) return true;
                return false;
            });
            lp.getUserManager().saveUser(u);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) p.recalculatePermissions();
            });
        });
    }

    private void cleanup(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        inAreaState.remove(uuid);
        appliedPermState.remove(uuid);
    }

    private final class ClaimFlyListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent e) {
            if (e.getTo() == null) return;
            if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                    && e.getFrom().getBlockY() == e.getTo().getBlockY()
                    && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

            Player p = e.getPlayer();
            UUID uuid = p.getUniqueId();

            Boolean prior = inAreaState.get(uuid);
            boolean now = inFlyArea(e.getTo());
            if (prior != null && prior == now) return;

            updateAndApply(p, e.getTo(), true, false);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onTeleport(PlayerTeleportEvent e) {
            if (e.getTo() == null) return;
            Player p = e.getPlayer();
            updateAndApply(p, e.getTo(), true, false);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent e) {
            updateAndApply(e.getPlayer(), e.getPlayer().getLocation(), true, true);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent e) {
            revokePerms(e.getPlayer());
            cleanup(e.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onKick(PlayerKickEvent e) {
            revokePerms(e.getPlayer());
            cleanup(e.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorldChange(PlayerChangedWorldEvent e) {
            updateAndApply(e.getPlayer(), e.getPlayer().getLocation(), true, true);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onRespawn(PlayerRespawnEvent e) {
            Bukkit.getScheduler().runTask(plugin, () -> updateAndApply(e.getPlayer(), e.getRespawnLocation(), true, true));
        }
    }
}
