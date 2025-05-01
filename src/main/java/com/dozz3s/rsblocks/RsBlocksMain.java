package com.dozz3s.rsblocks;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public class RsBlocksMain extends JavaPlugin {
    private BlockManager blockManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.blockManager = new BlockManager(this, configManager);

        getServer().getPluginManager().registerEvents(blockManager, this);
        Objects.requireNonNull(getCommand("rsblocks")).setExecutor(new RsBlocksCommand(this, configManager, blockManager));
        Objects.requireNonNull(getCommand("rsblocks")).setTabCompleter(new RsBlocksTabCompleter());

        blockManager.startTasks();
    }

    @Override
    public void onDisable() {
        blockManager.stopTasks();
    }
}