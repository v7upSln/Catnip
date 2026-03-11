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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class CatPlayFightOwnerGoal implements Goal<Cat> {
    private static final String GOAL_ID = "play_fight";
    private final GoalKey<Cat> key;
    private final Main plugin;
    private final Cat cat;
    private Player owner;
    private int cooldown = 0;
    private int nextActivationTick = 0;
    private static final int COOLDOWN_FIGHT = 20 * 60 * 10;
    private static final double ACTIVATION_CHANCE = 0.09;
    private static final int MAX_BOND = 35; // Maximum bond to allow play fighting

    public CatPlayFightOwnerGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "play_fight_owner"));
        this.plugin = plugin;
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting() || !cat.isTamed() || !(cat.getOwner() instanceof Player)) return false;

        Player checkOwner = (Player) cat.getOwner();
        if (!cat.getWorld().equals(checkOwner.getWorld()) || cat.getLocation().distanceSquared(checkOwner.getLocation()) > 36)
            return false;

        if (nextActivationTick > 0 && cat.getTicksLived() < nextActivationTick) return false;

        int bond = plugin.getBondLevels().getOrDefault(cat.getUniqueId(), 0);
        if (bond > MAX_BOND) return false;

        if (Math.random() >= ACTIVATION_CHANCE) return false;

        String active = cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING);
        return active == null;
    }

    @Override
    public boolean shouldStayActive() {
        if (cat.isSitting() || !cat.isTamed() || !(cat.getOwner() instanceof Player)) return false;
        Player checkOwner = (Player) cat.getOwner();
        return cat.getWorld().equals(checkOwner.getWorld()) && cat.getLocation().distanceSquared(checkOwner.getLocation()) <= 49;
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
        nextActivationTick = cat.getTicksLived() + COOLDOWN_FIGHT;
        owner = null;
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
        }
    }

    @Override
    public void tick() {
        if (owner == null) return;

        cat.getPathfinder().moveTo(owner.getLocation(), 1.15);

        if (cat.getLocation().distanceSquared(owner.getLocation()) < 4 && cooldown <= 0) {
            owner.damage(1.0, cat);
            cat.setVelocity(new Vector(0, 0.5, 0));
            cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_HISS, 0.45f, 1f);
            cat.getWorld().spawnParticle(Particle.CRIT, owner.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0);
            cooldown = 200;
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
