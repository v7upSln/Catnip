package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class CatSitOnFurnaceGoal implements Goal<Cat> {
    private final GoalKey<Cat> key;
    private final Cat cat;
    private Location targetFurnace;
    private int searchCooldown = 0;

    public CatSitOnFurnaceGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "sit_on_furnace"));
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting() && targetFurnace == null) return false; 
        
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
        targetFurnace = findFurnace();
        if (targetFurnace == null) {
            searchCooldown = 120; // Check again in 6 seconds
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldStayActive() {
        if (targetFurnace == null) return false;
        Material type = targetFurnace.getBlock().getType();
        if (!(type == Material.FURNACE || type == Material.SMOKER || type == Material.BLAST_FURNACE)) return false;
        
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
        cat.getPathfinder().moveTo(targetFurnace.clone().add(0.5, 1, 0.5), 1.0);
    }

    @Override
    public void stop() {
        targetFurnace = null;
        cat.setSitting(false);
    }

    @Override
    public void tick() {
        if (targetFurnace == null) return;
        
        Location catLoc = cat.getLocation();
        // If the cat is at the coordinates and above the block
        if (Math.abs(catLoc.getX() - (targetFurnace.getX() + 0.5)) < 0.6 &&
            Math.abs(catLoc.getZ() - (targetFurnace.getZ() + 0.5)) < 0.6 &&
            catLoc.getY() >= targetFurnace.getY()) {
            
            if (!cat.isSitting()) {
                cat.setSitting(true);
            }
        } else {
            if (cat.isSitting()) cat.setSitting(false);
            cat.getPathfinder().moveTo(targetFurnace.clone().add(0.5, 1, 0.5), 1.0);
        }
    }

    private Location findFurnace() {
        Location center = cat.getLocation();
        int radius = 6; // Scan radius
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    Material type = b.getType();
                    if (type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER) {
                        // Ensure there is room to sit
                        if (b.getRelative(0, 1, 0).getType().isAir()) {
                            return b.getLocation();
                        }
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
