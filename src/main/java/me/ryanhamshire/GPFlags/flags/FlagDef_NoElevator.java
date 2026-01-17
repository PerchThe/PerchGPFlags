package me.ryanhamshire.GPFlags.flags;

import me.perch.elevator.events.ElevatorAttemptEvent;
import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.TextMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.Arrays;
import java.util.List;

public class FlagDef_NoElevator extends FlagDefinition {

    public FlagDef_NoElevator(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "NoElevator";
    }

    @Override
    public MessageSpecifier getSetMessage(String p) {
        return new MessageSpecifier(Messages.EnabledNoElevator);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoElevator);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElevatorAttempt(ElevatorAttemptEvent event) {
        Player player = event.getPlayer();

        Flag flag = this.getFlagInstanceAtLocation(event.getLocation(), player);

        if (flag != null) {
            event.setCancelled(true);

        }
    }
}