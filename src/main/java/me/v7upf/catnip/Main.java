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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bstats.bukkit.Metrics;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {
    public static final NamespacedKey ACTIVE_GOAL_KEY = new NamespacedKey("catnip", "active_goal");

    public final HashMap<UUID, Integer> purrTimes = new HashMap<>();
    private final HashMap<UUID, Long> lastPurrStart = new HashMap<>();
    private final HashMap<UUID, Integer> bondLevels = new HashMap<>();
    private final HashMap<UUID, Long> lastFedTicks = new HashMap<>();
    private File bondFile;
    private static final long PURR_COOLDOWN_TICKS = 1200L;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        UpdateChecker.check(this, "v7upSln/Catnip");
        new Metrics(this, 30013);
        bondFile = new File(getDataFolder(), "bonds.yml");
        loadBonds();
        if (getCommand("catbond") != null) {
            getCommand("catbond").setExecutor(new CatBondCommand(this));
        }

        // Decay check every 5 minutes (6000 ticks)
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
        saveBonds();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (e.getPlayer().hasPermission("catnip.admin") && UpdateChecker.updateAvailable) {
            e.getPlayer().sendMessage("§6[Catnip] §eAn update is available! Version: §a" + UpdateChecker.latestVersion);
        }
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent e) {
        if (e.getEntity() instanceof Cat cat) {
            if (cat.getPersistentDataContainer().has(ACTIVE_GOAL_KEY, PersistentDataType.STRING)) {
                cat.getPersistentDataContainer().remove(ACTIVE_GOAL_KEY);
            }

            applyDecay(cat);

            Bukkit.getMobGoals().addGoal(cat, 2, new CatSitOnRedCarpetGoal(this, cat));
            Bukkit.getMobGoals().addGoal(cat, 2, new CatSitOnFurnaceGoal(this, cat));
            Bukkit.getMobGoals().addGoal(cat, 3, new CatHugOwnerGoal(this, cat));
            Bukkit.getMobGoals().addGoal(cat, 4, new CatPlayFightOwnerGoal(this, cat));
            Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.COOKED_COD));
            Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.COOKED_SALMON));
            Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.COD));
            Bukkit.getMobGoals().addGoal(cat, 5, new CatPlayWithItemGoal(this, cat, Material.SALMON));
            Bukkit.getMobGoals().addGoal(cat, 6, new CatPlayWithStringGoal(this, cat));
        }
    }

    @EventHandler
    public void onEntityTame(EntityTameEvent e) {
        if (e.getEntity() instanceof Cat cat) {
            // Initialise last fed time for newly tamed cats
            UUID catId = cat.getUniqueId();
            if (!lastFedTicks.containsKey(catId)) {
                lastFedTicks.put(catId, cat.getWorld().getFullTime());
                saveBonds();
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Cat cat && e.getDamager() instanceof Player player) {
            if (cat.isTamed() && player.getUniqueId().equals(cat.getOwnerUniqueId())) {
                // Player hit their own cat -> bond -17
                updateBond(cat.getUniqueId(), -17);
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

                    // Drop the item AT THE CHEST, not at the cat
                    Item dropped = cat.getWorld().dropItem(chestLoc.clone().add(0.5, 1.0, 0.5), stolen);
                    dropped.setPickupDelay(200); // Stop players from grabbing it immediately

                    // Make the cat run to the chest
                    if (cat.isSitting()) cat.setSitting(false);
                    if (cat.isLyingDown()) cat.setLyingDown(false);
                    cat.getPathfinder().moveTo(dropped.getLocation(), 1.3);

                    new BukkitRunnable() {
                        int phase = 0; // 0 = running to chest, 1 = running away with fish in mouth
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (!cat.isValid() || !dropped.isValid() || ticks > 200) {
                                dropped.setPickupDelay(20);
                                this.cancel();
                                return;
                            }

                            if (phase == 0) {
                                // If the cat reaches the chest/fish
                                if (cat.getLocation().distanceSquared(dropped.getLocation()) < 2.5) {
                                    phase = 1;
                                    ticks = 0;
                                    cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_EAT, 1f, 1f);

                                    // Calculate a random location to run away to
                                    double angle = Math.random() * 2 * Math.PI;
                                    double distance = 6 + Math.random() * 4;
                                    Location runTarget = cat.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                                    cat.getPathfinder().moveTo(runTarget, 1.4);
                                } else {
                                    // Keep updating path to the fish just in case
                                    if (ticks % 10 == 0) {
                                        cat.getPathfinder().moveTo(dropped.getLocation(), 1.3);
                                    }
                                }
                            } else if (phase == 1) {
                                // Fish is in the cat's mouth while running
                                Location mouth = cat.getEyeLocation().add(cat.getLocation().getDirection().multiply(0.4));
                                dropped.teleport(mouth);
                                dropped.setVelocity(new Vector(0, 0, 0));

                                // After 3 seconds of running, drop the fish to play with it
                                if (ticks > 60) {
                                    dropped.setPickupDelay(20);
                                    this.cancel();
                                }
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

        // Check if player is holding a fish (feeding)
        boolean isFish = item != null && (item.getType() == Material.COD || item.getType() == Material.SALMON ||
                                           item.getType() == Material.COOKED_COD || item.getType() == Material.COOKED_SALMON);

        if (cat.getOwnerUniqueId() != null && player.getUniqueId().equals(cat.getOwnerUniqueId())) {
            if (isFish) {
                // Feeding: bond +5, update last fed, consume one fish
                updateBond(cat.getUniqueId(), 5);
                lastFedTicks.put(cat.getUniqueId(), cat.getWorld().getFullTime());
                saveBonds();
                // Play eating sound
                cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_EAT, 0.5f, 1.0f);
                // Consume one item
                item.setAmount(item.getAmount() - 1);
                e.setCancelled(true); // prevent any other interaction
                return;
            } else {
                // Simple petting: bond +1
                updateBond(cat.getUniqueId(), 1);
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

    public HashMap<UUID, Integer> getBondLevels() {
        return bondLevels;
    }

    public HashMap<UUID, Long> getLastFedTicks() {
        return lastFedTicks;
    }

    public void updateBond(UUID catId, int amount) {
        bondLevels.merge(catId, amount, (oldVal, inc) -> Math.max(0, Math.min(oldVal + inc, 100))); // cap 0-100
        saveBonds();
    }

    /**
     * Applies starvation decay if the cat hasn't been fed for 7 in-game days.
     * Should be called when the cat loads and periodically while loaded.
     */
    private void applyDecay(Cat cat) {
        UUID catId = cat.getUniqueId();
        if (!lastFedTicks.containsKey(catId)) {
            // First time seeing this cat – initialise with current time
            lastFedTicks.put(catId, cat.getWorld().getFullTime());
            saveBonds();
            return;
        }

        long now = cat.getWorld().getFullTime();
        long lastFed = lastFedTicks.get(catId);
        long elapsed = now - lastFed;
        if (elapsed <= 0) return;

        final long SEVEN_DAYS = 7 * 24000L; // 168,000 ticks
        long periods = elapsed / SEVEN_DAYS;
        if (periods > 0) {
            // Apply decay for each full 7-day period missed
            int bondLoss = (int) (periods * 30);
            updateBond(catId, -bondLoss);
            // Advance last fed by the applied periods
            lastFedTicks.put(catId, lastFed + periods * SEVEN_DAYS);
            saveBonds();
        }
    }

    private void loadBonds() {
        if (!bondFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(bondFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int level = config.getInt(key + ".bond", 0);
                long lastFed = config.getLong(key + ".lastFed", 0);
                bondLevels.put(uuid, level);
                if (lastFed != 0) {
                    lastFedTicks.put(uuid, lastFed);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveBonds() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : bondLevels.entrySet()) {
            UUID uuid = entry.getKey();
            config.set(uuid.toString() + ".bond", entry.getValue());
            Long lastFed = lastFedTicks.get(uuid);
            if (lastFed != null) {
                config.set(uuid.toString() + ".lastFed", lastFed);
            }
        }
        try {
            config.save(bondFile);
        } catch (IOException ignored) {}
    }
}
