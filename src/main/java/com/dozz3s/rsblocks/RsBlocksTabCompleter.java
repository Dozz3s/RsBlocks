package com.dozz3s.rsblocks;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RsBlocksTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

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
            String[] types = {"furnace", "smoker", "blast_furnace", "brewing_stand"};
            for (String type : types) {
                if (type.startsWith(args[2].toLowerCase())) {
                    completions.add(type);
                }
            }
        }

        return completions;
    }
}