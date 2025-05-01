package com.dozz3s.rsblocks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "give":
                return handleGive(sender, args);
            default:
                sendUsage(sender);
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
        } catch (Exception e) {
            sender.sendMessage(ChatUtils.colorize("&cОшибка при перезагрузке конфигурации: " + e.getMessage()));
            plugin.getLogger().severe("Ошибка при перезагрузке конфигурации: " + e.getMessage());
        }

        return true;
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

        Material material = getMaterialFromType(args[2]);
        if (material == null) {
            sender.sendMessage(ChatUtils.colorize(configManager.getString("messages.invalid_type",
                    "&cНеверный тип. Используйте furnace, smoker, blast_furnace или brewing_stand")));
            return true;
        }

        ItemStack item = blockManager.createAcceleratedItem(material);
        target.getInventory().addItem(item);

        String message = configManager.getString("messages.give_success", "&aВыдан {type} игроку {player}");
        message = message.replace("{type}", args[2]).replace("{player}", target.getName());
        sender.sendMessage(ChatUtils.colorize(message));

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatUtils.colorize(configManager.getString("messages.usage",
                "&cИспользование: /rsblocks give <игрок> <тип> или /rsblocks reload")));
    }

    private Material getMaterialFromType(String type) {
        switch (type.toLowerCase()) {
            case "furnace":
                return Material.FURNACE;
            case "smoker":
                return Material.SMOKER;
            case "blast_furnace":
                return Material.BLAST_FURNACE;
            case "brewing_stand":
                return Material.BREWING_STAND;
            default:
                return null;
        }
    }
}