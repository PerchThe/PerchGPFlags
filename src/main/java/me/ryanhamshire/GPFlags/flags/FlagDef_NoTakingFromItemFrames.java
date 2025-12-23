package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Bungee Chat imports for action bar
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

public class FlagDef_NoTakingFromItemFrames extends FlagDefinition {

    private static final Map<String, String> TAG_TO_COLOR = new HashMap<>();
    private static final Pattern ANGLE_TAGS = Pattern.compile("<[^>]+>");

    static {
        // common color names -> ChatColor
        TAG_TO_COLOR.put("black", ChatColor.BLACK.toString());
        TAG_TO_COLOR.put("dark_blue", ChatColor.DARK_BLUE.toString());
        TAG_TO_COLOR.put("dark_green", ChatColor.DARK_GREEN.toString());
        TAG_TO_COLOR.put("dark_aqua", ChatColor.DARK_AQUA.toString());
        TAG_TO_COLOR.put("dark_red", ChatColor.DARK_RED.toString());
        TAG_TO_COLOR.put("dark_purple", ChatColor.DARK_PURPLE.toString());
        TAG_TO_COLOR.put("gold", ChatColor.GOLD.toString());
        TAG_TO_COLOR.put("gray", ChatColor.GRAY.toString());
        TAG_TO_COLOR.put("dark_gray", ChatColor.DARK_GRAY.toString());
        TAG_TO_COLOR.put("blue", ChatColor.BLUE.toString());
        TAG_TO_COLOR.put("green", ChatColor.GREEN.toString());
        TAG_TO_COLOR.put("aqua", ChatColor.AQUA.toString());
        TAG_TO_COLOR.put("red", ChatColor.RED.toString());
        TAG_TO_COLOR.put("light_purple", ChatColor.LIGHT_PURPLE.toString());
        TAG_TO_COLOR.put("white", ChatColor.WHITE.toString());
        TAG_TO_COLOR.put("yellow", ChatColor.YELLOW.toString());

        // formatting
        TAG_TO_COLOR.put("bold", ChatColor.BOLD.toString());
        TAG_TO_COLOR.put("italic", ChatColor.ITALIC.toString());
        TAG_TO_COLOR.put("underlined", ChatColor.UNDERLINE.toString());
        TAG_TO_COLOR.put("strikethrough", ChatColor.STRIKETHROUGH.toString());
        TAG_TO_COLOR.put("magic", ChatColor.MAGIC.toString());
        TAG_TO_COLOR.put("reset", ChatColor.RESET.toString());
    }

    public FlagDef_NoTakingFromItemFrames(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override public String getName() { return "NoTakingFromItemFrames"; }
    @Override public MessageSpecifier getSetMessage(String parameters) { return new MessageSpecifier(Messages.EnabledNoTakingFromItemFrames); }
    @Override public MessageSpecifier getUnSetMessage() { return new MessageSpecifier(Messages.DisabledNoTakingFromItemFrames); }
    @Override public List<FlagType> getFlagType() { return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof ItemFrame)) return;
        Player player = event.getPlayer();
        ItemFrame frame = (ItemFrame) event.getRightClicked();
        Flag flag = this.getFlagInstanceAtLocation(frame.getLocation(), player);
        if (flag == null) return;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(frame.getLocation(), false, null);
        if (player.isSneaking() && frame.getItem() != null && !frame.getItem().getType().isAir()) {
            event.setCancelled(true);
            String owner = claim != null ? claim.getOwnerName() : "Unknown";
            String rawMsg = plugin.getFlagsDataStore().getMessage(Messages.NoTakingFromItemFrames);
            String msg = rawMsg.replace("{p}", player.getName()).replace("{o}", owner).replace("{0}", player.getName()).replace("{1}", owner);

            // If TextMode.Warn is intended to be a tag like "<gold>" we translate it as well
            String warnPrefix = TextMode.Warn == null ? "" : TextMode.Warn.toString();
            String full = warnPrefix + msg;

            // format for action bar
            String formatted = formatForActionBar(full);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(formatted));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) return;
        ItemFrame frame = (ItemFrame) event.getEntity();
        Player p = event.getDamager() instanceof Player ? (Player) event.getDamager() :
                event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player ?
                        (Player) ((Projectile) event.getDamager()).getShooter() : null;
        Flag flag = this.getFlagInstanceAtLocation(frame.getLocation(), p);
        if (flag == null) return;
        event.setCancelled(true);
        if (p != null) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(frame.getLocation(), false, null);
            String owner = claim != null ? claim.getOwnerName() : "Unknown";
            String rawMsg = plugin.getFlagsDataStore().getMessage(Messages.NoTakingFromItemFrames);
            String msg = rawMsg.replace("{p}", p.getName()).replace("{o}", owner).replace("{0}", p.getName()).replace("{1}", owner);

            String warnPrefix = TextMode.Warn == null ? "" : TextMode.Warn.toString();
            String full = warnPrefix + msg;

            String formatted = formatForActionBar(full);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(formatted));
        }
    }

    /**
     * Replace tags like <gold> and formatting tags with ChatColor equivalents,
     * convert &-codes (like &6) to ChatColor, and strip any remaining angle tags.
     */
    private static String formatForActionBar(String input) {
        if (input == null) return "";

        String result = input;

        // 1) Replace known <tag> -> ChatColor
        for (Map.Entry<String, String> entry : TAG_TO_COLOR.entrySet()) {
            String tag = "<" + entry.getKey() + ">";
            if (result.contains(tag)) {
                result = result.replace(tag, entry.getValue());
            }
            // also support closing-like tags such as </gold> -> reset
            String closing = "</" + entry.getKey() + ">";
            if (result.contains(closing)) {
                result = result.replace(closing, ChatColor.RESET.toString());
            }
        }

        // 2) Convert & codes (&a, &6, &r ...) to ChatColor
        result = ChatColor.translateAlternateColorCodes('&', result);

        // 3) Remove any leftover <...> tags to avoid literal display
        result = ANGLE_TAGS.matcher(result).replaceAll("");

        // 4) Trim and ensure it is not empty
        return result.trim();
    }
}
