package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_SnowballsAlwaysDrop extends FlagDefinition implements Listener {

    private static final boolean FORCE_DROP_FOR_BYPASSERS = true;
    private static boolean listenerRegistered = false;
    private final Set<UUID> recentProjectiles = ConcurrentHashMap.newKeySet();
    private final Plugin plugin;

    public FlagDef_SnowballsAlwaysDrop(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        this.plugin = plugin;
        synchronized (FlagDef_SnowballsAlwaysDrop.class) {
            if (!listenerRegistered) {
                plugin.getServer().getPluginManager().registerEvents(this, plugin);
                listenerRegistered = true;
                plugin.getLogger().info("[SnowballsAlwaysDrop] registered");
            }
        }
    }

    @Override public void onFlagSet(Claim claim, String param) {}
    @Override public void onFlagUnset(Claim claim) {}

    private Flag findEffectiveFlag(Location loc, Claim claim) {
        if (loc == null) return null;
        try {
            FlagManager fm = GPFlags.getInstance().getFlagManager();
            if (fm == null) return null;
            try {
                Flag f = fm.getEffectiveFlag(loc, "SnowballsAlwaysDrop", claim);
                if (f != null) return f;
            } catch (Exception ignored) {}
            try {
                Flag f2 = fm.getEffectiveFlag(loc, "SnowballsAlwaysDrop", null);
                if (f2 != null) return f2;
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return null;
    }

    private void handleSnowballProjectile(Projectile proj) {
        if (proj == null) return;
        if (proj.getType() != EntityType.SNOWBALL) return;
        if (!(proj instanceof Snowball)) return;
        if (!(proj.getShooter() instanceof Player)) return;
        Player shooter = (Player) proj.getShooter();

        UUID id = proj.getUniqueId();
        if (!recentProjectiles.add(id)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentProjectiles.remove(id), 2L);

        Location loc = proj.getLocation();

        Claim claim = null;
        try {
            if (GriefPrevention.instance != null && GriefPrevention.instance.dataStore != null)
                claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
        } catch (Exception ignored) {}

        Flag f = findEffectiveFlag(loc, claim);
        if (f == null) return;

        boolean bypass = Util.shouldBypass(shooter, claim, f);
        boolean active = !(bypass && !FORCE_DROP_FOR_BYPASSERS);
        if (!active) return;

        World w = loc.getWorld();
        if (w != null) {
            Location dropLoc = loc.getBlock().getLocation().add(0.5, 1.1, 0.5);
            w.dropItemNaturally(dropLoc, new ItemStack(Material.SNOWBALL, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event == null || event.getEntity() == null) return;
        if (!(event.getEntity() instanceof Projectile)) return;
        handleSnowballProjectile((Projectile) event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null || event.getDamager() == null) return;
        if (!(event.getDamager() instanceof Projectile)) return;
        handleSnowballProjectile((Projectile) event.getDamager());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location loc = player.getLocation();

        Claim claim = null;
        try {
            if (GriefPrevention.instance != null && GriefPrevention.instance.dataStore != null)
                claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
        } catch (Exception ignored) {}

        Flag f = findEffectiveFlag(loc, claim);
        if (f == null) return;

        boolean bypass = Util.shouldBypass(player, claim, f);
        boolean active = !(bypass && !FORCE_DROP_FOR_BYPASSERS);
        if (!active) return;

        World w = loc.getWorld();
        if (w == null) return;

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (ItemStack item : contents) {
            if (item == null) continue;
            if (item.getType() != Material.SNOWBALL) continue;
            w.dropItemNaturally(loc, item.clone());
        }
    }

    @Override public String getName() { return "SnowballsAlwaysDrop"; }
    @Override public MessageSpecifier getSetMessage(String p) { return new MessageSpecifier(Messages.SnowballsAlwaysDropEnabled); }
    @Override public MessageSpecifier getUnSetMessage() { return new MessageSpecifier(Messages.SnowballsAlwaysDropDisabled); }
    @Override public List<FlagType> getFlagType() { return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT, FlagType.WORLD, FlagType.SERVER); }
}
