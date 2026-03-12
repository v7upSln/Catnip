package me.v7upf.catnip;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CatBondCommand implements CommandExecutor {
    private final Main plugin;

    public CatBondCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender.hasPermission("catnip.admin")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                UpdateChecker.check(plugin, "v7upSln/Catnip");
                if (UpdateChecker.updateAvailable) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§6[Catnip] §eAn update is available! Version: §a" + UpdateChecker.latestVersion);
                    });
                }
            });
        }
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player: /catbond <player>");
                return true;
            }
            target = (Player) sender;
        } else {
            if (!sender.hasPermission("catnip.admin")) {
                sender.sendMessage("§cYou do not have permission to view other players' cats.");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
        }

        String tn = target.getName() != null ? target.getName() : "Unknown";
        StringBuilder message = new StringBuilder("§6Cats owned by §e" + tn + "§6:\n");
        boolean found = false;

        for (World w : Bukkit.getWorlds()) {
            for (Cat cat : w.getEntitiesByClass(Cat.class)) {
                if (cat.isTamed() && target.getUniqueId().equals(cat.getOwnerUniqueId())) {
                    found = true;
                    int bond = plugin.getBond(cat);
                    String collar = cat.getCollarColor() != null ? cat.getCollarColor().toString() : "Unknown";
                    String name = cat.getName() != null ? cat.getName() : "Unnamed";
                    String shortId = cat.getUniqueId().toString().substring(0, 8);
                    message.append(" §7- §f").append(name)
                            .append(" §7(ID: ").append(shortId).append(")")
                            .append(" §7Bond: §a").append(bond).append("%")
                            .append(" §7Collar: ").append(collar).append("\n");
                }
            }
        }

        if (!found) {
            message.append(" §7No cats found.");
        }

        sender.sendMessage(message.toString());
        return true;
    }
}
