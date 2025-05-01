package com.dozz3s.rsblocks;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }
}