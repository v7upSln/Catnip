package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Item;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public class CatPlayWithStringGoal implements Goal<Cat> {
    private static final String GOAL_ID = "play_string";
    private final GoalKey<Cat> key;
    private final Main plugin;
    private final Cat cat;
    private Item targetString;
    private int ticksPlayed;
    private int jumpCooldown;

    public CatPlayWithStringGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "play_with_string"));
        this.plugin = plugin;
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting()) return false;
        if (ThreadLocalRandom.current().nextDouble() >= 0.15) return false;
        if (!findString()) return false;

        String active = cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING);
        return active == null;
    }

    @Override
    public boolean shouldStayActive() {
        if (cat.isSitting()) return false;
        return findString();
    }

    @Override
    public void start() {
        ticksPlayed = 0;
        jumpCooldown = 0;
        cat.getPersistentDataContainer().set(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING, GOAL_ID);
    }

    @Override
    public void stop() {
        cat.getPathfinder().stopPathfinding();
        targetString = null;
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
        }
    }

    @Override
    public void tick() {
        if (!findString()) return;
        cat.getPathfinder().moveTo(targetString.getLocation(), 1.2);
        if (targetString.getLocation().distanceSquared(cat.getLocation()) < 1) {
            targetString.setVelocity(new Vector(randomDouble(-0.25, 0.25), randomDouble(-0.25, 0.25), randomDouble(-0.25, 0.25)));
            ticksPlayed++;
            if (jumpCooldown <= 0 && ThreadLocalRandom.current().nextDouble() < 0.05) {
                cat.setVelocity(new Vector(0, 0.5, 0));
                cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1.2f);
                jumpCooldown = 30;
            } else if (jumpCooldown > 0) {
                jumpCooldown--;
            }
            if (ticksPlayed % 20 == 0 && cat.getOwner() != null) {
                plugin.updateBond(cat.getUniqueId(), 1);
            }
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

    private Item getNearbyString() {
        Collection<Item> items = cat.getWorld().getNearbyEntitiesByType(Item.class, cat.getLocation(), 10, 5, 10,
                item -> item.getItemStack().getType() == Material.STRING);
        if (items.isEmpty()) return null;
        int rand = randomInt(0, items.size() - 1);
        int i = 0;
        for (Item item : items) {
            if (i == rand) return item;
            i++;
        }
        return null;
    }

    private boolean findString() {
        if (targetString == null || !targetString.isValid()) {
            targetString = getNearbyString();
            return targetString != null;
        }
        return true;
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
    private double randomDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}
