package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_PublicContainerStackLimit extends FlagDefinition {

    private static final class Counter {
        long windowStart;
        int stacksTaken;
    }

    private static final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public FlagDef_PublicContainerStackLimit(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public String getName() { return "PublicContainerStackLimit"; }
    @Override public MessageSpecifier getSetMessage(String parameters) { return new MessageSpecifier(Messages.EnabledPublicContainerStackLimit); }
    @Override public MessageSpecifier getUnSetMessage() { return new MessageSpecifier(Messages.DisabledPublicContainerStackLimit); }
    @Override public List<FlagType> getFlagType() { return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        Inventory top = e.getView().getTopInventory();
        if (!isContainerInv(top)) return;

        Location loc = getContainerLocation(top);
        if (loc == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
        if (claim == null) return;

        Flag flag = this.getFlagInstanceAtLocation(loc, player);
        if (flag == null) return;

        if (isOwnerOrBuildTrusted(claim, player)) return;

        if (!isTakingFromTop(e)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Config cfg = parseParams(flag.getParameters());
        int maxStacks = cfg.stacks;
        long windowMs = cfg.windowMs;

        String key = key(claim, loc, player);
        long now = System.currentTimeMillis();
        Counter c = counters.computeIfAbsent(key, k -> {
            Counter cc = new Counter();
            cc.windowStart = now;
            cc.stacksTaken = 0;
            return cc;
        });
        if (windowMs > 0 && now - c.windowStart >= windowMs) {
            c.windowStart = now;
            c.stacksTaken = 0;
        }

        int remainingStacks = Math.max(0, maxStacks - c.stacksTaken);
        if (remainingStacks <= 0) {
            long left = Math.max(0, windowMs - (now - c.windowStart));
            deny(player, maxStacks, left);
            e.setCancelled(true);
            return;
        }

        int stackSize = Math.max(1, clicked.getMaxStackSize());
        int wantItems = estimateTakeItems(e, clicked);
        if (wantItems <= 0) return;

        int allowItems = Math.min(wantItems, remainingStacks * stackSize);
        if (allowItems <= 0) {
            long left = Math.max(0, windowMs - (now - c.windowStart));
            deny(player, maxStacks, left);
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        int given = giveUpTo(player, clicked, allowItems);
        if (given <= 0) {
            sendAB(player, TextMode.Warn + "No space to take items.");
            return;
        }

        clicked.setAmount(clicked.getAmount() - given);
        e.getClickedInventory().setItem(e.getSlot(), clicked.getAmount() > 0 ? clicked : null);

        int stacksUsed = (given + stackSize - 1) / stackSize;
        c.stacksTaken += stacksUsed;

        int afterRemain = Math.max(0, maxStacks - c.stacksTaken);
        if (afterRemain <= 0) {
            long left = Math.max(0, windowMs - (now - c.windowStart));
            if (windowMs > 0) sendAB(player, TextMode.Success + "Limit reached (" + maxStacks + " stacks). Resets in " + formatDurationShort(left) + ".");
            else sendAB(player, TextMode.Success + "Limit reached (" + maxStacks + " stacks).");
        } else {
            sendAB(player, TextMode.Success + "Took " + stacksUsed + " stack(s). Remaining: " + afterRemain + "/" + maxStacks + ".");
        }
    }

    private static boolean isContainerInv(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder h = inv.getHolder();
        if (h instanceof Container) return true;
        InventoryType t = inv.getType();
        switch (t) {
            case CHEST: case BARREL: case HOPPER: case DISPENSER: case DROPPER:
            case FURNACE: case BLAST_FURNACE: case SMOKER: case BREWING:
            case SHULKER_BOX:
                return true;
            default:
                return false;
        }
    }

    private static Location getContainerLocation(Inventory top) {
        InventoryHolder h = top.getHolder();
        if (h instanceof Container) {
            BlockState bs = (BlockState) h;
            return bs.getLocation();
        }
        return null;
    }

    private static boolean isOwnerOrBuildTrusted(Claim claim, Player player) {
        UUID ownerId = claim.getOwnerID();
        boolean owner = ownerId != null && ownerId.equals(player.getUniqueId());
        boolean buildTrusted;
        try {
            buildTrusted = claim.allowBuild(player, Material.AIR) == null;
        } catch (Throwable t) {
            buildTrusted = claim.allowBuild(player, null) == null;
        }
        return owner || buildTrusted;
    }

    private static boolean isTakingFromTop(InventoryClickEvent e) {
        if (e.getSlotType() == SlotType.OUTSIDE) return false;
        return e.getRawSlot() < e.getView().getTopInventory().getSize();
    }

    private static int estimateTakeItems(InventoryClickEvent e, ItemStack clicked) {
        InventoryAction a = e.getAction();
        int amt = clicked.getAmount();
        switch (a) {
            case PICKUP_ALL: return amt;
            case PICKUP_HALF: return (amt + 1) / 2;
            case PICKUP_ONE: return 1;
            case PICKUP_SOME: {
                ItemStack cur = e.getCursor();
                if (cur == null || cur.getType() == Material.AIR) return amt;
                int cursorSpace = Math.max(0, cur.getMaxStackSize() - cur.getAmount());
                return Math.min(cursorSpace, amt);
            }
            case MOVE_TO_OTHER_INVENTORY: return amt;
            case HOTBAR_MOVE_AND_READD: return amt;
            case HOTBAR_SWAP: {
                ItemStack hotbar = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
                int swapSpace = (hotbar == null || hotbar.getType() == Material.AIR)
                        ? clicked.getMaxStackSize()
                        : (hotbar.isSimilar(clicked) ? Math.max(0, clicked.getMaxStackSize() - hotbar.getAmount()) : 0);
                return Math.min(swapSpace, amt);
            }
            case SWAP_WITH_CURSOR: {
                ItemStack cur = e.getCursor();
                if (cur == null || cur.getType() == Material.AIR) return amt;
                if (!cur.isSimilar(clicked)) return 0;
                int curSpace = Math.max(0, cur.getMaxStackSize() - cur.getAmount());
                return Math.min(curSpace, amt);
            }
            default:
                return 0;
        }
    }

    private static int giveUpTo(Player p, ItemStack fromSlot, int maxItems) {
        ItemStack toGive = fromSlot.clone();
        toGive.setAmount(Math.min(maxItems, fromSlot.getAmount()));
        Map<Integer, ItemStack> rem = p.getInventory().addItem(toGive);
        int notAdded = 0;
        for (ItemStack r : rem.values()) notAdded += r.getAmount();
        return toGive.getAmount() - notAdded;
    }

    private static final class Config {
        final int stacks;
        final long windowMs;
        Config(int stacks, long windowMs) { this.stacks = stacks; this.windowMs = windowMs; }
    }

    private static Config parseParams(String params) {
        String s = params == null ? "" : params.trim();
        if (s.isEmpty()) return new Config(1, 7_200_000L);
        String[] parts = s.split("\\s+");
        int stacks = 1;
        long windowMs = 7_200_000L;
        try { stacks = Math.max(1, Integer.parseInt(parts[0])); } catch (NumberFormatException ignored) {}
        if (parts.length >= 2) windowMs = parseDurationMillis(parts[1], 7_200_000L);
        return new Config(stacks, windowMs);
    }

    private static long parseDurationMillis(String param, long def) {
        if (param == null || param.isEmpty()) return def;
        try {
            String s = param.toLowerCase(Locale.ROOT).replace(" ", "");
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2));
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3_600_000L;
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86_400_000L;
            return Long.parseLong(s) * 1000L;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String key(Claim claim, Location loc, Player p) {
        return claim.getID() + ":" + loc.getWorld().getUID() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ":" + p.getUniqueId();
    }

    private static String formatDurationShort(long millis) {
        if (millis <= 0) return "0s";
        long totalSec = millis / 1000;
        long d = totalSec / 86400;
        long h = (totalSec % 86400) / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static void deny(Player p, int maxStacks, long leftMs) {
        if (leftMs > 0) sendAB(p, TextMode.Warn + "Limit reached (" + maxStacks + " stacks). Resets in " + formatDurationShort(leftMs) + ".");
        else sendAB(p, TextMode.Warn + "Limit reached (" + maxStacks + " stacks).");
    }

    private static void sendAB(Player p, String raw) {
        String msg = raw.replaceAll("<[^>]+>", "").trim();
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }
}
