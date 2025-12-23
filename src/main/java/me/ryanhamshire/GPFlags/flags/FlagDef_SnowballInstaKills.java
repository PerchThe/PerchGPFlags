package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_SnowballInstaKills extends FlagDefinition implements Listener {

    private static boolean listenerRegistered = false;
    private final Plugin plugin;
    private final Set<UUID> recent = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SnowballKillInfo> snowballKills = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final String[] deathMessages = new String[] {
            "❄ %player% got sleighed by %killer%.",
            "❄ %player% caught %killer%s snowball... with their head.",
            "❄ %player% got given the cold shoulder by %killer%.",
            "❄ %player% was packed into a coffin by %killer%.",
            "❄ %player% melted on sight when they saw %killer%."
    };

    private static class SnowballKillInfo {
        private final UUID killerId;
        private final String killerName;

        private SnowballKillInfo(UUID killerId, String killerName) {
            this.killerId = killerId;
            this.killerName = killerName;
        }
    }

    public FlagDef_SnowballInstaKills(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        this.plugin = plugin;
        synchronized (FlagDef_SnowballInstaKills.class) {
            if (!listenerRegistered) {
                plugin.getServer().getPluginManager().registerEvents(this, plugin);
                listenerRegistered = true;
            }
        }
    }

    @Override
    public String getName() {
        return "SnowballInstaKills";
    }

    @Override
    public MessageSpecifier getSetMessage(String p) {
        return new MessageSpecifier(Messages.EnabledSnowballInstaKills);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledSnowballInstaKills);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.WORLD, FlagType.SERVER);
    }

    private void scheduleKill(UUID projId, final Projectile proj, final Player shooter, final LivingEntity victim) {
        if (!recent.add(projId)) return;

        if (victim instanceof Player) {
            Player v = (Player) victim;
            UUID killerId = shooter != null ? shooter.getUniqueId() : null;
            String killerName = shooter != null ? shooter.getName() : "a snowball";
            SnowballKillInfo info = new SnowballKillInfo(killerId, killerName);
            snowballKills.put(v.getUniqueId(), info);
            Bukkit.getScheduler().runTaskLater(plugin, () -> snowballKills.remove(v.getUniqueId(), info), 200L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (victim != null && !victim.isDead()) {
                    try {
                        if (shooter != null) {
                            victim.damage(10000.0, shooter);
                        } else {
                            victim.damage(10000.0, proj);
                        }
                    } catch (Throwable t) {
                        try {
                            victim.setHealth(0.0);
                        } catch (Throwable ignored) {
                            victim.damage(1000.0);
                        }
                    }
                }
            } finally {
                recent.remove(projId);
            }
        }, 1L);
    }

    private boolean handleSnowball(Projectile proj, org.bukkit.entity.Entity hit) {
        if (!(proj instanceof Snowball)) return false;
        if (hit == null || hit.getType() == EntityType.ITEM_FRAME || !(hit instanceof LivingEntity)) return false;
        LivingEntity victim = (LivingEntity) hit;

        if (victim instanceof Player) {
            Player p = (Player) victim;
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return false;
        }

        Player shooter = null;
        try {
            Object s = proj.getShooter();
            if (s instanceof Player) shooter = (Player) s;
        } catch (Throwable ignored) {
        }

        Location loc = victim.getLocation();
        Flag flag = this.getFlagInstanceAtLocation(loc, shooter);
        if (flag == null) return false;

        scheduleKill(proj.getUniqueId(), proj, shooter, victim);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() != null) handleSnowball(event.getEntity(), event.getHitEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile) {
            handleSnowball((Projectile) event.getDamager(), event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        SnowballKillInfo info = snowballKills.remove(victim.getUniqueId());
        if (info == null) return;

        String killerName = info.killerName != null ? info.killerName : "a snowball";
        String template = deathMessages[random.nextInt(deathMessages.length)];
        String msg = template
                .replace("%player%", victim.getName())
                .replace("%killer%", killerName);

        msg = applyGradient(msg, 0x94E3F3, 0xFFFFFF);

        event.setDeathMessage(null);
        Bukkit.getServer().broadcastMessage(msg);
    }

    private String applyGradient(String text, int startColor, int endColor) {
        int length = text.length();
        if (length == 0) return text;
        if (length == 1) return toHexColor(startColor) + text;

        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            double t = (double) i / (double) (length - 1);
            int r = (int) Math.round(startR + (endR - startR) * t);
            int g = (int) Math.round(startG + (endG - startG) * t);
            int b = (int) Math.round(startB + (endB - startB) * t);

            sb.append(toHexColor((r << 16) | (g << 8) | b));
            sb.append(text.charAt(i));
        }

        return sb.toString();
    }

    private String toHexColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        String rs = toTwoHex(r);
        String gs = toTwoHex(g);
        String bs = toTwoHex(b);

        return "§x§" + rs.charAt(0) + "§" + rs.charAt(1)
                + "§" + gs.charAt(0) + "§" + gs.charAt(1)
                + "§" + bs.charAt(0) + "§" + bs.charAt(1);
    }

    private String toTwoHex(int value) {
        String s = Integer.toHexString(value);
        if (s.length() == 1) return "0" + s;
        if (s.length() > 2) return s.substring(s.length() - 2);
        return s;
    }
}
