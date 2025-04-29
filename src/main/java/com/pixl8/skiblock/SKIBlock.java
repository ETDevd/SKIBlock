package com.pixl8.skiblock;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SKIBlock extends JavaPlugin implements Listener {
    private static SKIBlock instance;
    private IslandData islandData;
    private Configuration config;
    public IslandData getIslandData() {
        return islandData;
    }
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();

        // Initialize island data
        islandData = new IslandData(getDataFolder(), this);
        islandData.loadIslands();
        // Initialize border control
        BorderControl borderControl = new BorderControl(this, config.getInt("settings.border-size"));

        // Register commands
        getCommand("createsbworld").setExecutor(new CreateSBWorldCommand());
        getCommand("stp").setExecutor(new StartSkyBlockCommand());
        getCommand("sborder").setExecutor(new BorderCommand(borderControl));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new GUIManger(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        
        // Start border checking task
        new BukkitRunnable() {
            @Override
            public void run() {
                borderControl.BorderCheck();
            }
        }.runTaskTimer(this, 0L, 10L); // Runs every 10 ticks (0.5 seconds)

        getLogger().info("SKIBlock plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SKIBlock plugin disabled successfully!");
    }

    public static SKIBlock getInstance() {
        return instance;
    }

    private class StartSkyBlockCommand implements CommandExecutor {
        private final SchematicLoader schematicLoader;

        public StartSkyBlockCommand() {
            this.schematicLoader = new SchematicLoader(SKIBlock.this);
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(config.getString("settings.messages.only-players"));
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();

            // Check if player already has an island
            Location islandLocation = islandData.getIslandLocation(playerId);
            if (islandLocation != null) {
                // Add the offset to be in the middle of the block
                Location adjustedLocation = islandLocation.clone();
                adjustedLocation.setX(adjustedLocation.getX() + config.getDouble("settings.teleport-offset-x"));
                adjustedLocation.setZ(adjustedLocation.getZ() + config.getDouble("settings.teleport-offset-z"));

                // Ensure Y position is above the marker block
                adjustedLocation.setY(islandLocation.getY() + 1);
                player.teleport(adjustedLocation);

                // Show biome selection GUI
                GUIManger guiManager = new GUIManger(SKIBlock.this);
                player.openInventory(guiManager.createDropperUI());
                return true;
            }

            // Check if the SkyBlock world exists
            World world = getServer().getWorld(config.getString("settings.world-name"));

            // Calculate new island position
            int islandDistance = config.getInt("settings.island-distance");
            int minDistance = islandDistance;
            int maxDistance = islandDistance * 2;

            // Generate random distances for x and z
            int randomX = getRandomDistance(minDistance, maxDistance);
            int randomZ = getRandomDistance(minDistance, maxDistance);

            // Randomize direction (positive or negative)
            randomX *= Math.random() < 0.5 ? 1 : -1;
            randomZ *= Math.random() < 0.5 ? 1 : -1;

            // Set Y position from config
            int defaultY = config.getInt("settings.default-y");
            Location markerLocation = new Location(world, randomX, defaultY, randomZ);

            // Save island location
            islandData.setIslandLocation(playerId, markerLocation);

            // Load schematic at the marker location
            if (schematicLoader.loadSchematic(markerLocation)) {
                // Teleport player with offset and proper Y position
                Location teleportLocation = markerLocation.clone();
                teleportLocation.setX(teleportLocation.getX() + config.getDouble("settings.teleport-offset-x"));
                teleportLocation.setZ(teleportLocation.getZ() + config.getDouble("settings.teleport-offset-z"));
                teleportLocation.setY(defaultY + 1); // Teleport slightly above the marker block
                //-player.teleport(teleportLocation);
                player.sendMessage(config.getString("settings.messages.island-created"));
                
                // Show biome selection GUI
                GUIManger guiManager = new GUIManger(SKIBlock.this);
                Inventory dropperUI = guiManager.createDropperUI();
                player.openInventory(dropperUI);

            } else {
                player.sendMessage(config.getString("settings.messages.schematic-failed"));
            }

            return true;
        }

        private int getRandomDistance(int min, int max) {
            return min + (int) (Math.random() * (max - min + 1));
        }
    }

    private class CreateSBWorldCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(config.getString("settings.messages.only-players"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("skiblock.admin")) {
                player.sendMessage(config.getString("settings.messages.no-permission"));
                return true;
            }
            String worldName = config.getString("settings.world-name");

            WorldCreator worldCreator = new WorldCreator(worldName);
            worldCreator.environment(World.Environment.NORMAL);
            worldCreator.type(WorldType.FLAT);
            worldCreator.generator(new VoidGenerator());
            World world = worldCreator.createWorld();

            if (world != null) {
                player.sendMessage(config.getString("settings.messages.world-created"));
            } else {
                player.sendMessage(config.getString("settings.messages.world-failed"));
            }
            return true;
        }
    }

    private class BorderCommand implements CommandExecutor {
        private final BorderControl borderControl;

        public BorderCommand(BorderControl borderControl) {
            this.borderControl = borderControl;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("skiblock.admin")) {
                player.sendMessage("§cYou do not have permission to use this command!");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
                borderControl.removeBorder(player);
                return true;
            }

            if (args.length < 1 || args.length > 3) {
                player.sendMessage("§cUsage: /sborder <size> [x] [z] or /sborder remove");
                return true;
            }

            try {
                int size = Integer.parseInt(args[0]);
                if (size <= 0) {
                    player.sendMessage("§cBorder size must be greater than 0!");
                    return true;
                }

                Location center;
                if (args.length == 3) {
                    double x = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    center = new Location(player.getWorld(), x, player.getLocation().getY(), z);
                } else {
                    center = new Location(player.getWorld(), 
                        Math.floor(player.getLocation().getX()), 
                        Math.floor(player.getLocation().getY()), 
                        Math.floor(player.getLocation().getZ()));
                }

                borderControl.setBorder(player, center, size);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number format! Usage: /sborder <size> [x] [z] or /sborder remove");
            }

            return true;
        }
    }

}
