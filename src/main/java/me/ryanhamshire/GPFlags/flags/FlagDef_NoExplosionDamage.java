package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

public class FlagDef_NoExplosionDamage extends FlagDefinition {

    public FlagDef_NoExplosionDamage(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosionDamage(EntityDamageEvent e) {
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        Flag flag = this.getFlagInstanceAtLocation(e.getEntity().getLocation(), null);
        if (flag == null) return;

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreakByExplosion(HangingBreakEvent e) {
        if (e.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) return;

        Flag flag = this.getFlagInstanceAtLocation(e.getEntity().getLocation(), null);
        if (flag == null) return;

        e.setCancelled(true);
    }

    @Override
    public String getName() {
        return "NoExplosionDamage";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledNoExplosionDamage);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoExplosionDamage);
    }
}
