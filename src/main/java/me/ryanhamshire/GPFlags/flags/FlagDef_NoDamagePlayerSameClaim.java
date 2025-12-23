package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class FlagDef_NoDamagePlayerSameClaim extends FlagDefinition {

    public FlagDef_NoDamagePlayerSameClaim(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override public String getName() { return "NoDamagePlayerSameClaim"; }
    @Override public MessageSpecifier getSetMessage(String p) { return new MessageSpecifier(Messages.EnabledNoDamagePlayerSameClaim); }
    @Override public MessageSpecifier getUnSetMessage() { return new MessageSpecifier(Messages.DisabledNoDamagePlayerSameClaim); }
    @Override public List<FlagType> getFlagType() { return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        if (victim.hasPermission("gpflags.allowdamage")) return;
        if (attacker.hasPermission("gpflags.allowdamage")) return;

        Claim ca = GriefPrevention.instance.dataStore.getClaimAt(attacker.getLocation(), false, null);
        Claim cv = GriefPrevention.instance.dataStore.getClaimAt(victim.getLocation(), false, null);

        if (ca == null || cv == null) return;

        boolean sameClaim = false;
        try {
            Method m = Claim.class.getMethod("getID");
            Object ida = m.invoke(ca);
            Object idv = m.invoke(cv);
            sameClaim = ida != null && ida.equals(idv);
        } catch (NoSuchMethodException nsme) {
            sameClaim = ca.equals(cv);
        } catch (Exception e) {
            sameClaim = ca.equals(cv);
        }

        if (!sameClaim) return;

        Flag flag = this.getFlagInstanceAtLocation(victim.getLocation(), attacker);
        if (flag == null) return;

        event.setCancelled(true);

        String owner = ca.getOwnerName() != null ? ca.getOwnerName() : "Unknown";
        String msg = plugin.getFlagsDataStore().getMessage(Messages.NoDamagePlayerSameClaim);
        msg = msg.replace("{p}", attacker.getName())
                .replace("{o}", owner)
                .replace("{0}", attacker.getName())
                .replace("{1}", owner);
        MessagingUtil.sendMessage(attacker, TextMode.Warn + msg);
    }
}
