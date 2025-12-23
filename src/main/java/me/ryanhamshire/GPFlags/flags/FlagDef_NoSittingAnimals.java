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
import org.bukkit.Material;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

        // Only act if the flag is present at this location
        Flag flag = this.getFlagInstanceAtLocation(event.getRightClicked().getLocation(), player);
        if (flag == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getRightClicked().getLocation(), false, null);
        if (Util.shouldBypass(player, claim, flag)) return;

        // Pet owner check
        AnimalTamer tamer = tameable.getOwner();
        UUID petOwnerId = (tamer != null) ? tamer.getUniqueId() : null;
        boolean isOwnerOfPet = petOwnerId != null && petOwnerId.equals(player.getUniqueId());

        // Trust / ownership in claim
        boolean hasClaimTrust = false;
        if (claim == null) {
            // Wilderness: allow only the pet owner to control their own pet
            hasClaimTrust = isOwnerOfPet;
        } else {
            // Owner of claim?
            if (player.getUniqueId().equals(claim.getOwnerID())) {
                hasClaimTrust = true;
            } else {
                // Any trust level qualifies
                if (claim.allowAccess(player) == null
                        || claim.allowContainers(player) == null
                        || claim.allowBuild(player, Material.AIR) == null
                        || claim.allowGrantPermission(player) == null) {
                    hasClaimTrust = true;
                }
            }
        }

        // Allow only if the player is the pet owner AND trusted/owner in the claim (or wilderness-owner case above)
        if (isOwnerOfPet && hasClaimTrust) return;

        // Otherwise block & message
        event.setCancelled(true);
        String ownerName = (claim != null) ? claim.getOwnerName() : "Unknown";
        String msg = this.plugin.getFlagsDataStore().getMessage(Messages.NoSittingAnimals);
        msg = msg.replace("{p}", player.getName()).replace("{o}", ownerName);
        msg = msg.replace("{0}", player.getName()).replace("{1}", ownerName);
        MessagingUtil.sendMessage(player, TextMode.Warn + msg);
    }
}
