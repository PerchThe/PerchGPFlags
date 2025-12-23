package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class FlagDef_NoPlaceCertainBlock extends FlagDefinition {

    public FlagDef_NoPlaceCertainBlock(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "NoPlaceCertainBlock";
    }

    @Override
    public MessageSpecifier getSetMessage(String p) {
        return new MessageSpecifier(Messages.EnabledNoPlaceCertainBlock);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoPlaceCertainBlock);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

    private String getFlagParams(Flag flag) {
        String s = tryGetterString(flag, "getParams", "getParam", "getParameter", "getParameters", "getValue", "getValues", "getArgs", "getArguments");
        if (s != null && !s.trim().isEmpty()) return s;

        Object o = tryGetterObject(flag, "getParams", "getParam", "getParameter", "getParameters", "getValue", "getValues", "getArgs", "getArguments");
        String converted = coerceToString(o);
        if (converted != null && !converted.trim().isEmpty()) return converted;

        String fromFields = scanFieldsForParams(flag);
        if (fromFields != null && !fromFields.trim().isEmpty()) return fromFields;

        return null;
    }

    private String tryGetterString(Object target, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                Object o = m.invoke(target);
                if (o instanceof String) return (String) o;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object tryGetterObject(Object target, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String coerceToString(Object o) {
        if (o == null) return null;

        if (o instanceof String) return (String) o;

        if (o instanceof String[]) {
            String[] arr = (String[]) o;
            return String.join(" ", arr);
        }

        if (o instanceof Collection) {
            Collection<?> c = (Collection<?>) o;
            List<String> parts = new ArrayList<>();
            for (Object x : c) {
                if (x == null) continue;
                String xs = String.valueOf(x).trim();
                if (!xs.isEmpty()) parts.add(xs);
            }
            return parts.isEmpty() ? null : String.join(" ", parts);
        }

        String s = String.valueOf(o);
        return (s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim())) ? null : s;
    }

    private String scanFieldsForParams(Object target) {
        for (Field f : getAllFields(target.getClass())) {
            try {
                f.setAccessible(true);
                Object v = f.get(target);

                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (looksLikeParams(s)) return s;
                }

                if (v instanceof String[]) {
                    String s = String.join(" ", (String[]) v).trim();
                    if (looksLikeParams(s)) return s;
                }

                if (v instanceof Collection) {
                    String s = coerceToString(v);
                    if (s != null && looksLikeParams(s.trim())) return s.trim();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private List<Field> getAllFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            out.addAll(Arrays.asList(cur.getDeclaredFields()));
            cur = cur.getSuperclass();
        }
        return out;
    }

    private boolean looksLikeParams(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        if (t.length() > 2000) return false;
        return true;
    }

    private Set<Material> getBlockedMaterials(Flag flag) {
        String params = getFlagParams(flag);
        if (params == null || params.trim().isEmpty()) return Collections.emptySet();

        Set<Material> set = new HashSet<>();
        String[] parts = params.split("[; ,]+");
        for (String raw : parts) {
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String upper = s.toUpperCase(Locale.ROOT);
            if (upper.startsWith("MINECRAFT:")) upper = upper.substring("MINECRAFT:".length());

            Material mat = Material.matchMaterial(upper);
            if (mat != null) set.add(mat);
        }

        return set;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        Flag flag = this.getFlagInstanceAtLocation(block.getLocation(), player);
        if (flag == null) return;

        Set<Material> blocked = getBlockedMaterials(flag);
        if (blocked.isEmpty()) return;

        Material type = block.getType();
        if (!blocked.contains(type)) return;

        event.setCancelled(true);

        try {
            String msg = plugin.getFlagsDataStore().getMessage(Messages.NoPlaceCertainBlockMessage);
            msg = msg.replace("{block}", type.name()).replace("{player}", player.getName());
            MessagingUtil.sendMessage(player, TextMode.Warn + msg);
        } catch (Throwable ignored) {
        }
    }
}
