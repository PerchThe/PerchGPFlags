package me.ryanhamshire.GPFlags;

import java.util.*;
import java.util.logging.Level;

import com.google.common.collect.ImmutableMap;
import me.ryanhamshire.GPFlags.commands.*;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GPFlags.hooks.PlaceholderApiHook;
import me.ryanhamshire.GPFlags.listener.*;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import me.ryanhamshire.GPFlags.flags.NoMobSpawnsGate;

import me.ryanhamshire.GPFlags.flags.FlagDef_ViewContainers;
import org.checkerframework.checker.nullness.qual.NonNull;


public class GPFlags extends JavaPlugin {

    private static GPFlags instance;
    private FlagsDataStore flagsDataStore;
    private final FlagManager flagManager = new FlagManager();
    private WorldSettingsManager worldSettingsManager;
    public BukkitAudiences adventure;
    boolean registeredFlagDefinitions = false;
    private PlayerListener playerListener;

    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;
        this.adventure = BukkitAudiences.create(this);

        this.playerListener = new PlayerListener();
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        try {
            Class.forName("io.papermc.paper.event.entity.EntityMoveEvent");
            Bukkit.getPluginManager().registerEvents(new EntityMoveListener(), this);
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent");
            Bukkit.getPluginManager().registerEvents(new ClaimResizeListener(), this);
        } catch (ClassNotFoundException e) {
            Bukkit.getPluginManager().registerEvents(new ClaimModifiedListener(), this);
        }
        Bukkit.getPluginManager().registerEvents(new ClaimCreatedListener(), this);
        Bukkit.getPluginManager().registerEvents(new ClaimTransferListener(), this);
        Bukkit.getPluginManager().registerEvents(new FlightManager(), this);
        Bukkit.getPluginManager().registerEvents(new NoMobSpawnsGate(this), this);


        this.flagsDataStore = new FlagsDataStore();
        reloadConfig();

        // Register Commands
        getCommand("allflags").setExecutor(new CommandAllFlags());
        getCommand("gpflags").setExecutor(new CommandGPFlags());
        getCommand("listclaimflags").setExecutor(new CommandListClaimFlags());
        getCommand("setclaimflag").setExecutor(new CommandSetClaimFlag());
        getCommand("setclaimflagplayer").setExecutor(new CommandSetClaimFlagPlayer());
        getCommand("setdefaultclaimflag").setExecutor(new CommandSetDefaultClaimFlag());
        getCommand("setserverflag").setExecutor(new CommandSetServerFlag());
        getCommand("setworldflag").setExecutor(new CommandSetWorldFlag());
        getCommand("unsetclaimflag").setExecutor(new CommandUnsetClaimFlag());
        getCommand("unsetclaimflagplayer").setExecutor(new CommandUnsetClaimFlagPlayer());
        getCommand("unsetdefaultclaimflag").setExecutor(new CommandUnsetDefaultClaimFlag());
        getCommand("unsetserverflag").setExecutor(new CommandUnsetServerFlag());
        getCommand("unsetworldflag").setExecutor(new CommandUnsetWorldFlag());
        getCommand("bulksetflag").setExecutor(new CommandBulkSetFlag());
        getCommand("bulkunsetflag").setExecutor(new CommandBulkUnsetFlag());

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderApiHook(this).register();
        }

        float finish = (float) (System.currentTimeMillis() - start) / 1000;
        MessagingUtil.sendMessage(null, "Successfully loaded in " + String.format("%.2f", finish) + " seconds");
    }

    public void onDisable() {
        FlagDef_ViewContainers.getViewingInventories().forEach(inv -> {
            inv.setContents(new ItemStack[inv.getSize()]);
            new ArrayList<>(inv.getViewers()).forEach(HumanEntity::closeInventory);
        });
        flagsDataStore = null;
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        instance = null;
        playerListener = null;
    }

    public @NonNull BukkitAudiences getAdventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    public void reloadConfig() {
        this.worldSettingsManager = new WorldSettingsManager();
        new GPFlagsConfig(this);
    }

    public static GPFlags getInstance() {
        return instance;
    }
    public FlagsDataStore getFlagsDataStore() {
        return this.flagsDataStore;
    }
    public FlagManager getFlagManager() {
        return this.flagManager;
    }
    public WorldSettingsManager getWorldSettingsManager() {
        return this.worldSettingsManager;
    }

    private static DrilldownPie createStaticDrilldownStat(String statId, String value1, String value2) {
        final Map<String, Map<String, Integer>> map = ImmutableMap.of(value1, ImmutableMap.of(value2, 1));
        return new DrilldownPie(statId, () -> map);
    }

}
