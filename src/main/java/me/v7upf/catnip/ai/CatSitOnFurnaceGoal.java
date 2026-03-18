package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public class CatSitOnFurnaceGoal implements Goal<Cat> {
    private static final String GOAL_ID = "sit_furnace";
    private final GoalKey<Cat> key;
    private final Cat cat;
    private Location targetFurnace;
    private int searchCooldown = 0;
    
    private int state = 0; 
    private int spinTicks = 0;

    public CatSitOnFurnaceGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "sit_on_furnace"));
        this.cat = cat;
    }

    private boolean isSafeAndCalm() {
        return cat.getNoDamageTicks() == 0 && cat.getFireTicks() <= 0 && !cat.isInWater() && cat.isOnGround();
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting()) return false;
        if (!isSafeAndCalm()) return false;

        if (cat.getOwner() instanceof Player owner) {
            if (!cat.getWorld().equals(owner.getWorld()) || cat.getLocation().distanceSquared(owner.getLocation()) > 25) {
                return false;
            }
        }

        if (searchCooldown > 0) {
            searchCooldown--;
            return false;
        }
        
        targetFurnace = findFurnace();
        if (targetFurnace == null) {
            searchCooldown = 100 + ThreadLocalRandom.current().nextInt(60);
            return false;
        }

        return !cat.getPersistentDataContainer().has(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING);
    }

    @Override
    public boolean shouldStayActive() {
        if (cat.isSitting()) return false;
        if (targetFurnace == null || !isSafeAndCalm()) return false;
        
        Block block = targetFurnace.getBlock();
        if (!(block.getBlockData() instanceof Lightable lightable) || !lightable.isLit()) {
            return false;
        }

        if (cat.getOwner() instanceof Player owner) {
            if (!cat.getWorld().equals(owner.getWorld()) || cat.getLocation().distanceSquared(owner.getLocation()) > 81) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start() {
        state = 0;
        spinTicks = 0;
        cat.getPathfinder().moveTo(targetFurnace.clone().add(0.5, 1, 0.5), 1.0);
        cat.getPersistentDataContainer().set(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING, GOAL_ID);
    }

    @Override
    public void stop() {
        targetFurnace = null;
        state = 0;
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
        }
    }

    @Override
    public void tick() {
        if (targetFurnace == null) return;

        Location dest = targetFurnace.clone().add(0.5, 1, 0.5);
        
        if (state == 0) {
            if (cat.getLocation().distanceSquared(dest) < 0.4) {
                state = 1;
                cat.getPathfinder().stopPathfinding();
            } else {
                cat.getPathfinder().moveTo(dest, 1.0);
            }
        } else if (state == 1) {
            spinTicks++;
            cat.setRotation(cat.getLocation().getYaw() + 36, cat.getLocation().getPitch());
            if (spinTicks >= 10) {
                state = 2;
                cat.setSitting(true);
            }
        } else if (state == 2) {
            if (!cat.isSitting()) cat.setSitting(true);
        }
    }

    private Location findFurnace() {
        Location center = cat.getLocation();
        for (int x = -5; x <= 5; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getBlockData() instanceof Lightable lightable && lightable.isLit()) {
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
    public @NotNull GoalKey<Cat> getKey() { return key; }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.MOVE, GoalType.JUMP, GoalType.LOOK); }
}