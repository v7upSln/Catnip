package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class CatHugOwnerGoal implements Goal<Cat> {
    private static final String GOAL_ID = "hug_owner";
    private final GoalKey<Cat> key;
    private final Main plugin;
    private final Cat cat;
    private Player owner;
    private int cooldown = 0;
    private int nextActivationTick = 0;
    private int telegraphTicks = 0;
    private static final int TELEGRAPH_DURATION = 15;
    private static final int COOLDOWN_HUG = 20 * 60 * 5;
    private static final double ACTIVATION_CHANCE = 0.28;
    private static final int REQUIRED_BOND = 85;

    public CatHugOwnerGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "hug_owner"));
        this.plugin = plugin;
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting()) return false;
        if (cat.getNoDamageTicks() > 0 || cat.getFireTicks() > 0 || !cat.isOnGround() || cat.isInWater()) {
            return false;
        }
        if (cat.isSitting()) return false;
        if (!cat.isTamed()) return false;
        if (!(cat.getOwner() instanceof Player)) return false;
        Player owner = (Player) cat.getOwner();
        if (!cat.getWorld().equals(owner.getWorld()) || cat.getLocation().distanceSquared(owner.getLocation()) > 25)
            return false;
        if (cat.getTicksLived() < nextActivationTick) return false;
        int bond = plugin.getBond(cat);
        if (bond < REQUIRED_BOND) return false;
        if (Math.random() >= ACTIVATION_CHANCE) return false;

        String active = cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING);
        return active == null;
    }

    @Override
    public boolean shouldStayActive() {
        if (cat.isSitting()) return false;
        if (!cat.isTamed()) return false;
        if (!(cat.getOwner() instanceof Player)) return false;
        Player owner = (Player) cat.getOwner();
        return cat.getWorld().equals(owner.getWorld()) && cat.getLocation().distanceSquared(owner.getLocation()) <= 81;
    }

    @Override
    public void start() {
        owner = cat.getOwner() instanceof Player ? (Player) cat.getOwner() : null;
        cooldown = 0;
        cat.getPersistentDataContainer().set(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING, GOAL_ID);
    }

    @Override
    public void stop() {
        cat.getPathfinder().stopPathfinding();
        nextActivationTick = cat.getTicksLived() + COOLDOWN_HUG;
        owner = null;
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
        }
    }

    @Override
    public void tick() {
        if (owner == null) return;

        if (telegraphTicks == 0 && cat.getLocation().distanceSquared(owner.getLocation()) > 4) {
            cat.getPathfinder().stopPathfinding();
            cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURREOW, 0.5f, 1.2f);
            telegraphTicks = TELEGRAPH_DURATION;
        } else if (telegraphTicks > 0) {
            telegraphTicks--;
            cat.lookAt(owner.getLocation());
            if (telegraphTicks == 0) {
                cat.getPathfinder().moveTo(owner.getLocation(), 1.1);
            }
            return;
        }

        cat.getPathfinder().moveTo(owner.getLocation(), 1.1);
        if (cat.getLocation().distanceSquared(owner.getLocation()) < 4 && cooldown <= 0) {
            cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURR, 0.4f, 1.5f);
            cat.getWorld().spawnParticle(Particle.HEART, cat.getLocation().add(0, 1, 0), 7, 0.5, 0.5, 0.5, 0);
            plugin.updateBond(cat, 1);
            cooldown = 60 + (int) (Math.random() * 40);
        } else if (cooldown > 0) {
            cooldown--;
        }
    }

    @Override
    public @NotNull GoalKey<Cat> getKey() {
        return key;
    }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }
}
