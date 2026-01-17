package me.ryanhamshire.GPFlags.util;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("WeakerAccess")
public class Util {

    public static boolean isMonster(Entity entity) {
        EntityType type = entity.getType();
        return (entity instanceof Monster || type == EntityType.GHAST || type == EntityType.MAGMA_CUBE || type == EntityType.SHULKER
                || type == EntityType.PHANTOM || type == EntityType.SLIME || type == EntityType.HOGLIN);
    }

    public static boolean canAccess(Claim claim, Player player) {
        if (claim == null) return true;
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims) return true;
        try {
            return claim.checkPermission(player, ClaimPermission.Access, null) == null;
        } catch (NoSuchMethodError e) {
            return claim.allowAccess(player) == null;
        }
    }

    public static boolean canInventory(Claim claim, Player player) {
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims) return true;
        try {
            return claim.checkPermission(player, ClaimPermission.Inventory, null) == null;
        } catch (NoSuchFieldError | NoSuchMethodError e) {
            return claim.allowContainers(player) == null;
        }
    }

    public static boolean canBuild(Claim claim, Player player) {
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims) return true;
        try {
            return claim.checkPermission(player, ClaimPermission.Build, null) == null;
        } catch (NoSuchFieldError | NoSuchMethodError e) {
            return claim.allowBuild(player, Material.STONE) == null;
        }
    }

    public static boolean canManage(Claim claim, Player player) {
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims) return true;
        try {
            return claim.checkPermission(player, ClaimPermission.Manage, null) == null;
        } catch (NoSuchFieldError | NoSuchMethodError e) {
            return claim.allowGrantPermission(player) == null;
        }
    }

    public static boolean canEdit(Player player, Claim claim) {
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims) return true;
        try {
            return claim.checkPermission(player, ClaimPermission.Edit, null) == null;
        } catch (NoSuchFieldError e) {
            return claim.allowEdit(player) == null;
        }
    }

    public static List<String> flagTab(CommandSender sender, String arg) {
        List<String> flags = new ArrayList<>();
        GPFlags.getInstance().getFlagManager().getFlagDefinitions().forEach(flagDefinition -> {
            String perm = "gpflags.flag." + flagDefinition.getName();
            if (sender instanceof Player) {
                if (hasPerm((Player) sender, perm)) flags.add(flagDefinition.getName());
            } else {
                if (sender.hasPermission(perm)) flags.add(flagDefinition.getName());
            }
        });
        return StringUtil.copyPartialMatches(arg, flags, new ArrayList<>());
    }

    public static List<String> paramTab(CommandSender sender, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "commandblacklist":
            case "commandwhitelist":
            case "entercommand":
            case "entercommand_members":
            case "entercommand_owner":
            case "exitcommand":
            case "exitcommand_members":
            case "exitcommand_owner":
            case "entermessage":
            case "exitmessage":
                List<String> params = new ArrayList<>();
                if (!(sender instanceof Player)) return null;
                Player p = (Player) sender;
                FlagDefinition flagD = (GPFlags.getInstance().getFlagManager().getFlagDefinitionByName("entercommand"));
                Flag flag = flagD.getFlagInstanceAtLocation(p.getLocation(), p);
                if (flag == null) return null;
                String flagParams = flag.parameters;
                if (flagParams != null) {
                    params.add(flagParams);
                }
                return StringUtil.copyPartialMatches(args[1], params, new ArrayList<>());
            case "noenterplayer":
                if (!(sender instanceof Player)) return null;
                Player p2 = (Player) sender;
                FlagDefinition flagD2 = (GPFlags.getInstance().getFlagManager().getFlagDefinitionByName("noenterplayer"));
                Flag flag2 = flagD2.getFlagInstanceAtLocation(p2.getLocation(), p2);
                if (flag2 == null) return null;
                String flagParams2 = flag2.parameters;
                if (flagParams2 == null) return null;
                ArrayList<String> suggestion = new ArrayList<>();
                suggestion.add(flag2.getFriendlyParameters());
                return StringUtil.copyPartialMatches(args[1], suggestion, new ArrayList<>());
            case "nomobspawnstype":
                List<String> entityTypes = new ArrayList<>();
                for (EntityType entityType : EntityType.values()) {
                    String type = entityType.toString();
                    if (sender.hasPermission("gpflags.flag.nomobspawnstype." + type)) {
                        String arg = args[1];
                        if (arg.contains(";")) {
                            if (arg.charAt(arg.length() - 1) != ';') {
                                arg = arg.substring(0, arg.lastIndexOf(';') + 1);
                            }
                            entityTypes.add(arg + type);
                        } else {
                            entityTypes.add(type);
                        }
                    }
                }
                return StringUtil.copyPartialMatches(args[1], entityTypes, new ArrayList<>());

            case "changebiome":
                ArrayList<String> biomes = new ArrayList<>();
                for (Biome biome : Biome.values()) {
                    if (sender.hasPermission("gpflags.flag.changebiome." + biome)) {
                        biomes.add(biome.toString());
                    }
                }
                biomes.sort(String.CASE_INSENSITIVE_ORDER);
                return StringUtil.copyPartialMatches(args[1], biomes, new ArrayList<>());

            case "noopendoors":
                if (args.length != 2) return null;
                List<String> doorType = Arrays.asList("doors", "trapdoors", "gates");
                return StringUtil.copyPartialMatches(args[1], doorType, new ArrayList<>());
        }
        return Collections.emptyList();
    }

    public static int getMaxHeight(Location l) {
        return getMaxHeight(l.getWorld());
    }

    public static int getMinHeight(Location l) {
        return getMinHeight(l.getWorld());
    }

    public static int getMaxHeight(World w) {
        try {
            return w.getMaxHeight();
        } catch (NoSuchMethodError e) {
            return 256;
        }
    }

    public static int getMinHeight(World w) {
        try {
            return w.getMinHeight();
        } catch (NoSuchMethodError e) {
            return 0;
        }
    }

    public static Location getInBoundsLocation(@NotNull Location loc) {
        World world = loc.getWorld();
        int maxHeight = getMaxHeight(world);
        if (loc.getBlockY() <= maxHeight) return loc;
        return new Location(loc.getWorld(), loc.getX(), maxHeight, loc.getZ());
    }

    public static Location getInBoundsLocation(Player p) {
        return getInBoundsLocation(p.getLocation());
    }

    public static boolean isClaimOwner(Claim c, Player p) {
        if (c == null) return false;
        if (c.getOwnerID() == null) return false;
        return c.getOwnerID().equals(p.getUniqueId());
    }

    public static boolean shouldBypass(@NotNull Player p, @Nullable Claim c, @NotNull String basePerm) {
        if (hasPerm(p, basePerm)) return true;

        if (c == null) return hasPerm(p, basePerm + ".nonclaim");
        if (c.getOwnerID() == null && hasPerm(p, basePerm + ".adminclaim")) return true;
        if (isClaimOwner(c, p) && hasPerm(p, basePerm + ".ownclaim")) return true;

        if (hasPerm(p, basePerm + ".manage") && canManage(c, p)) return true;

        boolean wantsBuild = hasPerm(p, basePerm + ".build") || hasPerm(p, basePerm + ".edit");
        if (wantsBuild && canBuild(c, p)) return true;

        if (hasPerm(p, basePerm + ".inventory") && canInventory(c, p)) return true;
        if (hasPerm(p, basePerm + ".access") && canAccess(c, p)) return true;

        return false;
    }

    public static boolean shouldBypass(Player p, Claim c, Flag f) {
        String basePerm = "gpflags.bypass." + f.getFlagDefinition().getName();
        return shouldBypass(p, c, basePerm);
    }

    public static HashSet<Player> getPlayersIn(Claim claim) {
        HashSet<Player> players = new HashSet<>();
        World world = claim.getGreaterBoundaryCorner().getWorld();
        for (Player p : world.getPlayers()) {
            if (claim.contains(p.getLocation(), false, false)) {
                players.add(p);
            }
        }
        return players;
    }

    public static String getAvailableFlags(Permissible player) {
        StringBuilder flagDefsList = new StringBuilder();
        Collection<FlagDefinition> defs = GPFlags.getInstance().getFlagManager().getFlagDefinitions();
        List<FlagDefinition> sortedDefs = new ArrayList<>(defs);
        sortedDefs.sort(Comparator.comparing(FlagDefinition::getName));

        flagDefsList.append("<aqua>");
        if (player instanceof Player) {
            Player p = (Player) player;
            for (FlagDefinition def : sortedDefs) {
                if (hasPerm(p, "gpflags.flag." + def.getName())) {
                    flagDefsList.append(def.getName()).append("<grey>,<aqua> ");
                }
            }
        } else {
            for (FlagDefinition def : sortedDefs) {
                if (player.hasPermission("gpflags.flag." + def.getName())) {
                    flagDefsList.append(def.getName()).append("<grey>,<aqua> ");
                }
            }
        }

        String def = flagDefsList.toString();
        if (def.length() > 5) {
            def = def.substring(0, def.length() - 4);
        }
        return def;
    }

    private static ArrayList<ItemStack> getDrops(Vehicle vehicle) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        if (!vehicle.isValid()) return drops;

        if (vehicle instanceof Boat) {
            Boat boat = (Boat) vehicle;
            drops.add(new ItemStack(boat.getBoatMaterial()));
        } else if (vehicle instanceof Minecart) {
            Minecart cart = (Minecart) vehicle;
            drops.add(new ItemStack(cart.getMinecartMaterial()));
        }
        if (!(vehicle instanceof InventoryHolder)) return drops;

        InventoryHolder holder = (InventoryHolder) vehicle;
        for (ItemStack stack : holder.getInventory()) {
            if (stack != null) {
                drops.add(stack);
            }
        }
        return drops;
    }

    public static void breakVehicle(Vehicle vehicle, Location location) {
        if (!vehicle.isValid()) {
            return;
        }
        World world = vehicle.getWorld();
        vehicle.eject();
        if (vehicle instanceof Mob) {
            vehicle.teleport(location);
        } else {
            vehicle.remove();
            ArrayList<ItemStack> drops = getDrops(vehicle);
            for (ItemStack stack : drops) {
                world.dropItem(location, stack);
            }
        }
    }

    public static Set<Player> getMovementGroup(Entity entity) {
        Set<Player> group = new HashSet<>();

        if (entity instanceof Player) {
            Player player = (Player) entity;
            group.add(player);
        }

        List<Entity> passengers = entity.getPassengers();
        for (Entity passenger : passengers) {
            if (passenger instanceof Player) {
                Player person = ((Player) passenger);
                group.add(person);
            }
        }

        Entity mount = entity.getVehicle();
        if (mount instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) mount;
            passengers = vehicle.getPassengers();
            for (Entity passenger : passengers) {
                if (passenger instanceof Player) {
                    Player person = ((Player) passenger);
                    group.add(person);
                }
            }
        }

        return group;
    }

    private static final SpawnReason TRIAL_SPAWNER_REASON = resolveTrialSpawner();

    public static boolean isSpawnerReason(SpawnReason reason) {
        if (reason == SpawnReason.SPAWNER) return true;
        if (reason == SpawnReason.SPAWNER_EGG) return true;
        if (TRIAL_SPAWNER_REASON != null && reason == TRIAL_SPAWNER_REASON) return true;
        return false;
    }

    private static SpawnReason resolveTrialSpawner() {
        try {
            return SpawnReason.TRIAL_SPAWNER;
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    private static final PermissionCache PERM_CACHE = new PermissionCache(1000L);

    public static boolean hasPerm(Player p, String perm) {
        return PERM_CACHE.has(p, perm);
    }

    public static void invalidatePermCache(Player p) {
        if (p == null) return;
        PERM_CACHE.invalidate(p.getUniqueId());
    }

    private static final class PermissionCache {
        private final long ttlMillis;
        private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Entry>> cache = new ConcurrentHashMap<>();

        private PermissionCache(long ttlMillis) {
            this.ttlMillis = ttlMillis;
        }

        private boolean has(Player player, String permission) {
            long now = System.currentTimeMillis();
            UUID uuid = player.getUniqueId();
            ConcurrentHashMap<String, Entry> perms = cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            Entry entry = perms.get(permission);
            if (entry != null && entry.expiresAt >= now) return entry.value;

            boolean value = player.hasPermission(permission);
            perms.put(permission, new Entry(value, now + ttlMillis));
            return value;
        }

        private void invalidate(UUID uuid) {
            cache.remove(uuid);
        }

        private static final class Entry {
            private final boolean value;
            private final long expiresAt;

            private Entry(boolean value, long expiresAt) {
                this.value = value;
                this.expiresAt = expiresAt;
            }
        }
    }
}
