package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.SetFlagResult;
import me.ryanhamshire.GPFlags.WorldSettingsManager;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public abstract class FlagDefinition implements Listener {

    private final FlagManager flagManager;
    WorldSettingsManager settingsManager;
    private int instances = 0;
    protected GPFlags plugin;

    private static final ThreadLocal<Claim> TL_CLAIM_HINT = new ThreadLocal<>();

    public FlagDefinition(FlagManager manager, GPFlags plugin) {
        this.flagManager = manager;
        this.plugin = plugin;
        this.settingsManager = plugin.getWorldSettingsManager();
    }

    public abstract String getName();

    public SetFlagResult validateParameters(String parameters, @Nullable CommandSender sender) {
        return new SetFlagResult(true, this.getSetMessage(parameters));
    }

    public abstract MessageSpecifier getSetMessage(String parameters);

    public abstract MessageSpecifier getUnSetMessage();

    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT, FlagType.WORLD, FlagType.SERVER);
    }

    public void onFlagSet(Claim claim, String params) {
    }

    public void onFlagUnset(Claim claim) {
    }

    public Flag getFlagInstanceAtLocation(@NotNull Location location, @Nullable Player player) {
        Claim hint;
        PlayerData playerData;

        if (player != null) {
            playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            hint = playerData.lastClaim;
        } else {
            playerData = null;
            hint = TL_CLAIM_HINT.get();
        }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, false, hint);

        if (playerData != null) {
            playerData.lastClaim = claim;
        } else {
            TL_CLAIM_HINT.set(claim);
        }

        return flagManager.getEffectiveFlag(location, this.getName(), claim);
    }

    public Flag getEffectiveFlag(@Nullable Claim claim, @NotNull World world) {
        return flagManager.getEffectiveFlag(this.getName(), claim, world);
    }

    public Flag getEffectiveFlag(@Nullable Claim claim, Location location) {
        if (location == null) return null;
        return flagManager.getEffectiveFlag(this.getName(), claim, location.getWorld());
    }

    public void incrementInstances() {
        if (++this.instances == 1) {
            this.firstTimeSetup();
        }
    }

    private boolean hasRegisteredEvents = false;

    public void firstTimeSetup() {
        if (hasRegisteredEvents) return;
        hasRegisteredEvents = true;
        Bukkit.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void updateSettings(WorldSettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public enum FlagType {
        CLAIM("<green>CLAIM"),
        WORLD("<gold>WORLD"),
        SERVER("<dark_aqua>SERVER"),
        DEFAULT("<YELLOW>DEFAULT");

        String name;

        FlagType(String string) {
            this.name = string;
        }

        @Override
        public String toString() {
            return name + "<grey>";
        }
    }
}
