package me.v7upf.catnip;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import me.v7upf.catnip.ai.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bstats.bukkit.Metrics;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {
    public static final NamespacedKey ACTIVE_GOAL_KEY = new NamespacedKey("catnip", "active_goal");
    public static final NamespacedKey BOND_KEY = new NamespacedKey("catnip", "bond");
    public static final NamespacedKey LAST_FED_KEY = new NamespacedKey("catnip", "last_fed");

    public final HashMap<UUID, Integer> purrTimes = new HashMap<>();
    private final HashMap<UUID, Long> lastPurrStart = new HashMap<>();
    private static final long PURR_COOLDOWN_TICKS = 1200L;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UpdateChecker.check(this, "v7upSln/Catnip");
        });

        new Metrics(this, 30013);
        if (getCommand("catbond") != null) {
            getCommand("catbond").setExecutor(new CatBondCommand(this));
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Cat cat : world.getEntitiesByClass(Cat.class)) {
                injectCatGoals(cat);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    for (Cat cat : world.getEntitiesByClass(Cat.class)) {
                        applyDecay(cat);
                    }
                }
            }
        }.runTaskTimer(this, 6000, 6000);
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (e.getPlayer().hasPermission("catnip.admin") && UpdateChecker.updateAvailable) {
            e.getPlayer().sendMessage("§6[Catnip] §eAn update is available! Version: §a" + UpdateChecker.latestVersion);
        }
    }

    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (e.getEntity() instanceof Cat cat) {
            UUID id = cat.getUniqueId();
            purrTimes.remove(id);
            lastPurrStart.remove(id);
        }
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent e) {
        if (e.getEntity() instanceof Cat cat) {
            applyDecay(cat);
            injectCatGoals(cat);
        }
    }

    public void injectCatGoals(Cat cat) {
        if (cat.getPersistentDataContainer().has(ACTIVE_GOAL_KEY, PersistentDataType.STRING)) {
            cat.getPersistentDataContainer().remove(ACTIVE_GOAL_KEY);
        }

        Bukkit.getMobGoals().addGoal(cat, 2, new CatSitOnRedCarpetGoal(this, cat));
        Bukkit.getMobGoals().addGoal(cat, 2, new CatSitOnFurnaceGoal(this, cat));
        Bukkit.getMobGoals().addGoal(cat, 3, new CatHugOwnerGoal(this, cat));
        Bukkit.getMobGoals().addGoal(cat, 4, new CatPlayFightOwnerGoal(this, cat));
        Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.COOKED_COD));
        Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.COOKED_SALMON));
        Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.COD));
        Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.SALMON));
        Bukkit.getMobGoals().addGoal(cat, 6, new CatPlayWithStringGoal(this, cat));
        Bukkit.getMobGoals().addGoal(cat, 7, new CatZoomiesGoal(this, cat));
    }

    @EventHandler
    public void onEntityTame(EntityTameEvent e) {
        if (e.getEntity() instanceof Cat cat) {
            if (getLastFed(cat) == 0L) {
                setLastFed(cat, System.currentTimeMillis());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Cat cat && e.getDamager() instanceof Player player) {
            if (cat.isTamed() && player.getUniqueId().equals(cat.getOwnerUniqueId())) {
                updateBond(cat, -17);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType() != InventoryType.CHEST) return;

        Collection<Entity> entities = e.getPlayer().getNearbyEntities(6, 3, 6);
        for (Entity entity : entities) {
            if (entity instanceof Cat cat && Math.random() < 0.60) {
                Inventory inv = e.getInventory();
                ItemStack targetFish = null;
                for (ItemStack item : inv.getContents()) {
                    if (item != null && (item.getType() == Material.SALMON || item.getType() == Material.COD)) {
                        targetFish = item;
                        break;
                    }
                }

                if (targetFish != null) {
                    Location chestLoc = e.getInventory().getLocation();
                    if (chestLoc == null) return;

                    ItemStack stolen = targetFish.clone();
                    stolen.setAmount(1);
                    targetFish.setAmount(targetFish.getAmount() - 1);
                    ((Player) e.getPlayer()).updateInventory();

                    Item dropped = cat.getWorld().dropItem(chestLoc.clone().add(0.5, 1.0, 0.5), stolen);
                    dropped.setPickupDelay(200);

                    if (cat.isSitting()) cat.setSitting(false);
                    if (cat.isLyingDown()) cat.setLyingDown(false);
                    cat.getPathfinder().moveTo(dropped.getLocation(), 1.3);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (!cat.isValid() || !dropped.isValid() || ticks > 200) {
                                if (dropped.isValid()) dropped.remove();
                                this.cancel();
                                return;
                            }

                            if (cat.getLocation().distanceSquared(dropped.getLocation()) < 2.5) {
                                cat.getEquipment().setItem(EquipmentSlot.HAND, stolen);
                                dropped.remove();
                                cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_EAT, 1f, 1f);

                                double angle = Math.random() * 2 * Math.PI;
                                double distance = 6 + Math.random() * 4;
                                Location runTarget = cat.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                                cat.getPathfinder().moveTo(runTarget, 1.4);

                                Bukkit.getScheduler().runTaskLater(Main.this, () -> {
                                    if (cat.isValid()) {
                                        cat.getEquipment().setItem(EquipmentSlot.HAND, null);
                                    }
                                }, 60L);
                                this.cancel();
                            } else if (ticks % 10 == 0) {
                                cat.getPathfinder().moveTo(dropped.getLocation(), 1.3);
                            }
                            ticks++;
                        }
                    }.runTaskTimer(this, 1L, 1L);
                }
            }
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return; // only main hand
        Entity rightClicked = e.getRightClicked();
        if (rightClicked.getType() != EntityType.CAT) return;

        Cat cat = (Cat) rightClicked;
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        boolean isFish = item != null && (item.getType() == Material.COD || item.getType() == Material.SALMON ||
                                           item.getType() == Material.COOKED_COD || item.getType() == Material.COOKED_SALMON);

        if (cat.getOwnerUniqueId() != null && player.getUniqueId().equals(cat.getOwnerUniqueId())) {
            if (isFish) {
                updateBond(cat, 5);
                setLastFed(cat, System.currentTimeMillis());
                cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_EAT, 0.5f, 1.0f);

                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    item.setAmount(item.getAmount() - 1);
                }

                return;
            } else {
                updateBond(cat, 1);
            }
        }

        long currentTick = cat.getWorld().getFullTime();
        if (purrTimes.containsKey(rightClicked.getUniqueId())) return;
        if (lastPurrStart.containsKey(cat.getUniqueId()) && currentTick - lastPurrStart.get(cat.getUniqueId()) < PURR_COOLDOWN_TICKS) return;

        double rand = Math.random();
        if (rand < 0.3) {
            lastPurrStart.put(cat.getUniqueId(), currentTick);
            purrTimes.put(cat.getUniqueId(), 10);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!cat.isValid() || cat.isDead()) {
                        purrTimes.remove(cat.getUniqueId());
                        this.cancel();
                        return;
                    }
                    cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURR, 0.25f, 1.75f);
                    if (Math.random() <= 0.25)
                        cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURREOW, 0.3f, 1.75f);

                    Integer timeLeft = purrTimes.get(cat.getUniqueId());
                    if (timeLeft == null || timeLeft <= 0) {
                        purrTimes.remove(cat.getUniqueId());
                        this.cancel();
                        return;
                    }
                    purrTimes.put(cat.getUniqueId(), timeLeft - 1);
                }
            }.runTaskTimer(this, 0, 30);
        } else if (rand < 0.6) {
            cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_PURREOW, 0.3f, 1f);
        } else if (rand < 0.99) {
            cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.35f, 1f);
        } else {
            cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_HISS, 0.35f, 1f);
        }
    }

    public int getBond(Cat cat) {
        return cat.getPersistentDataContainer().getOrDefault(BOND_KEY, PersistentDataType.INTEGER, 0);
    }

    public void setBond(Cat cat, int bond) {
        bond = Math.max(0, Math.min(bond, 100));
        cat.getPersistentDataContainer().set(BOND_KEY, PersistentDataType.INTEGER, bond);
    }

    public void updateBond(Cat cat, int amount) {
        int current = getBond(cat);
        setBond(cat, current + amount);
    }

    public long getLastFed(Cat cat) {
        return cat.getPersistentDataContainer().getOrDefault(LAST_FED_KEY, PersistentDataType.LONG, 0L);
    }

    public void setLastFed(Cat cat, long time) {
        cat.getPersistentDataContainer().set(LAST_FED_KEY, PersistentDataType.LONG, time);
    }

    private void applyDecay(Cat cat) {
        if (getLastFed(cat) == 0L) {
            setLastFed(cat, System.currentTimeMillis());
            return;
        }

        long now = System.currentTimeMillis();
        long lastFed = getLastFed(cat);
        long elapsed = now - lastFed;
        if (elapsed <= 0) return;

        final long SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L;
        long periods = elapsed / SEVEN_DAYS_MILLIS;
        if (periods > 0) {
            int bondLoss = (int) (periods * 30);
            updateBond(cat, -bondLoss);
            setLastFed(cat, lastFed + periods * SEVEN_DAYS_MILLIS);
        }
    }
}
