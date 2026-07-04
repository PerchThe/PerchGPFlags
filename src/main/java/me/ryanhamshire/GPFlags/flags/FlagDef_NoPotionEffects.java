package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FlagDef_NoPotionEffects extends PlayerMovementFlagDefinition {

    private final GPFlags gpFlagsPlugin;
    private final NamespacedKey pdcKey;

    public FlagDef_NoPotionEffects(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        this.gpFlagsPlugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "paused_potion_effects");
    }

    // --- PDC Cache Helpers ---

    private Map<String, PotionEffect> getPausedEffects(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Map<String, PotionEffect> map = new HashMap<>();

        if (pdc.has(this.pdcKey, PersistentDataType.STRING)) {
            String data = pdc.get(this.pdcKey, PersistentDataType.STRING);
            if (data != null && !data.isEmpty()) {
                for (String part : data.split(";")) {
                    if (part.isEmpty()) continue;
                    String[] split = part.split(",");
                    if (split.length >= 6) {
                        try {
                            PotionEffectType type = PotionEffectType.getByName(split[0]);
                            if (type == null) continue;
                            int duration = Integer.parseInt(split[1]);
                            int amplifier = Integer.parseInt(split[2]);
                            boolean ambient = Boolean.parseBoolean(split[3]);
                            boolean particles = Boolean.parseBoolean(split[4]);
                            boolean icon = Boolean.parseBoolean(split[5]);
                            map.put(type.getName(), new PotionEffect(type, duration, amplifier, ambient, particles, icon));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return map;
    }

    private void savePausedEffects(Player player, Map<String, PotionEffect> effects) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        if (effects.isEmpty()) {
            pdc.remove(this.pdcKey);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (PotionEffect effect : effects.values()) {
            sb.append(effect.getType().getName()).append(",")
                    .append(effect.getDuration()).append(",")
                    .append(effect.getAmplifier()).append(",")
                    .append(effect.isAmbient()).append(",")
                    .append(effect.hasParticles()).append(",")
                    .append(effect.hasIcon()).append(";");
        }
        pdc.set(this.pdcKey, PersistentDataType.STRING, sb.toString());
    }

    // --- Core Logic ---

    @Override
    public void onFlagSet(Claim claim, String string) {
        World world = claim.getLesserBoundaryCorner().getWorld();
        boolean blockAll = string.equalsIgnoreCase("all");
        String[] paramArray = string.split(" ");

        for (Player player : world.getPlayers()) {
            if (claim.contains(Util.getInBoundsLocation(player), false, false)) {
                if (player.hasPermission("gpflags.bypass.nopotioneffects")) continue;

                Map<String, PotionEffect> pausedEffects = getPausedEffects(player);
                boolean pdcChanged = false;

                for (PotionEffect potionEffect : player.getActivePotionEffects()) {
                    PotionEffectType effectType = potionEffect.getType();
                    boolean blockThis = blockAll;

                    if (!blockThis) {
                        for (String s : paramArray) {
                            if (effectType.getName().equalsIgnoreCase(s)) {
                                blockThis = true;
                                break;
                            }
                        }
                    }

                    if (blockThis) {
                        pausedEffects.put(effectType.getName(), potionEffect);
                        player.removePotionEffect(effectType);
                        pdcChanged = true;
                    }
                }

                if (pdcChanged) {
                    savePausedEffects(player, pausedEffects);
                }
            }
        }
    }

    @Override
    public void onChangeClaim(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        Map<String, PotionEffect> pausedEffects = getPausedEffects(player);
        boolean pdcChanged = false;

        // 1. Always attempt to restore effects first.
        // This ensures if they leave a restricted claim, they get everything back.
        if (!pausedEffects.isEmpty()) {
            for (PotionEffect effect : pausedEffects.values()) {
                player.addPotionEffect(effect);
            }
            pausedEffects.clear();
            pdcChanged = true;
        }

        // 2. Re-evaluate against the new claim's rules.
        if (flagTo != null && !player.hasPermission("gpflags.bypass.nopotioneffects")) {
            boolean blockAll = flagTo.parameters.equalsIgnoreCase("all");
            String[] paramArray = flagTo.getParametersArray();

            for (PotionEffect potionEffect : player.getActivePotionEffects()) {
                PotionEffectType effectType = potionEffect.getType();
                boolean blockThis = blockAll;

                if (!blockThis) {
                    for (String string : paramArray) {
                        if (effectType.getName().equalsIgnoreCase(string)) {
                            blockThis = true;
                            break;
                        }
                    }
                }

                if (blockThis) {
                    pausedEffects.put(effectType.getName(), potionEffect);
                    player.removePotionEffect(effectType);
                    pdcChanged = true;
                }
            }
        }

        // 3. Save state
        if (pdcChanged) {
            savePausedEffects(player, pausedEffects);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        // We only care about effects being ADDED or CHANGED.
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }

        Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), player);
        if (flag == null) return;
        if (player.hasPermission("gpflags.bypass.nopotioneffects")) return;

        PotionEffect potionEffect = event.getNewEffect();
        if (potionEffect == null) return;

        PotionEffectType effectType = potionEffect.getType();
        boolean blockThis = flag.parameters.equalsIgnoreCase("all");

        if (!blockThis) {
            for (String string : flag.getParametersArray()) {
                if (effectType.getName().equalsIgnoreCase(string)) {
                    blockThis = true;
                    break;
                }
            }
        }

        if (blockThis) {
            // Save it to PDC so it gets properly restored upon exiting
            Map<String, PotionEffect> paused = getPausedEffects(player);
            paused.put(effectType.getName(), potionEffect);
            savePausedEffects(player, paused);

            // FIX FOR THE DESYNC:
            // Allow the event to pass so the client registers the item consumption.
            event.setCancelled(false);

            // Strip it 1 tick later to sync the client visually and mechanically.
            this.gpFlagsPlugin.getServer().getScheduler().runTask(this.gpFlagsPlugin, () -> {
                if (player.isOnline()) {
                    player.removePotionEffect(effectType);
                }
            });
        }
    }

    // Prevents exploits: clears the paused effects cache if a player dies in the claim.
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<String, PotionEffect> paused = getPausedEffects(player);
        if (!paused.isEmpty()) {
            paused.clear();
            savePausedEffects(player, paused);
        }
    }

    @Override
    public SetFlagResult validateParameters(String parameters, CommandSender sender) {
        if (parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.SpecifyPotionEffectName));
        }
        if (parameters.equalsIgnoreCase("all")) {
            return new SetFlagResult(true, this.getSetMessage(parameters));
        }
        for (String s : parameters.split(" ")) {
            PotionEffectType pet = PotionEffectType.getByName(s.toUpperCase());
            if (pet == null) {
                return new SetFlagResult(false, new MessageSpecifier(Messages.NotValidPotionName, s));
            }
        }
        return new SetFlagResult(true, this.getSetMessage(parameters));
    }

    @Override
    public String getName() {
        return "NoPotionEffects";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledNoPotionEffects, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledNoPotionEffects);
    }
}