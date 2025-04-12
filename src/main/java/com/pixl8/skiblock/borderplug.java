package com.etdevd.borderplug;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

public class BorderPlugin extends JavaPlugin {

    private final HashMap<UUID, Border> playerBorders = new HashMap<>(); 

    @Override
    public void onEnable() {
        getLogger().info("BorderPlugin has been enabled!");

        
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayersNearBorders();
            }
        }.runTaskTimer(this, 0L, 10L); 
    }

    @Override
    public void onDisable() {
        getLogger().info("BorderPlugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sborder")) {
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

            if (args.length < 1 || args.length > 3) {
                player.sendMessage("Usage: /sborder <size> [x] [z]");
                return true;
            }

            try {
                int size = Integer.parseInt(args[0]);
                if (size <= 0) {
                    player.sendMessage("Border size must be greater than 0!");
                    return true;
                }

                Location center = player.getLocation(); 
                if (args.length == 3) {
                    double x = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    center = new Location(player.getWorld(), x, center.getY(), z);
                }

                playerBorders.put(playerId, new Border(center, size));
                player.sendMessage("Your border has been set! Size: " + size + ", Center: " + center.getBlockX() + ", " + center.getBlockZ());
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid number format! Usage: /sborder <size> [x] [z]");
            }

            return true;
        }

        return false;
    }

    private void checkPlayersNearBorders() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Border border = playerBorders.computeIfAbsent(playerId, id -> new Border(player.getLocation(), 100)); 

            Location playerLoc = player.getLocation();
            double px = playerLoc.getX();
            double pz = playerLoc.getZ();

            int halfSize = border.size / 2;
            int minX = border.center.getBlockX() - halfSize;
            int maxX = border.center.getBlockX() + halfSize;
            int minZ = border.center.getBlockZ() - halfSize;
            int maxZ = border.center.getBlockZ() + halfSize;

            spawnParticleLine(playerLoc, minX, maxX, minZ, maxZ);

            if (px < minX || px > maxX || pz < minZ || pz > maxZ) {
                Location safeLocation = findSafeLocation(player, border.center, border.size);
                if (safeLocation != null) {
                    player.teleport(safeLocation);
                    player.sendMessage("You were teleported back inside your border!");
                }
            }
        }
    }

    private void spawnParticleLine(Location playerLoc, int minX, int maxX, int minZ, int maxZ) {
        World world = playerLoc.getWorld();
        if (world == null) return;

        for (int x = minX; x <= maxX; x++) {
            world.spawnParticle(Particle.REDSTONE, x, playerLoc.getY(), minZ, 1);
            world.spawnParticle(Particle.REDSTONE, x, playerLoc.getY(), maxZ, 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            world.spawnParticle(Particle.REDSTONE, minX, playerLoc.getY(), z, 1);
            world.spawnParticle(Particle.REDSTONE, maxX, playerLoc.getY(), z, 1);
        }
    }

    /**
     * Finds a safe location near the border center to teleport the player.
     *
     * @param player The player to teleport.
     * @param center The center of the border.
     * @param size   The size of the border.
     * @return A safe location one is found.
     */
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

    /**
     * Checks if a location is safe for a space to teleport a player.
     *
     * @param loc The location to check.
     * @return True if the location is safe, false otherwise.
     */
    private boolean isSafeLocation(Location loc) {
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
