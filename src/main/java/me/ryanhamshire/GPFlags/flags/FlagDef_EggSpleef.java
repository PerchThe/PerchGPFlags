package me.ryanhamshire.GPFlags.flags;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import me.ryanhamshire.GPFlags.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class FlagDef_EggSpleef extends FlagDefinition implements CommandExecutor {

    private enum EggType {
        NORMAL,
        EXPLOSIVE,
        FREEZE
    }

    private final Map<UUID, EggType> thrownEggs = new HashMap<>();
    private final Map<Location, Material> originalBlocks = new HashMap<>();

    public FlagDef_EggSpleef(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);

        plugin.getCommand("eggspleefreset").setExecutor(this);

        Bukkit.getScheduler().runTaskTimer(plugin, this::giveNormalEggs, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::giveBrownEggs, 20L * 45, 20L * 45);
        Bukkit.getScheduler().runTaskTimer(plugin, this::giveBlueEggs, 20L * 25, 20L * 25);
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, Command command, @NonNull String label, String @NonNull [] args) {

        if (!command.getName().equalsIgnoreCase("eggspleefreset")) return false;

        if (!sender.hasPermission("gpflags.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        int restored = resetArena();
        sender.sendMessage("§aEggSpleef reset! Restored " + restored + " blocks.");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEggThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            ItemStack item = player.getInventory().getItemInMainHand();

            EggType type = EggType.NORMAL;

            if (item.getType() == Material.BROWN_EGG) type = EggType.EXPLOSIVE;
            else if (item.getType() == Material.BLUE_EGG) type = EggType.FREEZE;

            thrownEggs.put(event.getEntity().getUniqueId(), type);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEggHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        Block block = event.getHitBlock();
        if (block == null) return;

        Flag flag = getFlagInstanceAtLocation(block.getLocation(), null);
        if (flag == null) return;

        EggType type = thrownEggs.getOrDefault(event.getEntity().getUniqueId(), EggType.NORMAL);
        thrownEggs.remove(event.getEntity().getUniqueId());

        java.util.function.Predicate<Material> breakable =
                mat -> mat.name().endsWith("_WOOL") || mat == Material.BLUE_ICE;

        if (type == EggType.NORMAL) {
            if (breakable.test(block.getType())) {
                store(block);
                block.setType(Material.AIR, false);
                fxPop(block);
            }

        } else if (type == EggType.EXPLOSIVE) {
            Random random = new Random();
            int radius = 1 + random.nextInt(2);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {

                    Block target = block.getRelative(dx, 0, dz);

                    int dist = Math.abs(dx) + Math.abs(dz);
                    double chance;

                    if (dist == 0) chance = 1.0;
                    else if (dist == 1) chance = 0.94;
                    else if (dist == 2) chance = 0.63;
                    else chance = 0.14;

                    if (random.nextDouble() >= chance) continue;

                    if (breakable.test(target.getType())) {
                        store(target);
                        target.setType(Material.AIR, false);
                        fxCloud(target);
                    }
                }
            }

            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

        } else if (type == EggType.FREEZE) {
            Random random = new Random();
            int radius = 1 + random.nextInt(2);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {

                    Block target = block.getRelative(dx, 0, dz);

                    int dist = Math.abs(dx) + Math.abs(dz);
                    double chance;

                    if (dist == 0) chance = 1.0;
                    else if (dist == 1) chance = 0.9;
                    else if (dist == 2) chance = 0.59;
                    else chance = 0.28;

                    if (random.nextDouble() >= chance) continue;

                    if (target.getType().name().endsWith("_WOOL")) {
                        store(target);
                        target.setType(Material.BLUE_ICE, false);

                        target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                target.getLocation().add(0.5,0.5,0.5),
                                10, 0.3,0.3,0.3,0.05);
                    }
                }
            }

            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STEM_STEP, 1f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCollide(ProjectileCollideEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        if (getFlagInstanceAtLocation(event.getEntity().getLocation(), null) == null) return;

        if (event.getCollidedWith() != null) {
            event.setCancelled(true);
        }
    }

    private void giveNormalEggs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getFlagInstanceAtLocation(p.getLocation(), null) == null) continue;

            if (count(p, Material.EGG) < 6) {
                p.getInventory().addItem(createEgg(Material.EGG,
                        "§6Eggstroyer",
                        "§7Breaks a block"));
            }
        }
    }

    private void giveBrownEggs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getFlagInstanceAtLocation(p.getLocation(), null) == null) continue;

            if (count(p, Material.BROWN_EGG) < 1) {
                p.getInventory().addItem(createEgg(Material.BROWN_EGG,
                        "§cEggsplosive",
                        "§7Breaks some blocks. How many? No idea"));
            }
        }
    }

    private void giveBlueEggs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getFlagInstanceAtLocation(p.getLocation(), null) == null) continue;

            if (count(p, Material.BLUE_EGG) < 2) {
                p.getInventory().addItem(createEgg(Material.BLUE_EGG,
                        "§bFreezegg",
                        "§7Turns wool into blue ice"));
            }
        }
    }

    private ItemStack createEgg(Material mat, String name, String loreText) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(loreText));

        item.setItemMeta(meta);
        return item;
    }

    private int count(Player p, Material mat) {
        int c = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && i.getType() == mat) c += i.getAmount();
        }
        return c;
    }

    private void store(Block b) {
        if (!originalBlocks.containsKey(b.getLocation())) {
            originalBlocks.put(b.getLocation(), b.getType());
        }
    }

    private void fxPop(Block b) {
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1f);
        b.getWorld().spawnParticle(Particle.CLOUD,
                b.getLocation().add(0.5,0.5,0.5),
                10, 0.3,0.3,0.3,0.05);
    }

    private void fxCloud(Block b) {
        b.getWorld().spawnParticle(Particle.CLOUD,
                b.getLocation().add(0.5,0.5,0.5),
                15, 0.3,0.3,0.3,0.1);
    }

    public int resetArena() {
        int restored = 0;

        for (Map.Entry<Location, Material> e : originalBlocks.entrySet()) {
            e.getKey().getBlock().setType(e.getValue(), false);
            restored++;
        }

        originalBlocks.clear();
        return restored;
    }

    @Override
    public String getName() {
        return "EggSpleef";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableEggSpleef);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableEggSpleef);
    }
}