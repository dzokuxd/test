package pl.gildie.service;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class SugarcaneTask extends BukkitRunnable {

    private final Random random = new Random();

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            for (int i = 0; i < 5; i++) {
                trySpawn(world);
            }
        }
    }

    private void trySpawn(World world) {
        Chunk[] chunks = world.getLoadedChunks();
        if (chunks.length == 0) return;

        Chunk chunk = chunks[random.nextInt(chunks.length)];
        int x = random.nextInt(16);
        int z = random.nextInt(16);

        int y = world.getHighestBlockYAt(
                chunk.getX() * 16 + x,
                chunk.getZ() * 16 + z
        );

        if (y <= 0) return;

        Block surface = chunk.getBlock(x, y - 1, z);
        Block air = chunk.getBlock(x, y, z);

        if (air.getType() != Material.AIR) return;

        if (surface.getType() != Material.SAND &&
                surface.getType() != Material.RED_SAND) {
            return;
        }

        if (!hasWater(surface)) return;

        int height = 1 + random.nextInt(3);
        for (int h = 0; h < height; h++) {
            Block b = air.getRelative(BlockFace.UP, h);
            if (b.getType() == Material.AIR) {
                b.setType(Material.SUGAR_CANE);
            } else {
                break;
            }
        }
    }

    private boolean hasWater(Block block) {
        return block.getRelative(BlockFace.NORTH).getType() == Material.WATER ||
                block.getRelative(BlockFace.SOUTH).getType() == Material.WATER ||
                block.getRelative(BlockFace.EAST).getType() == Material.WATER ||
                block.getRelative(BlockFace.WEST).getType() == Material.WATER;
    }
}