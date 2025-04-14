package com.pixl8.skiblock;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class SKIBlock extends JavaPlugin implements Listener {
    private static SKIBlock instance;
    private IslandData islandData;
    private Configuration config;
    private SchematicLoader schematicLoader;
    private final HashMap<UUID, Border> playerBorders = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();

        // Check if the SkyBlock world exists
        String worldName = config.getString("settings.world-name");
        World world = getServer().getWorld(worldName);
        if (world == null) {
            // Create the SkyBlock world
            WorldCreator worldCreator = new WorldCreator(worldName);
            worldCreator.environment(World.Environment.NORMAL);
            worldCreator.type(WorldType.FLAT);
            worldCreator.generator(new VoidGenerator());
            world = worldCreator.createWorld();

            if (world != null) {
                getLogger().info(config.getString("settings.messages.world-created"));
            } else {
                getLogger().severe(config.getString("settings.messages.world-failed"));
            }
        }
        // Initialize island data
        islandData = new IslandData(getDataFolder(), this);
        islandData.loadIslands();
        // Initialize schematic loader
        schematicLoader = new SchematicLoader(this);

        // Register commands
        // Register commands
        getCommand("stp").setExecutor(new StartSkyBlockCommand());
        getCommand("sborder").setExecutor(new BorderCommand());

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Start border check task
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayersNearBorders();
                checkItemsNearBorders();
            }
        }.runTaskTimer(this, 0L, 10L);

        getLogger().info("SKIBlock plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SKIBlock plugin disabled successfully!");
    }

    public static SKIBlock getInstance() {
        return instance;
    }

    private void checkPlayersNearBorders() {
        String worldName = config.getString("settings.world-name");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().getName().equals(worldName)) continue;
    
            UUID playerId = player.getUniqueId();
            Location markerLocation = islandData.getIslandLocation(playerId);
            if (markerLocation == null) continue;
    
            Border border = playerBorders.get(playerId);
            Location safeLocation = null;
            if (border == null) continue;
    
            Location playerLoc = player.getLocation();
            double px = playerLoc.getX();
            double pz = playerLoc.getZ();
    
            double PushSize = border.size + 0.5;
            double minX = markerLocation.getX() - PushSize;
            double maxX = markerLocation.getX() + PushSize;
            double minZ = markerLocation.getZ() - PushSize;
            double maxZ = markerLocation.getZ() + PushSize;
    
            // Check if the player is outside the border
            if (px < minX || px > maxX || pz < minZ || pz > maxZ) {
                Location teleportLocation = markerLocation.clone();
                 safeLocation = findSafeLocation(player, teleportLocation, border.size);
                if (safeLocation != null) {
                    player.teleport(safeLocation);
                    player.sendMessage("You were teleported back inside your border!");
                }
            }
    
            // Spawn particles only if the player is near or looking at the border
            spawnParticleLine(player, minX, maxX, minZ, maxZ);
        }
    }
    
    private void checkItemsNearBorders() {
        String worldName = config.getString("settings.world-name");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
    
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Location markerLocation = islandData.getIslandLocation(playerId);
            if (markerLocation == null) continue;
    
            Border border = playerBorders.get(playerId);
            if (border == null) continue;
    
            double PushSize = border.size + 0.5;
            double minX = markerLocation.getX() - PushSize;
            double maxX = markerLocation.getX() + PushSize;
            double minZ = markerLocation.getZ() - PushSize;
            double maxZ = markerLocation.getZ() + PushSize;
    
            world.getEntitiesByClass(org.bukkit.entity.Item.class).forEach(item -> {
                Location itemLoc = item.getLocation();
                if (itemLoc.getX() < minX || itemLoc.getX() > maxX || itemLoc.getZ() < minZ || itemLoc.getZ() > maxZ) {
                    item.teleport(markerLocation);
                }
            });
        }
    }
    
    private void spawnParticleLine(Player player, double minX, double maxX, double minZ, double maxZ) {
        World world = player.getWorld();
        if (world == null) return;
    
        // Use a universally supported particle for Geyser players
        Particle particleType = Particle.CLOUD; // Replace with a supported particle like FLAME or CLOUD
    
        // Get player's Y position and calculate vertical range
        double playerY = player.getLocation().getY();
        double minY = Math.max(playerY - 5, world.getMinHeight()); // 5 blocks below the player
        double maxY = Math.min(playerY + 5, world.getMaxHeight()); // 5 blocks above the player
    
        // Check if the player is looking at the border
        if (isLookingAtBorder(player, minX, maxX, minZ, maxZ)) {
            // Spawn particles along the X-axis (vertical line at minZ and maxZ)
            for (double y = minY; y <= maxY; y++) {
                world.spawnParticle(particleType, minX, y, minZ, 10);
                world.spawnParticle(particleType, maxX, y, minZ, 10);
                world.spawnParticle(particleType, minX, y, maxZ, 10);
                world.spawnParticle(particleType, maxX, y, maxZ, 10);
            }
    
            // Spawn particles along the Z-axis (vertical line at minX and maxX)
            for (double y = minY; y <= maxY; y++) {
                for (double z = minZ; z <= maxZ; z++) {
                    world.spawnParticle(particleType, minX, y, z, 10);
                    world.spawnParticle(particleType, maxX, y, z, 10);
                }
            }
        }
    }
    
    private boolean isLookingAtBorder(Player player, double minX, double maxX, double minZ, double maxZ) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();
    
        // Check if the player's view direction intersects with the border
        double px = eyeLocation.getX();
        double pz = eyeLocation.getZ();
    
        // Check if the player is looking toward the X or Z boundaries
        return (direction.getX() > 0 && px < maxX) || (direction.getX() < 0 && px > minX) ||
               (direction.getZ() > 0 && pz < maxZ) || (direction.getZ() < 0 && pz > minZ);
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        // Check if the entity is a player
        if (event.getEntity() instanceof Player player) {
            // Check if the damage cause is void
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                handleVoidDamage(player);
            }
        }
    }

    private void handleVoidDamage(Player player) {
        // Cancel the damage
        player.setFallDistance(0); // Reset fall distance to prevent fall damage
        player.sendMessage("You fell into the void!");

// Get the player's island location
UUID playerId = player.getUniqueId();
Location handleLocation = islandData.getIslandLocation(playerId);
if (handleLocation == null) {
    // No island location found, teleport to world spawn
    player.teleport(player.getWorld().getSpawnLocation());
    player.sendMessage("Teleporting you to the world spawn.");
    return;
}
// Check if the player has a border set
Location safeLocation = findSafeLocation(player, handleLocation, config.getInt("settings.border-size"));
if (safeLocation != null) {
    // Teleport the player to a safe location within the border
    player.teleport(safeLocation);
    player.sendMessage("Teleporting you back to your island.");
} else {
    // No safe location found, teleport to world spawn
    player.teleport(player.getWorld().getSpawnLocation());
    player.sendMessage("Teleporting you to the world spawn.");
}
    }

    private Location findSafeLocation(Player player, Location center, int size) {
        World world = center.getWorld();
        if (world == null) return null;
    
        double PushSize = size + 0.5;
        double minX = center.getX() - PushSize;
        double maxX = center.getX() + PushSize;
        double minZ = center.getZ() - PushSize;
        double maxZ = center.getZ() + PushSize;
    
        // First, try to find a safe location at the player's current Y level
        int playerY = player.getLocation().getBlockY();
        for (double x = minX; x <= maxX; x++) {
            for (double z = minZ; z <= maxZ; z++) {
                Location loc = new Location(world, x, playerY, z);
                if (isSafeLocation(loc)) {
                    return loc;
                }
            }
        }
    
        // If no safe location is found at the player's current Y level, fall back to the full Y-level search
        for (double x = minX; x <= maxX; x++) {
            for (double z = minZ; z <= maxZ; z++) {
                for (int y = world.getMinHeight(); y <= world.getMaxHeight(); y++) {
                    Location loc = new Location(world, x, y, z);
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
    public boolean loadSchematic(Location markerLocation) {
        // Logic to load the schematic at the given marker location
        // This method should return true if the schematic is successfully loaded, false otherwise
        return schematicLoader.loadSchematic(markerLocation);
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
                player.teleport(teleportLocation);
                player.sendMessage(config.getString("settings.messages.island-created"));
            } else {
                player.sendMessage(config.getString("settings.messages.schematic-failed"));
            }

            return true;
        }

        private int getRandomDistance(int min, int max) {
            return min + (int) (Math.random() * (max - min + 1));
        }
    }

    private class BorderCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }
    
            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();
            if (!player.hasPermission("skiblock.admin")) {
                player.sendMessage("You do not have permission to use this command!");
                return true;
            }
        
            if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
                playerBorders.remove(playerId);
                islandData.setIslandLocation(playerId, null);
                player.sendMessage("Your border has been removed. You can now move freely.");
                return true;
            }
        
            if (args.length < 1 || args.length > 3) {
                player.sendMessage("Usage: /sborder <size> [x] [z] or /sborder remove");
                return true;
            }
        
            try {
                int size = Integer.parseInt(args[0]);
                if (size <= 0) {
                    player.sendMessage("Border size must be greater than 0!");
                    return true;
                }
        
                Location center;
                if (args.length == 3) {
                    double x = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    center = new Location(player.getWorld(), x, player.getLocation().getY(), z);
                } else {
                    center = player.getLocation();
                }
        
                playerBorders.put(playerId, new Border(center, size));
                islandData.setIslandLocation(playerId, center);
                player.sendMessage("Your border has been set! Size: " + size + ", Center: " + center.getBlockX() + ", " + center.getBlockZ());
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid number format! Usage: /sborder <size> [x] [z]");
            }
        
            return true;
        }
    }

    private static class Border {
        private final Location center;
        private final int size;

        public Border(Location center, int size) {
            this.center = center;
            this.size = size;
        }
    }
}
