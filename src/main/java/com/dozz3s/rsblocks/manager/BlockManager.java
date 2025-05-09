package com.dozz3s.rsblocks.manager;

import com.dozz3s.rsblocks.util.ChatUtils;
import com.dozz3s.rsblocks.util.FurnaceInfo;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
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
    private final NamespacedKey luckFactorKey;
    private final NamespacedKey luckChanceKey;
    private final NamespacedKey blockIdKey;

    private final Map<Block, FurnaceInfo> furnaceCache = new ConcurrentHashMap<>();

    public BlockManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.acceleratedKey = new NamespacedKey(plugin, "is_accelerated");
        this.accelerationFactorKey = new NamespacedKey(plugin, "acceleration_factor");
        this.luckFactorKey = new NamespacedKey(plugin, "luck_factor");
        this.luckChanceKey = new NamespacedKey(plugin, "luck_chance");
        this.blockIdKey = new NamespacedKey(plugin, "block_id");
    }

    public void startTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processFurnaces();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        loadBlocks();
    }

    private void loadBlocks() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof PersistentDataHolder) {
                            loadBlock(state);
                        }
                    }
                }
            }
        });
    }

    private void loadBlock(BlockState state) {
        PersistentDataContainer container = ((PersistentDataHolder) state).getPersistentDataContainer();
        Block block = state.getBlock();

        if (container.has(acceleratedKey, PersistentDataType.BYTE)) {
            if (state instanceof Furnace) {
                int factor = container.getOrDefault(accelerationFactorKey, PersistentDataType.INTEGER, 2);
                furnaceCache.put(block, new FurnaceInfo(factor));
            }
        }
    }

    private void processFurnaces() {
        for (Map.Entry<Block, FurnaceInfo> entry : furnaceCache.entrySet()) {
            try {
                Block block = entry.getKey();
                if (!block.getChunk().isLoaded()) continue;

                BlockState state = block.getState();
                if (!(state instanceof Furnace)) {
                    furnaceCache.remove(block);
                    continue;
                }

                processFurnace((Furnace) state, entry.getValue().getCookTime());
            } catch (Exception ignored) {}
        }
    }

    private void processFurnace(Furnace furnace, int accelerationFactor) {
        if (furnace.getCookTime() > 0) {
            short newCookTime = (short) Math.min(furnace.getCookTimeTotal(), furnace.getCookTime() + accelerationFactor);
            furnace.setCookTime(newCookTime);
        }

        if (furnace.getBurnTime() > 0) {
            PersistentDataContainer container = ((PersistentDataHolder) furnace).getPersistentDataContainer();
            String mode = container.getOrDefault(new NamespacedKey(plugin, "fuel_consumption_mode"),
                    PersistentDataType.STRING, "matched");
            int consumption = mode.equalsIgnoreCase("normal") ? 1 : accelerationFactor;
            furnace.setBurnTime((short) Math.max(0, furnace.getBurnTime() - consumption));
        }

        furnace.update();
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        Block block = event.getBlock();
        if (furnaceCache.containsKey(block)) {
            PersistentDataContainer container = ((PersistentDataHolder) block.getState()).getPersistentDataContainer();
            int luckChance = container.getOrDefault(luckChanceKey, PersistentDataType.INTEGER, 0);

            if (luckChance > 0 && new Random().nextInt(100) < luckChance) {
                ItemStack result = event.getResult();
                result.setAmount(result.getAmount() *
                        container.getOrDefault(luckFactorKey, PersistentDataType.INTEGER, 1));
                event.setResult(result);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        Block block = event.getBlockPlaced();
        BlockState state = block.getState();

        if (state instanceof PersistentDataHolder) {
            copyPersistentData(meta.getPersistentDataContainer(),
                    ((PersistentDataHolder) state).getPersistentDataContainer());
            state.update();

            if (meta.getPersistentDataContainer().has(acceleratedKey, PersistentDataType.BYTE)) {
                int factor = meta.getPersistentDataContainer()
                        .getOrDefault(accelerationFactorKey, PersistentDataType.INTEGER, 2);
                if (state instanceof Furnace) {
                    furnaceCache.put(block, new FurnaceInfo(factor));
                }
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

        ItemStack specialItem = createSpecialItemFromContainer(container);
        if (specialItem != null && specialItem.getType() != Material.AIR) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), specialItem);
        }

        dropContainerContents(block, state);
        furnaceCache.remove(block);
    }

    private void copyPersistentData(PersistentDataContainer source, PersistentDataContainer target) {
        for (NamespacedKey key : source.getKeys()) {
            if (source.has(key, PersistentDataType.BYTE)) {
                target.set(key, PersistentDataType.BYTE, source.get(key, PersistentDataType.BYTE));
            }
            else if (source.has(key, PersistentDataType.INTEGER)) {
                target.set(key, PersistentDataType.INTEGER, source.get(key, PersistentDataType.INTEGER));
            }
            else if (source.has(key, PersistentDataType.STRING)) {
                target.set(key, PersistentDataType.STRING, source.get(key, PersistentDataType.STRING));
            }
        }
    }

    public ItemStack createSpecialItem(String blockId) {
        try {
            if (configManager.getConfig().isConfigurationSection("custom_furnaces." + blockId)) {
                return createCustomItem("custom_furnaces." + blockId, Material.FURNACE);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private ItemStack createCustomItem(String configPath, Material defaultMaterial) {
        Material material = Material.matchMaterial(
                configManager.getString(configPath + ".material", defaultMaterial.name()));
        if (material == null) material = defaultMaterial;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        setupItemMeta(meta, configPath);
        item.setItemMeta(meta);
        return item;
    }

    private void setupItemMeta(ItemMeta meta, String configPath) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String blockId = configPath.substring(configPath.lastIndexOf('.') + 1);
        container.set(blockIdKey, PersistentDataType.STRING, blockId);

        if (configPath.startsWith("custom_furnaces")) {
            setupFurnaceMeta(meta, container, configPath);
        }
    }

    private void setupFurnaceMeta(ItemMeta meta, PersistentDataContainer container, String configPath) {
        int factor = configManager.getInt(configPath + ".acceleration_factor", 2);
        int luckChance = configManager.getInt(configPath + ".luck_chance", 0);
        int luckFactor = configManager.getInt(configPath + ".luck_factor", 1);

        container.set(acceleratedKey, PersistentDataType.BYTE, (byte) 1);
        container.set(accelerationFactorKey, PersistentDataType.INTEGER, factor);

        if (luckChance > 0) {
            container.set(luckChanceKey, PersistentDataType.INTEGER, luckChance);
            container.set(luckFactorKey, PersistentDataType.INTEGER, luckFactor);
        }

        container.set(new NamespacedKey(plugin, "fuel_consumption_mode"),
                PersistentDataType.STRING,
                configManager.getString(configPath + ".fuel_consumption_mode", "matched"));

        String nameTemplate = configManager.getString(configPath + ".name", "Ускоренная печь");
        String name = replacePlaceholders(nameTemplate, factor, luckChance, luckFactor);
        meta.setDisplayName(ChatUtils.colorize(name));

        List<String> lore = new ArrayList<>();
        for (String line : configManager.getConfig().getStringList(configPath + ".lore")) {
            lore.add(ChatUtils.colorize(replacePlaceholders(line, factor, luckChance, luckFactor)));
        }
        meta.setLore(lore);
    }

    private String replacePlaceholders(String text, int factor, int luckChance, int luckFactor) {
        return text.replace("{factor}", String.valueOf(factor))
                .replace("{luck_chance}", String.valueOf(luckChance))
                .replace("{luck_factor}", String.valueOf(luckFactor));
    }

    private ItemStack createSpecialItemFromContainer(PersistentDataContainer container) {
        String blockId = container.get(blockIdKey, PersistentDataType.STRING);
        if (blockId == null) return null;

        ItemStack item = createSpecialItem(blockId);
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer newContainer = meta.getPersistentDataContainer();
            copyPersistentData(container, newContainer);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void dropContainerContents(Block block, BlockState state) {
        if (state instanceof Container) {
            for (ItemStack item : ((Container) state).getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    block.getWorld().dropItemNaturally(block.getLocation(), item);
                }
            }
        }
    }

    public void stopTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}