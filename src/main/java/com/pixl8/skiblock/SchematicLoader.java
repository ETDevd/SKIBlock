package com.pixl8.skiblock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public class SchematicLoader {
    private final JavaPlugin plugin;
    private static final String SCHEMATIC_FILE = "island1.yaml";

    public SchematicLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads and builds a schematic at the specified location
     * @param location The location to build the schematic
     * @return true if successful, false otherwise
     */
    public boolean loadSchematic(Location location) {
        try {
            // First check if there's a custom schematic file in the plugin directory
            File customSchematic = new File(plugin.getDataFolder(), SCHEMATIC_FILE);
            if (customSchematic.exists()) {
                // Use the custom schematic file
                Yaml yaml = new Yaml();
                Map<String, Object> schematicData = yaml.load(new FileReader(customSchematic));
                
                if (schematicData == null) {
                    plugin.getLogger().warning("Invalid YAML format in custom schematic file");
                    return false;
                }

                // Get the blocks data
                Map<String, String> blocks = (Map<String, String>) schematicData.get("blocks");
                if (blocks == null) {
                    plugin.getLogger().warning("No blocks found in custom schematic");
                    return false;
                }

                // Place blocks according to schematic
                for (Map.Entry<String, String> entry : blocks.entrySet()) {
                    String[] coords = entry.getKey().split(",");
                    if (coords.length != 3) {
                        continue;
                    }

                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);

                    Location blockLoc = new Location(location.getWorld(),
                            location.getBlockX() + x,
                            location.getBlockY() + y,
                            location.getBlockZ() + z);

                    Material material = Material.getMaterial(entry.getValue().toUpperCase());
                    if (material != null) {
                        Block block = blockLoc.getBlock();
                        block.setType(material);

                        // If the block is a chest, try to load its contents
                        if (block.getState() instanceof Chest) {
                            Map<String, List<String>> chestContents = (Map<String, List<String>>) schematicData.get("chest_contents");
                            if (chestContents != null) {
                                String coordsKey = x + "," + y + "," + z;
                                if (chestContents.containsKey(coordsKey)) {
                                    loadChestContents(block, chestContents.get(coordsKey));
                                }
                            }
                        }
                    }
                }

                return true;
            }
            
            // If no custom file exists, fall back to the default schematic in the JAR
            InputStream schematicStream = plugin.getResource(SCHEMATIC_FILE);
            if (schematicStream == null) {
                plugin.getLogger().warning("Schematic file not found in resources: " + SCHEMATIC_FILE);
                return false;
            }

            // Read the YAML data from the stream
            Yaml yaml = new Yaml();
            Map<String, Object> schematicData = yaml.load(new InputStreamReader(schematicStream));
            
            if (schematicData == null) {
                plugin.getLogger().warning("Invalid YAML format in default schematic");
                return false;
            }

            // Get the blocks data
            Map<String, String> blocks = (Map<String, String>) schematicData.get("blocks");
            if (blocks == null) {
                plugin.getLogger().warning("No blocks found in default schematic");
                return false;
            }

            // Place blocks according to schematic
            for (Map.Entry<String, String> entry : blocks.entrySet()) {
                String[] coords = entry.getKey().split(",");
                if (coords.length != 3) {
                    continue;
                }

                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);

                Location blockLoc = new Location(location.getWorld(),
                        location.getBlockX() + x,
                        location.getBlockY() + y,
                        location.getBlockZ() + z);

                Material material = Material.getMaterial(entry.getValue().toUpperCase());
                if (material != null) {
                    Block block = blockLoc.getBlock();
                    block.setType(material);

                    // If the block is a chest, try to load its contents
                    if (block.getState() instanceof Chest) {
                        Map<String, List<String>> chestContents = (Map<String, List<String>>) schematicData.get("chest_contents");
                        if (chestContents != null) {
                            String coordsKey = x + "," + y + "," + z;
                            if (chestContents.containsKey(coordsKey)) {
                                loadChestContents(block, chestContents.get(coordsKey));
                            }
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading schematic: " + e.getMessage());
            return false;
        }
    }

    private void loadChestContents(Block block, List<String> contents) {
        if (!(block.getState() instanceof Chest chest)) return;
        
        for (String content : contents) {
            String[] parts = content.split(":");
            if (parts.length != 2) continue;

            Material material = Material.getMaterial(parts[0]);
            if (material == null) continue;

            int amount = Integer.parseInt(parts[1]);
            ItemStack item = new ItemStack(material, amount);
            chest.getBlockInventory().addItem(item);
        }
    }
}
