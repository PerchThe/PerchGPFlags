package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_PerchPlace extends FlagDefinition {

    private static final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> reminderTasks = new ConcurrentHashMap<>();

    private final GPFlags plugin;

    public FlagDef_PerchPlace(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "PerchPlace";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnablePerchPlace);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisablePerchPlace);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPunch(PlayerInteractEvent e) {

        if (e.getAction() != Action.LEFT_CLICK_BLOCK
                && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (e.getClickedBlock() == null) return;

        Player player = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
        if (claim == null) return;

        Flag flag = this.getFlagInstanceAtLocation(loc, player);
        if (flag == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        Material clickedType = e.getClickedBlock().getType();
        Material handType = hand.getType();

        if (!isValidBlock(clickedType) || !isValidBlock(handType)) {
            send(player, TextMode.Warn + "<gold>PerchPlace</gold> <dark_gray>»</dark_gray> <gray>This block cannot be used.</gray>");
            e.setCancelled(true);
            return;
        }

        if (clickedType == handType) {
            send(player, TextMode.Warn + "<gold>PerchPlace</gold> <dark_gray>»</dark_gray> <gray>The same block is already placed here.</gray>");
            e.setCancelled(true);
            return;
        }

        // cooldown (default 30 min)
        long cooldownMs = parseDurationMillis(flag.getParameters(), 1_800_000L);
        String key = claim.getID() + ":" + player.getUniqueId();

        long now = System.currentTimeMillis();
        long lastUse = cooldowns.getOrDefault(key, 0L);

        if (now - lastUse < cooldownMs) {
            long remaining = cooldownMs - (now - lastUse);

            send(player, TextMode.Warn + "<gold>PerchPlace</gold> <dark_gray>»</dark_gray> <gray>You must wait " + formatDuration(remaining) + "!");

            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);

            return;
        }

        e.setCancelled(true);
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);

        e.getClickedBlock().setType(handType);

        cooldowns.put(key, now);

        send(player, TextMode.Success + "<gold>PerchPlace</gold> <dark_gray>»</dark_gray> <gray>Block replaced! Cooldown: " + formatDuration(cooldownMs) + ".");

        Integer oldTask = reminderTasks.remove(key);
        if (oldTask != null) {
            Bukkit.getScheduler().cancelTask(oldTask);
        }

        long delayTicks = Math.max(1L, cooldownMs / 50L);

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {

            reminderTasks.remove(key);
            cooldowns.remove(key);

            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null || !online.isOnline()) return;

            send(online, TextMode.Success + "<gold>PerchPlace</gold> <dark_gray>»</dark_gray> <gray>You can place a block again!</gray>");

        }, delayTicks).getTaskId();

        reminderTasks.put(key, taskId);
    }

    private static boolean isValidBlock(Material mat) {
        return mat.isBlock() && mat.isOccluding();
    }

    private static long parseDurationMillis(String param, long def) {

        if (param == null || param.isEmpty()) return def;

        try {

            String s = param.toLowerCase().replace(" ", "");

            if (s.endsWith("ms"))
                return Long.parseLong(s.replace("ms", ""));

            if (s.endsWith("s"))
                return Long.parseLong(s.replace("s", "")) * 1000L;

            if (s.endsWith("m"))
                return Long.parseLong(s.replace("m", "")) * 60_000L;

            if (s.endsWith("h"))
                return Long.parseLong(s.replace("h", "")) * 3_600_000L;

            if (s.endsWith("d"))
                return Long.parseLong(s.replace("d", "")) * 86_400_000L;

            return Long.parseLong(s);

        } catch (Exception e) {
            return def;
        }
    }

    private static String formatDuration(long ms) {

        if (ms <= 0) return "0 seconds";

        long totalSec = (long) Math.ceil(ms / 1000.0);

        long days = totalSec / 86_400;
        totalSec %= 86_400;

        long hours = totalSec / 3_600;
        totalSec %= 3_600;

        long minutes = totalSec / 60;
        long seconds = totalSec % 60;

        List<String> parts = new ArrayList<>();

        if (days > 0) {
            parts.add(days + (days == 1 ? " day" : " days"));
        }

        if (hours > 0) {
            parts.add(hours + (hours == 1 ? " hour" : " hours"));
        }

        if (minutes > 0) {
            parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        }

        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + (seconds == 1 ? " second" : " seconds"));
        }

        return String.join(", ", parts);
    }

    private static void send(Player p, String msg) {
        MessagingUtil.sendMessage(p, msg);
    }
}