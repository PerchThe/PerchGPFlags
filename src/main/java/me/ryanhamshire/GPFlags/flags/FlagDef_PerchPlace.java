package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_PerchPlace extends FlagDefinition {

    private static final long DEFAULT_COOLDOWN_MS = 1_800_000L;
    private static final long INVALID_BLOCK_MESSAGE_COOLDOWN_MS = 5_000L;

    private static final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> reminderTasks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> invalidBlockMessageCooldowns = new ConcurrentHashMap<>();

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

    @Override
    public void onFlagSet(Claim claim, String param) {
        refreshCooldownsForClaim(claim, parseDurationMillis(param, DEFAULT_COOLDOWN_MS));
    }

    @Override
    public void onFlagUnset(Claim claim) {
        clearCooldownsForClaim(claim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPunch(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Player player = e.getPlayer();

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getClickedBlock().getLocation(), false, null);
        if (claim == null) return;

        Flag flag = this.getFlagInstanceAtLocation(e.getClickedBlock().getLocation(), player);
        if (flag == null) return;

        if (player.hasPermission("group.altaccount")) {
            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);

            send(player, TextMode.Warn + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>Alts cannot participate in the event!</gray>");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        Material clickedType = e.getClickedBlock().getType();
        Material handType = hand.getType();

        if (!isValidBlock(clickedType) || !isValidBlock(handType)) {
            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);

            sendInvalidBlockMessage(player, clickedType, handType);
            return;
        }

        if (clickedType == handType) {
            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);

            send(player, TextMode.Warn + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>The same block is already placed here.</gray>");
            return;
        }

        long cooldownMs = parseDurationMillis(flag.getParameters(), DEFAULT_COOLDOWN_MS);
        String key = cooldownKey(claim, player.getUniqueId());

        long now = System.currentTimeMillis();
        long lastUse = cooldowns.getOrDefault(key, 0L);

        if (now - lastUse < cooldownMs) {
            long remaining = cooldownMs - (now - lastUse);

            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);

            send(player, TextMode.Warn + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>You must wait " + formatDuration(remaining) + "!</gray>");
            return;
        }

        e.setCancelled(true);
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);

        e.getClickedBlock().setType(handType);

        cooldowns.put(key, now);

        send(player, TextMode.Success + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>Block replaced! Cooldown: " + formatDuration(cooldownMs) + ".</gray>");

        scheduleReminder(key, player.getUniqueId(), cooldownMs);
    }

    private void refreshCooldownsForClaim(Claim claim, long newCooldownMs) {
        if (claim == null) return;

        String prefix = claim.getID() + ":";
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) continue;

            UUID uuid = uuidFromCooldownKey(key);
            if (uuid == null) continue;

            long elapsed = now - entry.getValue();
            long remaining = newCooldownMs - elapsed;

            cancelReminder(key);

            if (remaining <= 0L) {
                cooldowns.remove(key);
                Player online = Bukkit.getPlayer(uuid);

                if (online != null && online.isOnline()) {
                    send(online, TextMode.Success + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>You can place a block again!</gray>");
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 1.6f);
                }
            } else {
                scheduleReminder(key, uuid, remaining);
            }
        }
    }

    private void clearCooldownsForClaim(Claim claim) {
        if (claim == null) return;

        String prefix = claim.getID() + ":";

        for (String key : new ArrayList<>(cooldowns.keySet())) {
            if (!key.startsWith(prefix)) continue;

            cooldowns.remove(key);
            cancelReminder(key);
        }
    }

    private void scheduleReminder(String key, UUID uuid, long delayMs) {
        cancelReminder(key);

        long delayTicks = Math.max(1L, delayMs / 50L);

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reminderTasks.remove(key);
            cooldowns.remove(key);

            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) return;

            send(online, TextMode.Success + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>You can place a block again!</gray>");
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 1.6f);
        }, delayTicks).getTaskId();

        reminderTasks.put(key, taskId);
    }

    private void cancelReminder(String key) {
        Integer oldTask = reminderTasks.remove(key);
        if (oldTask != null) {
            Bukkit.getScheduler().cancelTask(oldTask);
        }
    }

    private static String cooldownKey(Claim claim, UUID uuid) {
        return claim.getID() + ":" + uuid;
    }

    private static UUID uuidFromCooldownKey(String key) {
        int split = key.indexOf(':');
        if (split < 0 || split + 1 >= key.length()) return null;

        try {
            return UUID.fromString(key.substring(split + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final Set<Material> EXTRA_ALLOWED = EnumSet.of(
            Material.REDSTONE_BLOCK,
            Material.GLOWSTONE
    );

    private static boolean isValidBlock(Material mat) {
        return EXTRA_ALLOWED.contains(mat) || (mat.isBlock() && mat.isOccluding());
    }

    private static void sendInvalidBlockMessage(Player player, Material clickedType, Material handType) {
        String key = invalidMaterialMessageKey(player, clickedType, handType);
        long now = System.currentTimeMillis();
        long last = invalidBlockMessageCooldowns.getOrDefault(key, 0L);

        if (now - last < INVALID_BLOCK_MESSAGE_COOLDOWN_MS) return;

        invalidBlockMessageCooldowns.put(key, now);
        send(player, TextMode.Warn + "<#48ab76>r/Evergreen <dark_gray>»</dark_gray> <gray>Non-full and opaque blocks cannot be used!</gray>");
    }

    private static String invalidMaterialMessageKey(Player player, Material clickedType, Material handType) {
        Material material = !isValidBlock(clickedType) ? clickedType : handType;
        return player.getUniqueId() + ":" + material.name();
    }

    private static long parseDurationMillis(String param, long def) {
        if (param == null || param.isEmpty()) return def;

        try {
            String s = param.toLowerCase().replace(" ", "");

            if (s.endsWith("ms")) return Long.parseLong(s.replace("ms", ""));
            if (s.endsWith("s")) return Long.parseLong(s.replace("s", "")) * 1000L;
            if (s.endsWith("m")) return Long.parseLong(s.replace("m", "")) * 60_000L;
            if (s.endsWith("h")) return Long.parseLong(s.replace("h", "")) * 3_600_000L;
            if (s.endsWith("d")) return Long.parseLong(s.replace("d", "")) * 86_400_000L;

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

        if (days > 0) parts.add(days + (days == 1 ? " day" : " days"));
        if (hours > 0) parts.add(hours + (hours == 1 ? " hour" : " hours"));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        if (seconds > 0 || parts.isEmpty()) parts.add(seconds + (seconds == 1 ? " second" : " seconds"));

        return String.join(", ", parts);
    }

    private static void send(Player p, String msg) {
        MessagingUtil.sendMessage(p, msg);
    }
}