package com.dozz3s.rsblocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RsBlocks extends JavaPlugin implements Listener {

    private static RsBlocks instance;
    private NamespacedKey acceleratedKey;
    private Map<Block, FurnaceInfo> furnaceCache = new HashMap<>();
    private Map<Block, BrewingStandInfo> brewingStandCache = new HashMap<>();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F\\d]{6})");
    private static final int SUB_VERSION;

    static {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String version = packageName.replace(".", ",").split(",")[3];
        version =version.replace("v", "").replace("1_", "").replaceAll("_R\\d", "");
        SUB_VERSION = Integer.parseInt(version);
    }

    public static String colorize(String message) {
        if (SUB_VERSION >= 16) {
            Matcher matcher = HEX_PATTERN.matcher(message);
            StringBuilder builder = new StringBuilder(message.length() + 4 * 8);
            while (matcher.find()) {
                String group = matcher.group(1);
                matcher.appendReplacement(builder,
                        ChatColor.COLOR_CHAR + "x"
                                + ChatColor.COLOR_CHAR + group.charAt(0)
                                + ChatColor.COLOR_CHAR + group.charAt(1)
                                + ChatColor.COLOR_CHAR + group.charAt(2)
                                + ChatColor.COLOR_CHAR + group.charAt(3)
                                + ChatColor.COLOR_CHAR + group.charAt(4)
                                + ChatColor.COLOR_CHAR + group.charAt(5)
                );
            }
            matcher.appendTail(builder);
            message = builder.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public void onEnable() {
        instance = this;
        acceleratedKey = new NamespacedKey(this, "is_accelerated");
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("rsblocks")).setExecutor(new RsBlocksCommand());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, FurnaceInfo> entry : new HashMap<>(furnaceCache).entrySet()) {
                    Block block = entry.getKey();
                    if (block.getType() == Material.FURNACE || block.getType() == Material.SMOKER || block.getType() == Material.BLAST_FURNACE) {
                        BlockState state = block.getState();
                        if (state instanceof org.bukkit.block.Furnace) {
                            org.bukkit.block.Furnace furnace = (org.bukkit.block.Furnace) state;
                            if (furnace.getBurnTime() > 0 && furnace.getCookTime() < furnace.getCookTimeTotal()) {
                                int increase = entry.getValue().getCookTime() - 1;
                                short newCookTime = (short) Math.min(furnace.getCookTime() + increase, furnace.getCookTimeTotal());
                                furnace.setCookTime(newCookTime);
                                furnace.update();
                            }
                        } else {
                            furnaceCache.remove(block);
                        }
                    } else {
                        furnaceCache.remove(block);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    public static RsBlocks getInstance() {
        return instance;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                String[] types = {"furnace", "smoker", "blast_furnace", "brewing_stand"};
                for (String type : types) {
                    String configName = colorize(getConfig().getString(type + ".name", "Accelerated " + type));
                    if (name.equals(configName)) {
                        Block block = event.getBlockPlaced();
                        if (block.getState() instanceof org.bukkit.block.TileState) {
                            org.bukkit.block.TileState state = (org.bukkit.block.TileState) block.getState();
                            state.getPersistentDataContainer().set(acceleratedKey, PersistentDataType.BYTE, (byte) 1);
                            state.update();
                            int accelerationFactor = getConfig().getInt(type + ".acceleration_factor", 2);
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
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof org.bukkit.block.TileState) {
            org.bukkit.block.TileState state = (org.bukkit.block.TileState) block.getState();
            PersistentDataContainer container = state.getPersistentDataContainer();
            if (container.has(acceleratedKey, PersistentDataType.BYTE)) {
                String type;
                Material material;

                switch (block.getType()) {
                    case FURNACE:
                        type = "furnace";
                        material = Material.FURNACE;
                        break;
                    case SMOKER:
                        type = "smoker";
                        material = Material.SMOKER;
                        break;
                    case BLAST_FURNACE:
                        type = "blast_furnace";
                        material = Material.BLAST_FURNACE;
                        break;
                    case BREWING_STAND:
                        type = "brewing_stand";
                        material = Material.BREWING_STAND;
                        break;
                    default:
                        return;
                }

                event.setDropItems(false);

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                String displayName = colorize(getConfig().getString(type + ".name", "Accelerated " + type));
                int accelerationFactor = getConfig().getInt(type + ".acceleration_factor", 2);
                List<String> lore = new ArrayList<>();
                for (String line : getConfig().getStringList(type + ".lore")) {
                    String processedLine = line.replace("{factor}", String.valueOf(accelerationFactor));
                    lore.add(colorize(processedLine));
                }
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                item.setItemMeta(meta);

                block.getWorld().dropItemNaturally(block.getLocation(), item);

                furnaceCache.remove(block);
                brewingStandCache.remove(block);
            }
        }
    }

    @EventHandler
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof org.bukkit.block.BrewingStand) {
            org.bukkit.block.BrewingStand brewingStand = (org.bukkit.block.BrewingStand) block.getState();
            PersistentDataContainer container = brewingStand.getPersistentDataContainer();
            if (container.has(acceleratedKey, PersistentDataType.BYTE)) {
                BrewingStandInfo info = brewingStandCache.get(block);
                if (info != null) {
                    int accelerationFactor = info.getBrewTime();
                    int newBrewingTime = Math.max(0, brewingStand.getBrewingTime() / accelerationFactor);
                    brewingStand.setBrewingTime(newBrewingTime);
                    brewingStand.update();
                }
            }
        }
    }

    private class RsBlocksCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                sender.sendMessage(colorize(getConfig().getString("messages.usage", "&cИспользование: /rsblocks give <игрок> <тип> или /rsblocks reload")));
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("rsblocks.reload")) {
                    sender.sendMessage(colorize(getConfig().getString("messages.reload_no_permission", "&cУ вас нет разрешения на перезагрузку")));
                    return true;
                }
                try {
                    reloadConfig();
                    sender.sendMessage(colorize(getConfig().getString("messages.reload_success", "&aКонфигурация успешно перезагружена")));
                    getLogger().info("Конфигурация перезагружена пользователем " + sender.getName());
                } catch (Exception e) {
                    sender.sendMessage(colorize("&cОшибка при перезагрузке конфигурации: " + e.getMessage()));
                    getLogger().severe("Ошибка при перезагрузке конфигурации: " + e.getMessage());
                }
                return true;
            }

            if (!args[0].equalsIgnoreCase("give") || args.length != 3) {
                sender.sendMessage(colorize(getConfig().getString("messages.usage", "&cИспользование: /rsblocks give <игрок> <тип> или /rsblocks reload")));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(colorize(getConfig().getString("messages.player_not_found", "&cИгрок не найден")));
                return true;
            }

            String type = args[2].toLowerCase();
            Material material;
            switch (type) {
                case "furnace":
                    material = Material.FURNACE;
                    break;
                case "smoker":
                    material = Material.SMOKER;
                    break;
                case "blast_furnace":
                    material = Material.BLAST_FURNACE;
                    break;
                case "brewing_stand":
                    material = Material.BREWING_STAND;
                    break;
                default:
                    sender.sendMessage(colorize(getConfig().getString("messages.invalid_type", "&cНеверный тип. Используйте furnace, smoker, blast_furnace или brewing_stand")));
                    return true;
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            String displayName = colorize(getConfig().getString(type + ".name", "Accelerated " + type));
            int accelerationFactor = getConfig().getInt(type + ".acceleration_factor", 2);
            List<String> lore = new ArrayList<>();
            for (String line : getConfig().getStringList(type + ".lore")) {
                String processedLine = line.replace("{factor}", String.valueOf(accelerationFactor));
                lore.add(colorize(processedLine));
            }
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);

            target.getInventory().addItem(item);
            String message = getConfig().getString("messages.give_success", "&aВыдан {type} игроку {player}");
            message = message.replace("{type}", type).replace("{player}", target.getName());
            sender.sendMessage(colorize(message));
            return true;
        }
    }

    public static class FurnaceInfo {
        private final int cookTime;

        public FurnaceInfo(int accelerationFactor) {
            this.cookTime = accelerationFactor;
        }

        public int getCookTime() {
            return cookTime;
        }
    }

    public static class BrewingStandInfo {
        private final int brewTime;

        public BrewingStandInfo(int accelerationFactor) {
            this.brewTime = accelerationFactor;
        }

        public int getBrewTime() {
            return brewTime;
        }
    }
}