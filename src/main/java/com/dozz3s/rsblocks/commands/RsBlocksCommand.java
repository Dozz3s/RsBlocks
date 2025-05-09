package com.dozz3s.rsblocks.commands;

import com.dozz3s.rsblocks.manager.BlockManager;
import com.dozz3s.rsblocks.manager.ConfigManager;
import com.dozz3s.rsblocks.util.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RsBlocksCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BlockManager blockManager;

    public RsBlocksCommand(JavaPlugin plugin, ConfigManager configManager, BlockManager blockManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.blockManager = blockManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                sendUsage(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    return handleReload(sender);
                case "give":
                    if (args.length < 3) {
                        sendUsage(sender);
                        return true;
                    }
                    return handleGive(sender, args);
                default:
                    sendUsage(sender);
                    return true;
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Произошла ошибка при выполнении команды. Подробности в консоли.");
            plugin.getLogger().severe("Ошибка выполнения команды: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rsblocks.reload")) {
            sender.sendMessage(ChatUtils.colorize(configManager.getString("messages.reload_no_permission",
                    "&cУ вас нет разрешения на перезагрузку")));
            return true;
        }

        try {
            configManager.reloadConfig();
            sender.sendMessage(ChatUtils.colorize(configManager.getString("messages.reload_success",
                    "&aКонфигурация успешно перезагружена")));
            plugin.getLogger().info("Конфигурация перезагружена пользователем " + sender.getName());
            return true;
        } catch (Exception e) {
            sender.sendMessage(ChatUtils.colorize("&cОшибка при перезагрузке конфигурации: " + e.getMessage()));
            plugin.getLogger().severe("Ошибка перезагрузки конфига: " + e.getMessage());
            return true;
        }
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatUtils.colorize(configManager.getString("messages.player_not_found",
                    "&cИгрок не найден")));
            return true;
        }

        ItemStack item = blockManager.createSpecialItem(args[2]);
        if (item == null || item.getType() == Material.AIR) {
            Set<String> availableTypes = getAvailableBlockIds();
            String errorMsg = configManager.getString("messages.invalid_type", "&cНеверный ID блока. Доступные: {types}")
                    .replace("{types}", String.join(", ", availableTypes));
            sender.sendMessage(ChatUtils.colorize(errorMsg));
            plugin.getLogger().warning("Не удалось создать предмет с ID: " + args[2]);
            return true;
        }

        try {
            target.getInventory().addItem(item);
            String message = configManager.getString("messages.give_success", "&aВыдан {type} игроку {player}");
            message = message.replace("{type}", args[2]).replace("{player}", target.getName());
            sender.sendMessage(ChatUtils.colorize(message));
            return true;
        } catch (Exception e) {
            sender.sendMessage(ChatUtils.colorize("&cОшибка при выдаче предмета"));
            plugin.getLogger().warning("Ошибка выдачи предмета: " + e.getMessage());
            return true;
        }
    }

    private Set<String> getAvailableBlockIds() {
        Set<String> ids = new HashSet<>();

        if (configManager.getConfig().isConfigurationSection("custom_furnaces")) {
            ids.addAll(configManager.getConfig().getConfigurationSection("custom_furnaces").getKeys(false));
        }

        return ids.stream().sorted().collect(Collectors.toSet());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatUtils.colorize(configManager.getString("messages.usage",
                "&cИспользование: /rsblocks give <игрок> <ID_блока> или /rsblocks reload")));
    }
}