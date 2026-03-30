package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class FlagDef_EggSpleef extends FlagDefinition {

    // Tracks thrown eggs: UUID -> isBrownEgg
    private final Map<UUID, Boolean> thrownEggs = new HashMap<>();

    public FlagDef_EggSpleef(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);

        Bukkit.getScheduler().runTaskTimer(plugin, this::giveNormalEggs, 20L, 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, this::giveBrownEggs, 20L * 60, 20L * 60);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEggThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            ItemStack item = player.getInventory().getItemInMainHand();
            boolean isBrownEgg = (item != null && item.getType() == Material.BROWN_EGG);
            thrownEggs.put(event.getEntity().getUniqueId(), isBrownEgg);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEggHitBlock(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        Block block = event.getHitBlock();
        if (block == null) return;

        Flag flag = this.getFlagInstanceAtLocation(block.getLocation(), null);
        if (flag == null) return;

        boolean isBrownEgg = thrownEggs.getOrDefault(event.getEntity().getUniqueId(), false);
        thrownEggs.remove(event.getEntity().getUniqueId());

        if (!isBrownEgg) {
            // Normal egg: break single wool block
            if (block.getType().name().endsWith("_WOOL")) {
                block.setType(Material.AIR, false);
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.0f);
                block.getWorld().spawnParticle(Particle.CLOUD,
                        block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.05);
            }
        } else {
            // Brown egg: break 3x3 wool area
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block target = block.getRelative(dx, 0, dz);
                    if (target.getType().name().endsWith("_WOOL")) {
                        target.setType(Material.AIR, false);
                        target.getWorld().spawnParticle(Particle.CLOUD,
                                target.getLocation().add(0.5, 0.5, 0.5),
                                15, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggCollide(ProjectileCollideEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        Flag flag = this.getFlagInstanceAtLocation(event.getEntity().getLocation(), null);
        if (flag == null) return;

        if (event.getCollidedWith() != null && event.getCollidedWith().getType() != null) {
            event.setCancelled(true);
        }
    }

    private void giveNormalEggs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), null);
            if (flag == null) continue;

            int count = countEggs(player, false);
            if (count < 5) {
                ItemStack egg = new ItemStack(Material.EGG, 1);
                ItemMeta meta = egg.getItemMeta();
                meta.setDisplayName("§6Hard Egg");
                List<String> lore = new ArrayList<>();
                lore.add("§7Breaks 1 wool block");
                meta.setLore(lore);
                egg.setItemMeta(meta);

                player.getInventory().addItem(egg);
            }
        }
    }

    private void giveBrownEggs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), null);
            if (flag == null) continue;

            int count = countEggs(player, true);
            if (count < 1) {
                ItemStack egg = new ItemStack(Material.BROWN_EGG, 1);
                ItemMeta meta = egg.getItemMeta();
                meta.setDisplayName("§cExplosive Egg");
                List<String> lore = new ArrayList<>();
                lore.add("§7Breaks 3x3 wool blocks");
                meta.setLore(lore);
                egg.setItemMeta(meta);

                player.getInventory().addItem(egg);
            }
        }
    }

    private int countEggs(Player player, boolean brown) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (brown && item.getType() == Material.BROWN_EGG) count += item.getAmount();
            if (!brown && item.getType() == Material.EGG) count += item.getAmount();
        }
        return count;
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