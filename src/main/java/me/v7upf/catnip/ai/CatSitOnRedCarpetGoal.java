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
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public class CatSitOnRedCarpetGoal implements Goal<Cat> {
    private static final String GOAL_ID = "sit_carpet";
    private final GoalKey<Cat> key;
    private final Cat cat;
    private Location targetCarpet;
    private int searchCooldown = 0;
    private int purrTick = 0;
    private int nextPurrDelay = 0;
    private static final double ACTIVATION_CHANCE = 0.15;

    public CatSitOnRedCarpetGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "sit_on_red_carpet"));
        this.cat = cat;
    }

    private boolean isSafeAndCalm() {
        return cat.getNoDamageTicks() == 0 && cat.getFireTicks() <= 0 && !cat.isInWater() && cat.isOnGround();
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting()) return false;
        if (!isSafeAndCalm()) return false;
        if (cat.isLyingDown() && targetCarpet == null) return false;

        if (cat.getOwner() instanceof Player owner) {
            if (!cat.getWorld().equals(owner.getWorld()) || cat.getLocation().distanceSquared(owner.getLocation()) > 25) {
                return false;
            }
        }

        if (searchCooldown > 0) {
            searchCooldown--;
            return false;
        }

        if (Math.random() > ACTIVATION_CHANCE) {
            searchCooldown = ThreadLocalRandom.current().nextInt(300, 600);
            return false;
        }

        targetCarpet = findRedCarpet();
        if (targetCarpet == null) {
            searchCooldown = 200;
            return false;
        }

        return !cat.getPersistentDataContainer().has(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING);
    }

    @Override
    public boolean shouldStayActive() {
        if (cat.isSitting()) return false;
        if (targetCarpet == null || !isSafeAndCalm() || cat.isLeashed()) return false;
        if (targetCarpet.getBlock().getType() != Material.RED_CARPET) return false;

        if (cat.getOwner() instanceof Player owner) {
            if (!cat.getWorld().equals(owner.getWorld()) || cat.getLocation().distanceSquared(owner.getLocation()) > 81) {
                return false; 
            }
        }
        return true;
    }

    @Override
    public void start() {
        cat.getPathfinder().moveTo(targetCarpet.clone().add(0.5, 0, 0.5), 1.0);
        cat.getPersistentDataContainer().set(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING, GOAL_ID);
        purrTick = 0;
        nextPurrDelay = randomPurrDelay();
    }

    @Override
    public void stop() {
        targetCarpet = null;
        cat.setLyingDown(false);
        searchCooldown = 400; 
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
        }
    }

    @Override
    public void tick() {
        if (targetCarpet == null) return;

        Location dest = targetCarpet.clone().add(0.5, 0, 0.5);
        if (cat.getLocation().distanceSquared(dest) < 0.4) {
            if (!cat.isLyingDown()) cat.setLyingDown(true);

            Player owner = (Player) cat.getOwner();
            if (owner != null && owner.getWorld().equals(cat.getWorld()) &&
                    owner.getLocation().distanceSquared(cat.getLocation()) < 16 &&
                    owner.getVelocity().lengthSquared() < 0.01) {
                if (purrTick++ % 40 == 0) {
                    cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURR, 0.2f, 1.2f);
                }
            } else {
                purrTick++;
                if (purrTick >= nextPurrDelay) {
                    float pitch = 0.9f + (float) Math.random() * 0.2f;
                    cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURR, 0.1f, pitch);
                    purrTick = 0;
                    nextPurrDelay = randomPurrDelay();
                }
            }
        } else {
            if (cat.isLyingDown()) cat.setLyingDown(false);
            cat.getPathfinder().moveTo(dest, 1.0);
        }
    }

    private int randomPurrDelay() { return ThreadLocalRandom.current().nextInt(15, 26); }

    private Location findRedCarpet() {
        Location center = cat.getLocation();
        for (int x = -8; x <= 8; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -8; z <= 8; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.RED_CARPET) return b.getLocation();
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull GoalKey<Cat> getKey() { return key; }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.MOVE, GoalType.JUMP); }
}