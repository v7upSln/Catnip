package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class CatSitOnRedCarpetGoal implements Goal<Cat> {
    private static final String GOAL_ID = "sit_carpet";
    private final GoalKey<Cat> key;
    private final Cat cat;
    private Location targetCarpet;
    private int searchCooldown = 0;
    private int purrTick = 0;
    private static final double ACTIVATION_CHANCE = 0.29;

    public CatSitOnRedCarpetGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "sit_on_red_carpet"));
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isLyingDown() && targetCarpet == null) return false;

        if (cat.getOwner() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player owner = (org.bukkit.entity.Player) cat.getOwner();
            if (!cat.getWorld().equals(owner.getWorld()) ||
                cat.getLocation().distanceSquared(owner.getLocation()) > 100) {
                return false;
            }
        }

        if (searchCooldown > 0) {
            searchCooldown--;
            return false;
        }

        if (Math.random() > ACTIVATION_CHANCE) {
            searchCooldown = 100;
            return false;
        }

        targetCarpet = findRedCarpet();
        if (targetCarpet == null) {
            searchCooldown = 120;
            return false;
        }

        // Check if another goal is already active
        NamespacedKey activeKey = new NamespacedKey(cat.getServer().getPluginManager().getPlugin("Catnip"), "active_goal");
        String active = cat.getPersistentDataContainer().get(activeKey, PersistentDataType.STRING);
        return active == null;
    }

    @Override
    public boolean shouldStayActive() {
        if (targetCarpet == null) return false;
        if (targetCarpet.getBlock().getType() != Material.RED_CARPET) return false;
        if (cat.isLeashed()) return false;

        if (cat.getOwner() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player owner = (org.bukkit.entity.Player) cat.getOwner();
            if (!cat.getWorld().equals(owner.getWorld()) ||
                cat.getLocation().distanceSquared(owner.getLocation()) > 100) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start() {
        cat.getPathfinder().moveTo(targetCarpet.clone().add(0.5, 0, 0.5), 1.0);
        NamespacedKey activeKey = new NamespacedKey(cat.getServer().getPluginManager().getPlugin("Catnip"), "active_goal");
        cat.getPersistentDataContainer().set(activeKey, PersistentDataType.STRING, GOAL_ID);
    }

    @Override
    public void stop() {
        targetCarpet = null;
        cat.setLyingDown(false);
        NamespacedKey activeKey = new NamespacedKey(cat.getServer().getPluginManager().getPlugin("Catnip"), "active_goal");
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(activeKey, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(activeKey);
        }
    }

    @Override
    public void tick() {
        if (targetCarpet == null) return;

        Location catLoc = cat.getLocation();
        if (Math.abs(catLoc.getX() - (targetCarpet.getX() + 0.5)) < 0.5 &&
            Math.abs(catLoc.getZ() - (targetCarpet.getZ() + 0.5)) < 0.5) {

            if (!cat.isLyingDown()) {
                cat.setLyingDown(true);
            }

            // Purring every 5 ticks as per Wiki, but with very low volume
            purrTick++;
            if (purrTick >= 5) {
                cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURR, 0.15f, 1.0f);
                purrTick = 0;
            }

        } else {
            if (cat.isLyingDown()) cat.setLyingDown(false);
            cat.getPathfinder().moveTo(targetCarpet.clone().add(0.5, 0, 0.5), 1.0);
        }
    }

    private Location findRedCarpet() {
        Location center = cat.getLocation();
        int radius = 8;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.RED_CARPET) {
                        return b.getLocation();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull GoalKey<Cat> getKey() {
        return key;
    }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.JUMP);
    }
}