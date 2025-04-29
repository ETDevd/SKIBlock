package com.pixl8.skiblock;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class BorderControl implements Listener {
    private final HashMap<UUID, Border> playerBorders = new HashMap<>();
    private final int defaultBorderSize;
    private final SKIBlock plugin;
 
    public BorderControl(SKIBlock plugin, int defaultBorderSize) {
        this.plugin = plugin;
        this.defaultBorderSize = defaultBorderSize;
    }
    /**
     * Sets a border for a player.
     *
     * @param player The player to set the border for.
     * @param center The center of the border.
     * @param size   The size of the border.
     */
    public void setBorder(Player player, Location center, int size) {
        if (size <= 0) {
            size = defaultBorderSize; // Use defaultBorderSize if the provided size is invalid
        }
        Location flooredCenter = new Location(center.getWorld(), Math.floor(center.getX()), Math.floor(center.getY()), Math.floor(center.getZ()));
        playerBorders.put(player.getUniqueId(), new Border(flooredCenter, size));
        player.sendMessage("§aBorder set! Size: " + size + ", Center: " + center.getBlockX() + ", " + center.getBlockZ());
    }

    /**
     * Removes a player's border.
     *
     * @param player The player whose border should be removed.
     */
    public void removeBorder(Player player) {
        playerBorders.remove(player.getUniqueId());
        player.sendMessage("§aYour border has been removed!");
    }

    public void BorderCheck() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location islandLocation = plugin.getIslandData().getIslandLocation(player.getUniqueId());

            // Skip if the player does not have an island or is not in the same world as their island
            if (islandLocation == null || !player.getWorld().equals(islandLocation.getWorld())) {
                continue;
            }

            Border border = playerBorders.get(player.getUniqueId());

            if (border == null) continue;

            Location playerLoc = player.getLocation();
            double playerX = playerLoc.getX();
            double playerZ = playerLoc.getZ();

            double minX = border.center.getX() - border.size;
            double maxX = border.center.getX() + border.size;
            double minZ = border.center.getZ() - border.size;
            double maxZ = border.center.getZ() + border.size;

            // Check for items outside the border or in the void
            for (Item item : player.getWorld().getEntitiesByClass(Item.class)) {
                Location itemLocation = item.getLocation();
                World world = itemLocation.getWorld();
                if (world == null) continue;

                // Check if the item is in the void or beyond the border
                double x = itemLocation.getX();
                double z = itemLocation.getZ();

                if (itemLocation.getY() < world.getMinHeight() || x < minX || x > maxX || z < minZ || z > maxZ) {
                    // Find the nearest player
                    Player nearestPlayer = null;
                    double nearestDistanceSquared = Double.MAX_VALUE;

                    for (Player nearbyPlayer : itemLocation.getWorld().getPlayers()) {
                        double distanceSquared = nearbyPlayer.getLocation().distanceSquared(itemLocation);
                        if (distanceSquared < nearestDistanceSquared) {
                            nearestDistanceSquared = distanceSquared;
                            nearestPlayer = nearbyPlayer;
                        }
                    }

                    if (nearestPlayer != null) {
                        // Teleport the item to the player's head
                        Location playerHeadLocation = nearestPlayer.getLocation().clone().add(0, 1.5, 0); // 1.5 blocks above for the head
                        item.teleport(playerHeadLocation);
                    }
                }
            }

            if (playerLoc.getY() < player.getWorld().getMinHeight() || playerX < minX || playerX > maxX || playerZ < minZ || playerZ > maxZ) {
                // Teleport the player to the center of their border
                player.teleport(new Location(border.center.getWorld(),
                        border.center.getX(),
                        border.center.getY()+0.35,
                        border.center.getZ(),
                        player.getLocation().getYaw(),
                        player.getLocation().getPitch()));

                // Notify the player
                player.sendMessage("§7You were teleported back to your island!");
            }

            // Check if the player is outside the border
            boolean isOutside = playerX < minX || playerX > maxX || playerZ < minZ || playerZ > maxZ;
            if (isOutside) {
                Location safeLoc = new Location(player.getWorld(), Math.max(minX, Math.min(maxX, playerX)), playerLoc.getY(), Math.max(minZ, Math.min(maxZ, playerZ)));

                // Find a safe location at the calculated position
                safeLoc = findSafeLoc(player, safeLoc, 1);

                player.teleport(new Location(safeLoc.getWorld(),
                        Math.floor(safeLoc.getX()),
                        Math.floor(safeLoc.getY()),
                        Math.floor(safeLoc.getZ()),
                        playerLoc.getYaw(),
                        playerLoc.getPitch()));
                player.sendMessage("§7You were teleported back inside your border!");
            }

            // Check if the player is near the border (within 3.5 blocks)
            if (Math.abs(playerX - minX) <= 5 || Math.abs(playerX - maxX) <= 5 || Math.abs(playerZ - minZ) <= 5 || Math.abs(playerZ - maxZ) <= 5) {
                // Spawn particles at the border's edge
                World world = player.getWorld();
                for (int angle = 0; angle < 360; angle += 45) {
                    double radians = Math.toRadians(angle);
                    double offsetX = Math.cos(radians) * 10; // 10 blocks away
                    double offsetZ = Math.sin(radians) * 10;

                    // Determine the closest edge
                    double closestX = (playerX < minX) ? minX : (playerX > maxX) ? maxX : playerX;
                    double closestZ = (playerZ < minZ) ? minZ : (playerZ > maxZ) ? maxZ : playerZ;

                    // Spawn particles above, below, and at the edge
                    Location particleLoc = new Location(world, closestX + offsetX, playerLoc.getY() + 10, closestZ + offsetZ);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, particleLoc, 5, 0.5, 0.5, 0.5, 0.1,
                            new Particle.DustTransition(Color.RED, Color.RED, 1.0f));

                    particleLoc.setY(playerLoc.getY() - 10); // Below
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, particleLoc, 5, 0.5, 0.5, 0.5, 0.1,
                            new Particle.DustTransition(Color.RED, Color.RED, 1.0f));

                    particleLoc.setY(playerLoc.getY()); // At the edge
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, particleLoc, 5, 0.5, 0.5, 0.5, 0.1,
                            new Particle.DustTransition(Color.RED, Color.RED, 1.0f));
                }
            }
        }
    }

    private Location findSafeLoc(Player player, Location center, int size) {
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
                    Location loc = new Location(world, x, y, z);
                    if (isSafeLoc(loc)) {
                        return loc;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeLoc(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        Location below = loc.clone().add(0, -1, 0);
        Location above = loc.clone().add(0, 1, 0);

        return below.getBlock().getType().isSolid()
                && loc.getBlock().getType() == Material.AIR
                && above.getBlock().getType() == Material.AIR;
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

