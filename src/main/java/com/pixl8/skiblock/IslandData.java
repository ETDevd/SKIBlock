package com.pixl8.skiblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandData {
    private final File dataFile;
    private FileConfiguration config;
    private final SKIBlock plugin;

    public IslandData(File dataFolder, SKIBlock plugin) {
        this.plugin = plugin;
        this.dataFile = new File(dataFolder, "island_data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not create island data file", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void loadIslands() {
        config = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveIslands() {
        try {
            config.save(dataFile);
        } catch (IOException e) {
            throw new RuntimeException("Could not save island data", e);
        }
    }

    public Map<UUID, Location> getIslandLocations() {
        Map<UUID, Location> islands = new HashMap<>();
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Location loc = deserializeLocation(config.getString(key));
                islands.put(uuid, loc);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load island data for UUID: " + key);
            }
        }
        return islands;
    }

    public void setIslandLocation(UUID playerUUID, Location location) {
        config.set(playerUUID.toString(), serializeLocation(location));
        saveIslands();
    }

    public Location getIslandLocation(UUID playerUUID) {
        String locString = config.getString(playerUUID.toString());
        return locString != null ? deserializeLocation(locString) : null;
    }

    private String serializeLocation(Location location) {
        return String.format("%s,%f,%f,%f,%f,%f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    private Location deserializeLocation(String locString) {
        String[] parts = locString.split(",");
        if (parts.length != 6) return null;
        
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        
        return new Location(
                world,
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
        );
    }
}
