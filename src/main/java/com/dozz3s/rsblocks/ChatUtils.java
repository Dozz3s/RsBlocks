package com.dozz3s.rsblocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.regex.*;

public class ChatUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F\\d]{6})");
    private static final int SUB_VERSION = getSubVersion();

    private static int getSubVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String version = packageName.replace(".", ",").split(",")[3];
        version = version.replace("v", "").replace("1_", "").replaceAll("_R\\d", "");
        return Integer.parseInt(version);
    }

    public static String colorize(String message) {
        if (SUB_VERSION >= 16) {
            Matcher matcher = HEX_PATTERN.matcher(message);
            StringBuilder builder = new StringBuilder(message.length() + 4 * 8);

            while (matcher.find()) {
                String group = matcher.group(1);
                matcher.appendReplacement(builder, ChatColor.COLOR_CHAR + "x"
                        + ChatColor.COLOR_CHAR + group.charAt(0)
                        + ChatColor.COLOR_CHAR + group.charAt(1)
                        + ChatColor.COLOR_CHAR + group.charAt(2)
                        + ChatColor.COLOR_CHAR + group.charAt(3)
                        + ChatColor.COLOR_CHAR + group.charAt(4)
                        + ChatColor.COLOR_CHAR + group.charAt(5));
            }

            matcher.appendTail(builder);
            message = builder.toString();
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }
}