package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;

public class FlagDef_PermissionFly extends FlagDefinition {

    private static final String[] PERMS = {
            "claimfly.fly","claimfly.bypass",
            "claimflight.fly","claimflight.bypass"
    };

    private static final LuckPerms LP = LuckPermsProvider.get();

    public FlagDef_PermissionFly(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        Bukkit.getPluginManager().registerEvents(new ClaimFlyListener(), plugin);
        for (Player p : Bukkit.getOnlinePlayers()) reconcile(p, p.getLocation(), true);
        new BukkitRunnable(){ @Override public void run(){
            for (Player p : Bukkit.getOnlinePlayers()) reconcile(p, p.getLocation(), false);
        }}.runTaskTimer(plugin, 200L, 200L);
    }

    @Override public void onFlagSet(Claim claim, String param) { for (Player p : Util.getPlayersIn(claim)) reconcile(p, p.getLocation(), true); }
    @Override public void onFlagUnset(Claim claim) { for (Player p : Util.getPlayersIn(claim)) reconcile(p, p.getLocation(), true); }
    @Override public String getName(){ return "PermissionFly"; }
    @Override public MessageSpecifier getSetMessage(String parameters){ return new MessageSpecifier(Messages.PermissionFlightEnabled); }
    @Override public MessageSpecifier getUnSetMessage(){ return new MessageSpecifier(Messages.PermissionFlightDisabled); }

    public static boolean letPlayerFly(Player player, Location location, Claim claim) {
        Flag flag = GPFlags.getInstance().getFlagManager().getEffectiveFlag(location, "PermissionFly", claim);
        return flag != null;
    }

    private static boolean inPermFlyArea(Location loc){
        Claim c = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
        if (c == null) return false;
        return GPFlags.getInstance().getFlagManager().getEffectiveFlag(loc, "PermissionFly", c) != null;
    }

    private static void grantPerms(Player p){
        LP.getUserManager().loadUser(p.getUniqueId()).thenAcceptAsync(u -> {
            Arrays.stream(PERMS).forEach(perm -> u.data().add(Node.builder(perm).value(true).build()));
            LP.getUserManager().saveUser(u);
            Bukkit.getScheduler().runTask(GPFlags.getInstance(), () -> {
                p.recalculatePermissions();
            });
        });
    }

    private static void revokePerms(Player p){
        LP.getUserManager().loadUser(p.getUniqueId()).thenAcceptAsync(u -> {
            u.data().clear(n -> {
                String k = n.getKey().toLowerCase();
                for (String perm : PERMS) if (k.equals(perm)) return true;
                return false;
            });
            LP.getUserManager().saveUser(u);
            Bukkit.getScheduler().runTask(GPFlags.getInstance(), () -> {
                p.recalculatePermissions();
            });
        });
    }

    private static void reconcile(Player p, Location loc, boolean adjustFlight){
        boolean shouldHave = inPermFlyArea(loc);
        if (shouldHave) grantPerms(p); else revokePerms(p);
        if (adjustFlight) {
            Bukkit.getScheduler().runTaskLater(GPFlags.getInstance(), () -> {
                if (shouldHave) FlightManager.managePlayerFlight(p, null, loc);
                else FlightManager.manageFlightLater(p, 1, loc);
            }, 1L);
        }
    }

    private static class ClaimFlyListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent e){
            if (e.getTo() == null) return;
            if (e.getFrom().getBlockX()==e.getTo().getBlockX()
                    && e.getFrom().getBlockY()==e.getTo().getBlockY()
                    && e.getFrom().getBlockZ()==e.getTo().getBlockZ()) return;
            boolean from = inPermFlyArea(e.getFrom());
            boolean to = inPermFlyArea(e.getTo());
            if (from == to) return;
            reconcile(e.getPlayer(), e.getTo(), true);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onTeleport(PlayerTeleportEvent e){
            boolean from = inPermFlyArea(e.getFrom());
            boolean to = inPermFlyArea(e.getTo());
            if (from == to) return;
            reconcile(e.getPlayer(), e.getTo(), true);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent e){ reconcile(e.getPlayer(), e.getPlayer().getLocation(), true); }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent e){ revokePerms(e.getPlayer()); }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onKick(PlayerKickEvent e){ revokePerms(e.getPlayer()); }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorldChange(PlayerChangedWorldEvent e){ reconcile(e.getPlayer(), e.getPlayer().getLocation(), true); }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onRespawn(PlayerRespawnEvent e){
            Bukkit.getScheduler().runTask(GPFlags.getInstance(), () -> reconcile(e.getPlayer(), e.getRespawnLocation(), true));
        }
    }
}
