package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class FlagDef_OwnerMemberFly extends FlagDefinition {

    public FlagDef_OwnerMemberFly(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public void onFlagSet(Claim claim, String param) {
        for (Player p : Util.getPlayersIn(claim)) {
            FlightManager.managePlayerFlight(p, null, p.getLocation());
        }
    }

    @Override
    public void onFlagUnset(Claim claim) {
        for (Player p : Util.getPlayersIn(claim)) {
            FlightManager.manageFlightLater(p, 1, p.getLocation());
        }
    }

    public static boolean letPlayerFly(Player player, Location location, Claim claim) {
        if (claim == null) return false;
        Flag flag = GPFlags.getInstance().getFlagManager().getEffectiveFlag(location, "OwnerMemberFly", claim);
        if (flag == null) return false;
        if (!flag.getSet()) return false;
        return Util.canAccess(claim, player);
    }


    @Override
    public String getName() {
        return "OwnerMemberFly";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.OwnerMemberFlightEnabled);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.OwnerMemberFlightDisabled);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

}
