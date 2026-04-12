package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;

public class FlagDef_NoVehicleDamage extends FlagDefinition {

    public FlagDef_NoVehicleDamage(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Vehicle vehicle = event.getVehicle();

        EntityDamageEvent last = vehicle.getLastDamageCause();

        boolean isExplosion = false;

        if (last != null) {
            EntityDamageEvent.DamageCause cause = last.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                    cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                isExplosion = true;
            }
        }
        // to handle tnt primed using redstone
        else if (event.getAttacker() == null) {
            isExplosion = true;
        }

        if (!isExplosion) return;

        Flag flag = this.getFlagInstanceAtLocation(vehicle.getLocation(), null);
        if (flag == null) return;

        event.setCancelled(true);
    }

    @Override
    public String getName() {
        return "NoVehicleDamage";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledNoVehicleDamage);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoVehicleDamage);
    }
}
