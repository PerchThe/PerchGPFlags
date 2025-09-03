package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import java.util.Arrays;
import java.util.List;

public class FlagDef_AllowBlockExplosions extends FlagDefinition {

    public FlagDef_AllowBlockExplosions(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public void onFlagSet(Claim claim, String string) {
        setAndSave(liveClaim(claim), true);
    }

    @Override
    public void onFlagUnset(Claim claim) {
        setAndSave(liveClaim(claim), false);
    }

    @Override
    public String getName() {
        return "AllowBlockExplosions";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowBlockExplosions);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowBlockExplosions);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM);
    }

    private void setAndSave(Claim claim, boolean allowed) {
        if (claim == null) return;
        claim.areExplosivesAllowed = allowed;
        try {
            if (GriefPrevention.instance != null && GriefPrevention.instance.dataStore != null) {
                GriefPrevention.instance.dataStore.saveClaim(claim);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Get the live claim instance from GP's datastore that corresponds to the given claim.
     * This avoids mutating a detached/copy instance that the explosion logic wouldn't see.
     */
    private Claim liveClaim(Claim c) {
        if (c == null) return null;
        try {
            org.bukkit.Location a = c.getLesserBoundaryCorner();
            org.bukkit.Location b = c.getGreaterBoundaryCorner();

            int x = (a.getBlockX() + b.getBlockX()) / 2;
            int z = (a.getBlockZ() + b.getBlockZ()) / 2;

            int y;
            try {
                java.lang.reflect.Method is3D = c.getClass().getMethod("is3D");
                boolean threeD = (Boolean) is3D.invoke(c);
                if (threeD) {
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

            org.bukkit.Location center = new org.bukkit.Location(a.getWorld(), x, y, z);

            Claim resolved;
            try {
                // Prefer signature that includes subdivisions and respects height
                resolved = GriefPrevention.instance.dataStore.getClaimAt(center, false, false, null);
            } catch (Throwable t) {
                // Fallback signature
                resolved = GriefPrevention.instance.dataStore.getClaimAt(center, false, null);
            }

            // If the resolved claim has children, walk down to the innermost child containing 'center'
            if (resolved != null) {
                Claim current = resolved;
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

            return c;
        } catch (Throwable t) {
            return c;
        }
    }
}
