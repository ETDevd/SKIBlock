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
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

public class SKIBlock extends JavaPlugin {
    private static SKIBlock instance;
    private IslandData islandData;
    private Configuration config;
    private final HashMap<UUID, Border> playerBorders = new HashMap<>();
    private final int BORDER_RADIUS = 10; // 10 block radius for each island

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
        
        // Register commands
        getCommand("createsbworld").setExecutor(new CreateSBWorldCommand());
        getCommand("stp").setExecutor(new StartSkyBlockCommand());
        
        // Start border checking task
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayersNearBorders();
            }
        }.runTaskTimer(this, 0L, 10L); // change this if it gets too laggy
    }

    public static SKIBlock getInstance() {
        return instance;
    }

    private static class Border {
        private final Location center;
        private final int size;

        public Border(Location center, int size) {
            this.center = center;
            this.size = size;
        }
    }

    private void checkPlayersNearBorders() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Border border = playerBorders.get(playerId);
            
            // Get the player's island location
            Location islandLoc = islandData.getIslandLocation(playerId);
            if (islandLoc != null && player.getWorld().getName().equals("skiblock")) {
                // Create border if it doesn't exist
                if (border == null) {
                    border = new Border(islandLoc, BORDER_RADIUS);
                    playerBorders.put(playerId, border);
                }

                // Check if player is outside the square border
                double playerX = player.getLocation().getX();
                double playerZ = player.getLocation().getZ();
                double minX = border.center.getX() - border.size;
                double maxX = border.center.getX() + border.size;
                double minZ = border.center.getZ() - border.size;
                double maxZ = border.center.getZ() + border.size;

                boolean isOutside = playerX < minX || playerX > maxX || playerZ < minZ || playerZ > maxZ;
                if (isOutside) {
                    // Get the direction the player was trying to move in
                    double angle = Math.atan2(player.getLocation().getZ() - border.center.getZ(), 
                                            player.getLocation().getX() - border.center.getX());
                    
                    // Calculate a safe location just inside the border
                    double newX = Math.max(minX, Math.min(maxX, playerX));
                    double newZ = Math.max(minZ, Math.min(maxZ, playerZ));
                    
                    Location safeLocation = new Location(player.getWorld(), newX, player.getLocation().getY(), newZ);
                    
                    // Find a safe location at the calculated position
                    safeLocation = findSafeLocation(player, safeLocation, 1);
                    if (safeLocation != null) {
                        // Preserve player's rotation during teleport
                        Location currentLoc = player.getLocation();
                        player.teleport(safeLocation);
                        player.teleport(new Location(safeLocation.getWorld(), 
                                safeLocation.getX(), 
                                safeLocation.getY(), 
                                safeLocation.getZ(),
                                currentLoc.getYaw(),
                                currentLoc.getPitch()));
                        player.sendMessage("You cannot leave your island!");
                    }
                }

                // Show particles when near border
                double distance = player.getLocation().distance(border.center);
                if (distance > border.size) { // Only show when actually hitting the border
                    // Create a sphere of red particles around the player
                    World world = player.getWorld();
                    
                    // Create a sphere with radius 1.5 blocks completely around the player
                    double playerHeight = 1.8; // Typical player height
                    for (double theta = 0; theta < Math.PI; theta += 0.2) {
                        for (double phi = 0; phi < 2 * Math.PI; phi += 0.2) {
                            // Spherical coordinate conversion
                            double x = 1.5 * Math.sin(theta) * Math.cos(phi);
                            double y = 1.5 * Math.sin(theta) * Math.sin(phi);
                            double z = 1.5 * Math.cos(theta);
                            
                            // Center the sphere around the player's body
                            Location loc = new Location(world, 
                                player.getLocation().getX() + x, 
                                player.getLocation().getY() + playerHeight/2 + y, // Center around player's middle
                                player.getLocation().getZ() + z);
                            
                            // Spawn particles
                            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 1, 0, 0, 0, 0.1,
                                new Particle.DustTransition(Color.RED, Color.RED, 1.0f));
                        }
                    }
                }
            }
        }
    }

    private Location findSafeLocation(Player player, Location center, int size) {
        World world = center.getWorld();
        if (world == null) return null;

        int halfSize = size / 2;
        int minX = center.getBlockX() - halfSize;
        int maxX = center.getBlockX() + halfSize;
        int minZ = center.getBlockZ() - halfSize;
        int maxZ = center.getBlockZ() + halfSize;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = world.getMinHeight(); y <= world.getMaxHeight(); y++) {
                    Location loc = new Location(world, x + 0.5, y, z + 0.5);
                    if (isSafeLocation(loc)) {
                        return loc;
                    }
                }
            }
        }
        return null; 
    }

    private boolean isSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        Location below = loc.clone().add(0, -1, 0);
        Location above = loc.clone().add(0, 1, 0);

        return below.getBlock().getType().isSolid()
                && loc.getBlock().getType() == Material.AIR
                && above.getBlock().getType() == Material.AIR;
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
