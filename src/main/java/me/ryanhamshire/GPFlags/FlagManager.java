package me.ryanhamshire.GPFlags;

import com.google.common.io.Files;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for flags
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class FlagManager {

    private final ConcurrentHashMap<String, FlagDefinition> definitions;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Flag>> flags;
    private final List<String> worlds = new ArrayList<>();

    public static final String DEFAULT_FLAG_ID = "-2";

    public FlagManager() {
        this.definitions = new ConcurrentHashMap<>();
        this.flags = new ConcurrentHashMap<>();
        Bukkit.getWorlds().forEach(world -> worlds.add(world.getName()));
    }

    public void registerFlagDefinition(FlagDefinition def) {
        String name = def.getName();
        this.definitions.put(name.toLowerCase(), def);
    }

    public FlagDefinition getFlagDefinitionByName(String name) {
        return this.definitions.get(name.toLowerCase());
    }

    public Collection<FlagDefinition> getFlagDefinitions() {
        return new ArrayList<>(this.definitions.values());
    }

    public Collection<String> getFlagDefinitionNames() {
        return new ArrayList<>(this.definitions.keySet());
    }

    public SetFlagResult setFlag(String claimId, FlagDefinition def, boolean isActive, CommandSender sender, String... args) {
        StringBuilder internalParameters = new StringBuilder();
        StringBuilder friendlyParameters = new StringBuilder();
        for (String arg : args) {
            friendlyParameters.append(arg).append(" ");
            if (def.getName().equals("NoEnterPlayer") && !arg.isEmpty()) {
                if (arg.length() <= 30) {
                    OfflinePlayer offlinePlayer;
                    try {
                        offlinePlayer = Bukkit.getOfflinePlayerIfCached(arg);
                        if (offlinePlayer != null) {
                            arg = offlinePlayer.getUniqueId().toString();
                        }
                    } catch (NoSuchMethodError ignored) {
                        offlinePlayer = Bukkit.getOfflinePlayer(arg);
                        arg = offlinePlayer.getUniqueId().toString();
                    }
                }
            }
            internalParameters.append(arg).append(" ");
        }
        internalParameters = new StringBuilder(internalParameters.toString().trim());
        friendlyParameters = new StringBuilder(friendlyParameters.toString().trim());

        SetFlagResult result;
        if (isActive) {
            result = def.validateParameters(friendlyParameters.toString(), sender);
            if (!result.success) return result;
        } else {
            result = new SetFlagResult(true, def.getUnSetMessage());
        }

        Flag flag = new Flag(def, internalParameters.toString());
        flag.setSet(isActive);
        ConcurrentHashMap<String, Flag> claimFlags = this.flags.get(claimId);
        if (claimFlags == null) {
            claimFlags = new ConcurrentHashMap<>();
            this.flags.put(claimId, claimFlags);
        }

        String key = def.getName().toLowerCase();
        if (!claimFlags.containsKey(key) && isActive) {
            def.incrementInstances();
        }
        claimFlags.put(key, flag);

        // Default flags -> apply to all claims & subclaims
        if (DEFAULT_FLAG_ID.equals(claimId)) {
            for (Claim top : GriefPrevention.instance.dataStore.getClaims()) {
                Deque<Claim> stack = new ArrayDeque<>();
                stack.push(top);
                while (!stack.isEmpty()) {
                    Claim c = stack.pop();
                    if (isActive) {
                        def.onFlagSet(c, flag.parameters);
                    } else {
                        def.onFlagUnset(c);
                    }
                    if (c.children != null) {
                        for (Claim ch : c.children) stack.push(ch);
                    }
                }
            }
            return result;
        }

        // Normal flag -> resolve live innermost claim by ID
        try {
            long id = Long.parseLong(claimId);
            Claim claim = findClaimByIdDeep(id);
            if (claim != null) {
                claim = resolveLiveInnermost(claim);
                if (isActive) {
                    def.onFlagSet(claim, internalParameters.toString());
                } else {
                    def.onFlagUnset(claim);
                }
            }
        } catch (Throwable ignored) {}

        return result;
    }

    public @Nullable Flag getRawClaimFlag(@NotNull Claim claim, @NotNull String flag) {
        String claimId = claim.getID().toString();
        ConcurrentHashMap<String, Flag> claimFlags = this.flags.get(claimId);
        if (claimFlags == null) return null;
        return claimFlags.get(flag);
    }

    public @Nullable Flag getRawDefaultFlag(@NotNull String flag) {
        ConcurrentHashMap<String, Flag> defaultFlags = flags.get(DEFAULT_FLAG_ID);
        if (defaultFlags == null) return null;
        return defaultFlags.get(flag);
    }

    public @Nullable Flag getRawWorldFlag(@NotNull World world, @NotNull String flag) {
        ConcurrentHashMap<String, Flag> worldFlags = flags.get(world.getName());
        if (worldFlags == null) return null;
        return worldFlags.get(flag);
    }

    public @Nullable Flag getRawServerFlag(@NotNull String flag) {
        ConcurrentHashMap<String, Flag> serverFlags = this.flags.get("everywhere");
        if (serverFlags == null) return null;
        return serverFlags.get(flag);
    }

    public @Nullable Flag getEffectiveFlag(@Nullable Location location, @NotNull String flagname, @Nullable Claim claim) {
        if (location == null) return null;
        flagname = flagname.toLowerCase();
        Flag flag;
        if (GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) {
            if (claim != null) {
                flag = getRawClaimFlag(claim, flagname);
                if (flag != null) {
                    if (flag.getSet()) return flag;
                    return null;
                }
                Claim parent = claim.parent;
                if (parent != null) {
                    flag = getRawClaimFlag(parent, flagname);
                    if (flag != null) {
                        if (flag.getSet()) return flag;
                        return null;
                    }
                }
                flag = getRawDefaultFlag(flagname);
                if (flag != null) {
                    if (flag.getSet()) return flag;
                    return null;
                }
            }
        }
        flag = getRawWorldFlag(location.getWorld(), flagname);
        if (flag != null && flag.getSet()) return flag;
        flag = getRawServerFlag(flagname);
        if (flag != null && flag.getSet()) return flag;
        return null;
    }

    public @Nullable Flag getEffectiveFlag(@NotNull String flagname, @Nullable Claim claim, @NotNull World world) {
        flagname = flagname.toLowerCase();
        Flag flag;
        if (claim != null && GriefPrevention.instance.claimsEnabledForWorld(world)) {
            flag = getRawClaimFlag(claim, flagname);
            if (flag != null) {
                if (flag.getSet()) return flag;
                return null;
            }
            Claim parent = claim.parent;
            if (parent != null) {
                flag = getRawClaimFlag(parent, flagname);
                if (flag != null) {
                    if (flag.getSet()) return flag;
                    return null;
                }
            }
            flag = getRawDefaultFlag(flagname);
            if (flag != null) {
                if (flag.getSet()) return flag;
                return null;
            }
        }
        flag = getRawWorldFlag(world, flagname);
        if (flag != null && flag.getSet()) return flag;
        flag = getRawServerFlag(flagname);
        if (flag != null && flag.getSet()) return flag;
        return null;
    }

    public Collection<Flag> getFlags(Claim claim) {
        if (claim == null) return null;
        return getFlags(claim.getID().toString());
    }

    public Collection<Flag> getFlags(String claimID) {
        if (claimID == null) return null;
        ConcurrentHashMap<String, Flag> claimFlags = this.flags.get(claimID);
        if (claimFlags == null) {
            return new ArrayList<>();
        } else {
            return new ArrayList<>(claimFlags.values());
        }
    }

    public SetFlagResult unSetFlag(Claim claim, FlagDefinition def) {
        return unSetFlag(claim.getID().toString(), def);
    }

    public SetFlagResult unSetFlag(String claimId, FlagDefinition def) {
        ConcurrentHashMap<String, Flag> claimFlags = this.flags.get(claimId);
        if (claimFlags == null || !claimFlags.containsKey(def.getName().toLowerCase())) {
            return this.setFlag(claimId, def, false, null);
        } else {
            try {
                long id = Long.parseLong(claimId);
                Claim claim = findClaimByIdDeep(id);
                if (claim != null) {
                    claim = resolveLiveInnermost(claim);
                    def.onFlagUnset(claim);
                }
            } catch (Throwable ignored) {}
            claimFlags.remove(def.getName().toLowerCase());
            return new SetFlagResult(true, def.getUnSetMessage());
        }
    }

    List<MessageSpecifier> load(String input) throws InvalidConfigurationException {
        this.flags.clear();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(input);

        ArrayList<MessageSpecifier> errors = new ArrayList<>();
        Set<String> claimIDs = yaml.getKeys(false);
        for (String claimID : claimIDs) {
            Set<String> flagNames = yaml.getConfigurationSection(claimID).getKeys(false);
            for (String flagName : flagNames) {
                String paramsDefault = yaml.getString(claimID + "." + flagName);
                String params = yaml.getString(claimID + "." + flagName + ".params", paramsDefault);
                if (FlagsDataStore.PRIOR_CONFIG_VERSION == 0) {
                    params = MessagingUtil.reserialize(params);
                }
                boolean set = yaml.getBoolean(claimID + "." + flagName + ".value", true);
                FlagDefinition def = this.getFlagDefinitionByName(flagName);
                if (def != null) {
                    SetFlagResult result = this.setFlag(claimID, def, set, null, params);
                    if (!result.success) {
                        errors.add(result.message);
                    }
                }
            }
        }
        if (errors.isEmpty() && FlagsDataStore.PRIOR_CONFIG_VERSION == 0) save();
        return errors;
    }

    public void save() {
        try {
            this.save(FlagsDataStore.flagsFilePath);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public HashSet<String> getUsedFlags() {
        HashSet<String> usedFlags = new HashSet<>();
        Set<String> claimIDs = this.flags.keySet();
        for (String claimID : claimIDs) {
            ConcurrentHashMap<String, Flag> claimFlags = this.flags.get(claimID);
            usedFlags.addAll(claimFlags.keySet());
        }
        return usedFlags;
    }

    public String flagsToString() {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<String> claimIDs = this.flags.keySet();
        for (String claimID : claimIDs) {
            ConcurrentHashMap<String, Flag> claimFlags = this.flags.get(claimID);
            Set<String> flagNames = claimFlags.keySet();
            for (String flagName : flagNames) {
                Flag flag = claimFlags.get(flagName);
                String paramsPath = claimID + "." + flagName + ".params";
                yaml.set(paramsPath, flag.parameters);
                String valuePath = claimID + "." + flagName + ".value";
                yaml.set(valuePath, flag.getSet());
            }
        }
        return yaml.saveToString();
    }

    public void save(String filepath) throws IOException {
        String fileContent = this.flagsToString();
        File file = new File(filepath);
        file.getParentFile().mkdirs();
        file.createNewFile();
        Files.write(fileContent.getBytes(StandardCharsets.UTF_8), file);
    }

    public List<MessageSpecifier> load(File file) throws IOException, InvalidConfigurationException {
        if (!file.exists()) return this.load("");
        List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return this.load(builder.toString());
    }

    public void clear() {
        this.flags.clear();
    }

    void removeExceptClaimIDs(HashSet<String> validClaimIDs) {
        HashSet<String> toRemove = new HashSet<>();
        for (String key : this.flags.keySet()) {
            if (!validClaimIDs.contains(key)) {
                try {
                    int numericalValue = Integer.parseInt(key);
                    if (numericalValue >= 0) toRemove.add(key);
                } catch (NumberFormatException ignore) {
                }
            }
        }
        for (String key : toRemove) {
            this.flags.remove(key);
        }
        save();
    }

    // ---------------- helpers to resolve live claims ----------------

    private @Nullable Claim findClaimByIdDeep(long id) {
        for (Claim top : GriefPrevention.instance.dataStore.getClaims()) {
            Claim hit = findInTree(top, id);
            if (hit != null) return hit;
        }
        return null;
    }

    private @Nullable Claim findInTree(@NotNull Claim node, long id) {
        if (node.getID() != null && node.getID() == id) return node;
        if (node.children != null && !node.children.isEmpty()) {
            for (Claim child : node.children) {
                Claim hit = findInTree(child, id);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private @NotNull Claim resolveLiveInnermost(@NotNull Claim c) {
        Location a = c.getLesserBoundaryCorner();
        Location b = c.getGreaterBoundaryCorner();
        int x = (a.getBlockX() + b.getBlockX()) / 2;
        int z = (a.getBlockZ() + b.getBlockZ()) / 2;

        int y;
        try {
            boolean is3D = (Boolean) Claim.class.getMethod("is3D").invoke(c);
            if (is3D) {
                int minY = Math.min(a.getBlockY(), b.getBlockY());
                int maxY = Math.max(a.getBlockY(), b.getBlockY());
                y = (minY + maxY) / 2;
            } else {
                int minH = a.getWorld().getMinHeight();
                int maxH = a.getWorld().getMaxHeight();
                y = (minH + maxH) / 2;
            }
        } catch (Throwable t) {
            int minH = a.getWorld().getMinHeight();
            int maxH = a.getWorld().getMaxHeight();
            y = (minH + maxH) / 2;
        }

        Location center = new Location(a.getWorld(), x, y, z);
        Claim base;
        try {
            base = GriefPrevention.instance.dataStore.getClaimAt(center, false, false, null);
        } catch (Throwable t) {
            base = GriefPrevention.instance.dataStore.getClaimAt(center, false, null);
        }
        if (base == null) return c;

        Claim current = base;
        while (current.children != null && !current.children.isEmpty()) {
            Claim hit = null;
            for (Claim child : current.children) {
                if (child != null && child.inDataStore && child.contains(center, false, false)) {
                    hit = child;
                    break;
                }
            }
            if (hit == null) break;
            current = hit;
        }
        return current;
    }
}
