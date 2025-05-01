package com.dozz3s.rsblocks;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey acceleratedKey;
    private final NamespacedKey accelerationFactorKey;

    private final Map<Block, FurnaceInfo> furnaceCache = new ConcurrentHashMap<>();
    private final Map<Block, BrewingStandInfo> brewingStandCache = new ConcurrentHashMap<>();

    private BukkitRunnable furnaceTask;
    private BukkitRunnable brewingTask;

    public BlockManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.acceleratedKey = new NamespacedKey(plugin, "is_accelerated");
        this.accelerationFactorKey = new NamespacedKey(plugin, "acceleration_factor");
    }

    public void startTasks() {
        this.furnaceTask = new BukkitRunnable() {
            @Override
            public void run() {
                processFurnaces();
            }
        };
        furnaceTask.runTaskTimer(plugin, 0L, 1L);

        this.brewingTask = new BukkitRunnable() {
            @Override
            public void run() {
                processBrewingStands();
            }
        };
        brewingTask.runTaskTimer(plugin, 0L, 1L);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::loadAcceleratedBlocks);
    }

    public void stopTasks() {
        if (furnaceTask != null) furnaceTask.cancel();
        if (brewingTask != null) brewingTask.cancel();
    }

    private void processFurnaces() {
        for (Map.Entry<Block, FurnaceInfo> entry : furnaceCache.entrySet()) {
            Block block = entry.getKey();
            BlockState state = block.getState();

            if (!(state instanceof Furnace)) {
                furnaceCache.remove(block);
                continue;
            }

            Furnace furnace = (Furnace) state;
            String type = getFurnaceType(block);
            if (type == null) continue;

            int accelerationFactor = entry.getValue().getCookTime();

            // Ускорение приготовления
            if (furnace.getCookTime() > 0) {
                short newCookTime = (short) Math.min(furnace.getCookTimeTotal(), furnace.getCookTime() + accelerationFactor);
                furnace.setCookTime(newCookTime);
            }

            // Ускорение расхода топлива
            if (furnace.getBurnTime() > 0) {
                String mode = configManager.getConfig().getString(type + ".fuel_consumption_mode", "matched");
                int fuelConsumption = mode.equalsIgnoreCase("normal") ? 1 : accelerationFactor;
                short newBurnTime = (short) Math.max(0, furnace.getBurnTime() - fuelConsumption);
                furnace.setBurnTime(newBurnTime);
            }

            furnace.update();
        }
    }

    private void processBrewingStands() {
        for (Map.Entry<Block, BrewingStandInfo> entry : brewingStandCache.entrySet()) {
            Block block = entry.getKey();
            BlockState state = block.getState();

            if (!(state instanceof BrewingStand)) {
                brewingStandCache.remove(block);
                continue;
            }

            BrewingStand brewingStand = (BrewingStand) state;
            int accelerationFactor = entry.getValue().getBrewTime();

            if (brewingStand.getBrewingTime() > 0) {
                int newBrewingTime = Math.max(0, brewingStand.getBrewingTime() - accelerationFactor);
                brewingStand.setBrewingTime(newBrewingTime);
                brewingStand.update();
            }
        }
    }

    private void loadAcceleratedBlocks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof PersistentDataHolder) {
                        PersistentDataContainer container = ((PersistentDataHolder) state).getPersistentDataContainer();

                        if (container.has(acceleratedKey, PersistentDataType.BYTE)) {
                            Integer accelerationFactor = container.get(accelerationFactorKey, PersistentDataType.INTEGER);
                            if (accelerationFactor == null) accelerationFactor = 2;

                            Block block = state.getBlock();
                            if (state instanceof Furnace) {
                                furnaceCache.put(block, new FurnaceInfo(accelerationFactor));
                            } else if (state instanceof BrewingStand) {
                                brewingStandCache.put(block, new BrewingStandInfo(accelerationFactor));
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return;

        String name = meta.getDisplayName();
        String[] types = {"furnace", "smoker", "blast_furnace", "brewing_stand"};

        for (String type : types) {
            String configName = ChatUtils.colorize(configManager.getString(type + ".name", "Accelerated " + type));
            if (name.equals(configName)) {
                Block block = event.getBlockPlaced();
                BlockState state = block.getState();

                if (state instanceof PersistentDataHolder) {
                    PersistentDataContainer container = ((PersistentDataHolder) state).getPersistentDataContainer();
                    container.set(acceleratedKey, PersistentDataType.BYTE, (byte) 1);

                    int accelerationFactor = configManager.getInt(type + ".acceleration_factor", 2);
                    container.set(accelerationFactorKey, PersistentDataType.INTEGER, accelerationFactor);
                    state.update();

                    if (type.equals("furnace") || type.equals("smoker") || type.equals("blast_furnace")) {
                        furnaceCache.put(block, new FurnaceInfo(accelerationFactor));
                    } else if (type.equals("brewing_stand")) {
                        brewingStandCache.put(block, new BrewingStandInfo(accelerationFactor));
                    }
                }
                break;
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockState state = block.getState();

        if (!(state instanceof PersistentDataHolder)) return;

        PersistentDataContainer container = ((PersistentDataHolder) state).getPersistentDataContainer();
        if (!container.has(acceleratedKey, PersistentDataType.BYTE)) return;

        dropContainerContents(block, state);

        ItemStack acceleratedItem = createAcceleratedItem(block.getType());
        if (acceleratedItem != null) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), acceleratedItem);
        }

        furnaceCache.remove(block);
        brewingStandCache.remove(block);
    }

    private void dropContainerContents(Block block, BlockState state) {
        if (state instanceof Furnace) {
            Furnace furnace = (Furnace) state;
            dropItems(block, furnace.getInventory().getContents());
        } else if (state instanceof BrewingStand) {
            BrewingStand brewingStand = (BrewingStand) state;
            dropItems(block, brewingStand.getInventory().getContents());
        }
    }

    private void dropItems(Block block, ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }
        }
    }

    public ItemStack createAcceleratedItem(Material material) {
        String type = getTypeFromMaterial(material);
        if (type == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayName = ChatUtils.colorize(configManager.getString(type + ".name", "Accelerated " + type));
        int accelerationFactor = configManager.getInt(type + ".acceleration_factor", 2);

        List<String> lore = new ArrayList<>();
        for (String line : configManager.getConfig().getStringList(type + ".lore")) {
            String processedLine = line.replace("{factor}", String.valueOf(accelerationFactor));
            lore.add(ChatUtils.colorize(processedLine));
        }

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String getFurnaceType(Block block) {
        switch (block.getType()) {
            case FURNACE: return "furnace";
            case SMOKER: return "smoker";
            case BLAST_FURNACE: return "blast_furnace";
            default: return null;
        }
    }

    private String getTypeFromMaterial(Material material) {
        switch (material) {
            case FURNACE: return "furnace";
            case SMOKER: return "smoker";
            case BLAST_FURNACE: return "blast_furnace";
            case BREWING_STAND: return "brewing_stand";
            default: return null;
        }
    }
}