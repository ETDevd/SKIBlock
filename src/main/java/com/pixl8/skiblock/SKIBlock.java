package com.pixl8.skiblock;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
import java.util.UUID;

public class SKIBlock extends JavaPlugin {
    private static SKIBlock instance;
    private IslandData islandData;
    private Configuration config;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        
        // Initialize island data
        islandData = new IslandData(getDataFolder(), this);
        islandData.loadIslands();
        
        // Register commands
        getCommand("createsbworld").setExecutor(new CreateSBWorldCommand());
        getCommand("stp").setExecutor(new StartSkyBlockCommand());
    }

    public static SKIBlock getInstance() {
        return instance;
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

            WorldCreator worldCreator = new WorldCreator("skiblock");
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
                player.sendMessage(config.getString("settings.messages.teleported"));
                return true;
            }

            // Calculate new island position
            int x = config.getInt("settings.starting-x");
            int z = config.getInt("settings.starting-z");
            int count = islandData.getIslandLocations().size();

            // Calculate position based on number of islands
            int row = count / 4;
            int col = count % 4;
            x += (col - 1) * config.getInt("settings.island-distance");
            z += (row - 1) * config.getInt("settings.island-distance");

            // Create island location in skyblock world
            World world = getServer().getWorld("skiblock");
            if (world == null) {
                player.sendMessage(config.getString("settings.messages.world-not-found"));
                return true;
            }

            // Set Y position from config
            int defaultY = config.getInt("settings.default-y");
            
            // Place marker block at default Y
            Location markerLocation = new Location(world, x, defaultY, z);
            
            // Save island location
            islandData.setIslandLocation(playerId, markerLocation);
            
            // Load schematic at the marker location
            if (schematicLoader.loadSchematic(markerLocation)) {
                // Teleport player with offset and proper Y position
                Location teleportLocation = markerLocation.clone();
                teleportLocation.setX(teleportLocation.getX() + config.getDouble("settings.teleport-offset-x"));
                teleportLocation.setZ(teleportLocation.getZ() + config.getDouble("settings.teleport-offset-z"));
                teleportLocation.setY(defaultY + 1); // Teleport slightly above the marker block
                player.teleport(teleportLocation);
                player.sendMessage(config.getString("settings.messages.island-created"));
            } else {
                player.sendMessage(config.getString("settings.messages.schematic-failed"));
            }
            
            return true;
        }
    }
}
