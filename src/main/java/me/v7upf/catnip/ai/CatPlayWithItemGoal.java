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

public class CatPlayWithItemGoal implements Goal<Cat> {
    private static final String GOAL_ID = "play_item";
    private final GoalKey<Cat> key;
    private final Main plugin;
    private final Cat cat;
    private final Material targetType;
    private Item targetItem;
    private int ticksPlayed;

    public CatPlayWithItemGoal(Main plugin, Cat cat, Material targetType) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "play_with_item"));
        this.plugin = plugin;
        this.cat = cat;
        this.targetType = targetType;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting()) return false;
        if (ThreadLocalRandom.current().nextDouble() >= 0.15) return false;
        if (!findItem()) return false;

        // Check if another goal is already active
        NamespacedKey activeKey = new NamespacedKey(plugin, "active_goal");
        String active = cat.getPersistentDataContainer().get(activeKey, PersistentDataType.STRING);
        return active == null;
    }

    @Override
    public boolean shouldStayActive() {
        if (cat.isSitting()) return false;
        return findItem();
    }

    @Override
    public void start() {
        ticksPlayed = 0;
        cat.getPersistentDataContainer().set(new NamespacedKey(plugin, "active_goal"), PersistentDataType.STRING, GOAL_ID);
    }

    @Override
    public void stop() {
        cat.getPathfinder().stopPathfinding();
        cat.setTarget(null);
        NamespacedKey activeKey = new NamespacedKey(plugin, "active_goal");
        if (GOAL_ID.equals(cat.getPersistentDataContainer().get(activeKey, PersistentDataType.STRING))) {
            cat.getPersistentDataContainer().remove(activeKey);
        }
    }

    @Override
    public void tick() {
        if (!findItem() || !targetItem.isValid()) {
            stop();
            return;
        }
        cat.getPathfinder().moveTo(targetItem.getLocation(), 1.2);
        if (targetItem.getLocation().distanceSquared(cat.getLocation()) < 1) {
            if (ticksPlayed > 600 && targetItem.getItemStack().getType().isEdible()) {
                targetItem.remove();
                cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1, 1);
                if (cat.getOwner() != null) {
                    plugin.updateBond(cat.getUniqueId(), 2);
                }
            } else {
                targetItem.setVelocity(new Vector(randomDouble(-0.25, 0.25), randomDouble(-0.25, 0.25), randomDouble(-0.25, 0.25)));
                ticksPlayed++;
                if (ticksPlayed % 20 == 0 && cat.getOwner() != null) {
                    plugin.updateBond(cat.getUniqueId(), 1);
                }
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

    private Item getNearbyTargetItem() {
        Collection<Item> items = cat.getWorld().getNearbyEntitiesByType(Item.class, cat.getLocation(), 10, 5, 10,
                item -> item.getItemStack().getType() == targetType);
        if (items.size() == 0) return null;
        int rand = randomInt(0, items.size() - 1);
        int i = 0;
        for (Item item : items) {
            if (i == rand) return item;
            i++;
        }
        return null;
    }

    private boolean findItem() {
        if (targetItem == null || !targetItem.isValid()) {
            targetItem = getNearbyTargetItem();
            return targetItem != null;
        } else return true;
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
    private double randomDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}