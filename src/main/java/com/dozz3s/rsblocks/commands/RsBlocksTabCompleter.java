package com.dozz3s.rsblocks.commands;

import com.dozz3s.rsblocks.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RsBlocksTabCompleter implements TabCompleter {
    private final ConfigManager configManager;

    public RsBlocksTabCompleter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        try {
            if (args.length == 1) {
                if (args[0].isEmpty() || "give".startsWith(args[0].toLowerCase())) {
                    completions.add("give");
                }
                if (args[0].isEmpty() || "reload".startsWith(args[0].toLowerCase())) {
                    completions.add("reload");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                Set<String> blockIds = getAvailableBlockIds();
                for (String id : blockIds) {
                    if (id.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(id);
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Ошибка автодополнения команд: " + e.getMessage());
        }

        return completions;
    }

    private Set<String> getAvailableBlockIds() {
        Set<String> ids = new HashSet<>();

        if (configManager.getConfig().isConfigurationSection("custom_furnaces")) {
            ids.addAll(configManager.getConfig().getConfigurationSection("custom_furnaces").getKeys(false));
        }

        return ids;
    }
}