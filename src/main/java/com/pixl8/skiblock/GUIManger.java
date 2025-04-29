package com.pixl8.skiblock;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GUIManger implements Listener {

    private final SKIBlock plugin;

    public GUIManger(SKIBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a dropper UI with 8 biome blocks and a barrier block in the middle.
     * @return The inventory representing the dropper UI.
     */
    public Inventory createDropperUI() {
        // Create a dropper inventory (3x3 grid)
        Inventory dropperUI = Bukkit.createInventory(null, 9, "Custom Dropper UI");

        // Define materials and their corresponding display names
        Material[] materials = {
            Material.GRASS_BLOCK, Material.MYCELIUM, Material.MUD, Material.SAND, Material.BARRIER,
            Material.DRIPSTONE_BLOCK, Material.POWDER_SNOW, Material.MOSS_BLOCK, Material.ORANGE_TERRACOTTA
        };
        String[] displayNames = {
            "§aThe Woodlands", "§aThe Wetlands", "§aThe Marshlands", "§aThe Fossil Lands", "§cRandom Biome",
            "§aThe Frozen Lands", "§aThe Lush Lands", "§aThe Stony Lands", "§aThe Red Lands"
        };

        // Shared lore for all items
        List<String> lore = List.of("§7Click to select");

        // Create and add items to the inventory
        for (int i = 0; i < materials.length; i++) {
            ItemStack item = new ItemStack(materials[i]);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayNames[i]);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            dropperUI.setItem(i, item);
        }

        return dropperUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory is the custom dropper UI
        if (event.getView().getTitle().equals("Custom Dropper UI")) {
            event.setCancelled(true); // Prevent item movement

            // Check if the clicked item is valid
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
                return;
            }

            // Get the player
            Player player = (Player) event.getWhoClicked();
            UUID playerId = player.getUniqueId();

            // Get the player's island location
            Location islandLocation = plugin.getIslandData().getIslandLocation(playerId);
            if (islandLocation == null) {
                player.sendMessage("§cNo island found! Please create one first.");
                return;
            }

            // Check if the player is already at their island
            if (!isPlayerAtIsland(player, islandLocation)) {
                // Teleport the player to their island
                player.teleport(islandLocation);
                player.sendMessage("§aYou have been teleported to your island!");
                return; // Stop further processing until the player is at their island
            }

            // Get the slot number
            int slot = event.getSlot() + 1;

            // Get the biome from the config
            String biomeName = plugin.getConfig().getString("biome-mapping." + slot);
            if (biomeName == null) {
                player.sendMessage("§cInvalid biome selection!");
                return;
            }

            try {
                Biome biome = Biome.valueOf(biomeName);

                // Change the biome at the island's location
                World world = islandLocation.getWorld();
                if (world != null) {
                    int islandX = islandLocation.getBlockX();
                    int islandZ = islandLocation.getBlockZ();
                    for (int x = islandX - 10; x <= islandX + 10; x++) {
                        for (int z = islandZ - 10; z <= islandZ + 10; z++) {
                            world.setBiome(x, islandLocation.getBlockY(), z, biome);
                        }
                    }
                }

                player.closeInventory();
                player.sendMessage("§aBiome changed to " + biomeName + "!");
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid biome name: " + biomeName);
            }
        }
    }

    /**
     * Checks if the player is at their island.
     * @param player The player to check.
     * @param islandLocation The location of the player's island.
     * @return True if the player is at their island, false otherwise.
     */
    private boolean isPlayerAtIsland(Player player, Location islandLocation) {
        Location playerLocation = player.getLocation();
        return playerLocation.getWorld().equals(islandLocation.getWorld())
                && playerLocation.distanceSquared(islandLocation) <= 100; // 10 blocks radius
    }
}
