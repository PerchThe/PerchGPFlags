package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.FlagsDataStore;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Sittable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Arrays;
import java.util.List;

public class FlagDef_NoSittingAnimals extends FlagDefinition {

    public FlagDef_NoSittingAnimals(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "NoSittingAnimals";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledNoSittingAnimals);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoSittingAnimals);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractSittable(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof Sittable)) return;
        if (!(event.getRightClicked() instanceof Tameable)) return;

        Tameable tameable = (Tameable) event.getRightClicked();
        if (!tameable.isTamed()) return;

        Player player = event.getPlayer();
        Flag flag = this.getFlagInstanceAtLocation(event.getRightClicked().getLocation(), player);
        if (flag == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getRightClicked().getLocation(), false, null);
        if (Util.shouldBypass(player, claim, flag)) return;

        event.setCancelled(true);
        String owner = claim != null ? claim.getOwnerName() : "Unknown";
        String msg = new FlagsDataStore().getMessage(Messages.NoSittingAnimals);
        msg = msg.replace("{p}", player.getName()).replace("{o}", owner);
        msg = msg.replace("{0}", player.getName()).replace("{1}", owner);
        MessagingUtil.sendMessage(player, TextMode.Warn + msg);
    }
}
