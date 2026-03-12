package me.v7upf.catnip.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import me.v7upf.catnip.Main;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cat;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class CatZoomiesGoal implements Goal<Cat> {
    private final Cat cat;
    private final Main plugin;
    private int duration = 0;
    private Location target;
    private static final int ZOOMIE_DURATION = 20 * 10;

    public CatZoomiesGoal(Main plugin, Cat cat) {
        this.plugin = plugin;
        this.cat = cat;
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isSitting() || cat.isInWater() || !cat.isOnGround()) return false;
        if (cat.getWorld().getTime() < 13000 && Math.random() > 0.1) return false;
        if (plugin.getBond(cat) < 50) return false;
        return Math.random() < 0.001;
    }

    @Override
    public boolean shouldStayActive() {
        return duration > 0;
    }

    @Override
    public void start() {
        duration = ZOOMIE_DURATION;
        cat.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ZOOMIE_DURATION, 1, false, false, true));
        pickNewTarget();
    }

    private void pickNewTarget() {
        double angle = Math.random() * 2 * Math.PI;
        double dist = 10 + Math.random() * 10;
        target = cat.getLocation().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        cat.getPathfinder().moveTo(target, 1.5);
    }

    @Override
    public void tick() {
        duration--;
        if (duration <= 0) return;
        if (cat.getLocation().distanceSquared(target) < 4) {
            pickNewTarget();
        }
        if (Math.random() < 0.05) {
            cat.setRotation((float)(Math.random()*360), (float)(Math.random()*30-15));
        }
    }

    @Override
    public void stop() {
        cat.getPathfinder().stopPathfinding();
        cat.removePotionEffect(PotionEffectType.SPEED);
    }

    @Override
    public @NotNull GoalKey<Cat> getKey() {
        return GoalKey.of(Cat.class, new NamespacedKey(plugin, "zoomies"));
    }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }
}
