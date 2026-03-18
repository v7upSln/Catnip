package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public class CatSocialGoal implements Goal<Cat> {
    private static final String GOAL_ID = "social_interaction";
    private static final long COOLDOWN_TICKS = 20 * 60 * 5; // 5 minutes cooldown
    private static final double MEET_DISTANCE_SQUARED = 2.0;
    private final GoalKey<Cat> key;
    private final Main plugin;
    private final Cat cat;
    private Cat friend;
    
    private int state = 0; 
    private int interactionTicks = 0; 
    private InteractionType type;

    private enum InteractionType { GROOM, SNIFF, TAG }

    public CatSocialGoal(Main plugin, Cat cat) {
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "cat_social"));
        this.plugin = plugin;
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        // Cooldown check
        if (cat.getTicksLived() < cat.getPersistentDataContainer().getOrDefault(
                new NamespacedKey(plugin, "next_social"), PersistentDataType.LONG, 0L)) return false;

        if (cat.isSitting() || cat.getNoDamageTicks() > 0) return false;
        if (cat.getPersistentDataContainer().has(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING)) return false;
        
        if (ThreadLocalRandom.current().nextDouble() > 0.05) return false;

        friend = findNearbyFriend();
        return friend != null && !friend.isSitting();
    }

    @Override
    public boolean shouldStayActive() {
        return friend != null
                && friend.isValid()
                && interactionTicks > 0
                && !cat.isSitting()
                && !friend.isSitting();
    }

    @Override
    public void start() {
        state = 0;
        interactionTicks = 100 + ThreadLocalRandom.current().nextInt(100);
        type = InteractionType.values()[ThreadLocalRandom.current().nextInt(InteractionType.values().length)];
        
        cat.getPersistentDataContainer().set(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING, GOAL_ID);
        
        // Signal the friend to join the interaction
        friend.getPersistentDataContainer().set(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING, "social_target");
        friend.getPathfinder().stopPathfinding();
        friend.lookAt(cat);
    }

    @Override
    public void stop() {
        // Set cooldown on both cats
        long nextTime = cat.getTicksLived() + COOLDOWN_TICKS;
        cat.getPersistentDataContainer().set(new NamespacedKey(plugin, "next_social"), PersistentDataType.LONG, nextTime);
        if (friend != null) {
            friend.getPersistentDataContainer().set(new NamespacedKey(plugin, "next_social"), PersistentDataType.LONG, nextTime);
            friend.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
            friend.getPathfinder().stopPathfinding();
        }
        
        cat.getPersistentDataContainer().remove(Main.ACTIVE_GOAL_KEY);
        friend = null;
    }

    @Override
    public void tick() {
        interactionTicks--;
        
        if (state == 0) {
            cat.getPathfinder().moveTo(friend.getLocation(), 1.0);
            // Make the "friend" actively approach too so it doesn't look frozen.
            friend.getPathfinder().moveTo(cat.getLocation(), 1.0);

            if (cat.getLocation().distanceSquared(friend.getLocation()) < MEET_DISTANCE_SQUARED) {
                state = 1;
                cat.getPathfinder().stopPathfinding();
                friend.getPathfinder().stopPathfinding();
            }
        } else {
            cat.lookAt(friend.getLocation());
            // Both cats now participate in the "performance"
            performBehavior();
            friend.lookAt(cat); // Ensure mutual eye contact
        }
    }

    private void performBehavior() {
        // Both cats play the sounds/particles to make it feel mutual
        switch (type) {
            case GROOM -> {
                if (interactionTicks % 20 == 0) {
                    cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURR, 0.2f, 1.5f);
                    cat.getWorld().spawnParticle(Particle.HEART, cat.getLocation().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0);
                    friend.getWorld().spawnParticle(Particle.HEART, friend.getLocation().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0);
                    friend.getWorld().playSound(friend.getLocation(), Sound.ENTITY_CAT_PURR, 0.2f, 1.5f);
                }
                // Subtle circling to avoid "statue" behavior while grooming.
                if (interactionTicks % 10 == 0) {
                    friend.getPathfinder().moveTo(cat.getLocation().add(
                            ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                            0,
                            ThreadLocalRandom.current().nextDouble(-1.0, 1.0)
                    ), 1.0);
                }
            }
            case SNIFF -> {
                if (interactionTicks % 15 == 0) {
                    cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.1f, 1.8f);
                    friend.getWorld().playSound(friend.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.1f, 1.8f);
                }
                if (interactionTicks % 12 == 0) {
                    cat.getPathfinder().moveTo(friend.getLocation().add(
                            ThreadLocalRandom.current().nextDouble(-0.75, 0.75),
                            0,
                            ThreadLocalRandom.current().nextDouble(-0.75, 0.75)
                    ), 1.05);
                    friend.getPathfinder().moveTo(cat.getLocation().add(
                            ThreadLocalRandom.current().nextDouble(-0.75, 0.75),
                            0,
                            ThreadLocalRandom.current().nextDouble(-0.75, 0.75)
                    ), 1.05);
                }
            }
            case TAG -> {
                // Both cats move slightly to mimic tag behavior
                cat.getPathfinder().moveTo(friend.getLocation().add(ThreadLocalRandom.current().nextInt(3)-1, 0, ThreadLocalRandom.current().nextInt(3)-1), 1.2);
                friend.getPathfinder().moveTo(cat.getLocation().add(ThreadLocalRandom.current().nextInt(3)-1, 0, ThreadLocalRandom.current().nextInt(3)-1), 1.2);
            }
        }
    }

    private Cat findNearbyFriend() {
        Collection<Cat> cats = cat.getWorld().getNearbyEntitiesByType(Cat.class, cat.getLocation(), 8);
        for (Cat c : cats) {
            if (c.equals(cat)) continue;
            // Only interact if the friend isn't already busy
            if (c.isTamed() && c.getOwnerUniqueId() != null && c.getOwnerUniqueId().equals(cat.getOwnerUniqueId()) 
                && !c.isSitting()
                && !c.getPersistentDataContainer().has(Main.ACTIVE_GOAL_KEY, PersistentDataType.STRING)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public @NotNull GoalKey<Cat> getKey() { return key; }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.MOVE, GoalType.LOOK); }
}