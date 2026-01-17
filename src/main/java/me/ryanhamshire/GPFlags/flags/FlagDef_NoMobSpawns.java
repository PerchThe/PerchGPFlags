package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class FlagDef_NoMobSpawns extends FlagDefinition {


    private Claim cachedClaim = null;

    public FlagDef_NoMobSpawns(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof Player || entity instanceof ArmorStand) return;

        EntityType type = entity.getType();

        if (type == EntityType.SHULKER
                && entity.getCustomName() != null
                && entity.getCustomName().equals("PerchShopFinderHighlight")) {
            return;
        }

        SpawnReason reason = event.getSpawnReason();

        if (reason == SpawnReason.BREEDING) return;
        if (reason == SpawnReason.BUCKET) return;
        if (reason == SpawnReason.EGG) return;
        if (reason == SpawnReason.SLIME_SPLIT) return;

        try {
            if (reason == SpawnReason.valueOf("BEEHIVE")) return;
        } catch (IllegalArgumentException ignored) {
        }

        if (cachedClaim != null && cachedClaim.contains(event.getLocation(), false, false)) {

        } else {

            cachedClaim = GriefPrevention.instance.dataStore.getClaimAt(event.getLocation(), false, false, cachedClaim);
        }

        if (cachedClaim == null) return;

        Flag flag = this.getEffectiveFlag(cachedClaim, event.getLocation());

        if (flag != null) {
            WorldSettings settings = this.settingsManager.get(event.getEntity().getWorld());
            if (!(settings.noMonsterSpawnIgnoreSpawners && Util.isSpawnerReason(reason))) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public String getName() {
        return "NoMobSpawns";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.DisableMobSpawns);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.EnableMobSpawns);
    }
}