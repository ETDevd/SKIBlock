package com.pixl8.skiblock;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class VoidGenerator extends ChunkGenerator {
    private static final int SPAWN_HEIGHT = 64;

    @Override
    public ChunkGenerator.ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, ChunkGenerator.BiomeGrid biome) {
        ChunkGenerator.ChunkData chunkData = createChunkData(world);

        // Set all blocks to air for the extended height range
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }

        // Set biome to ocean for the entire chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                biome.setBiome(x, z, org.bukkit.block.Biome.THE_VOID);
            }
        }

        return chunkData;
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0, SPAWN_HEIGHT, 0);
    }

    @Override
    public String toString() {
        return "VoidGenerator";
    }
}
