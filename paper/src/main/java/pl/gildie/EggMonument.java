package pl.gildie;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.EnderCrystal;

public class EggMonument {
    
    public static void generate(Location playerLoc) {
        World world = playerLoc.getWorld();
        int cx = playerLoc.getBlockX();
        int cy = playerLoc.getBlockY();
        int cz = playerLoc.getBlockZ();
        
        // Czyść obszar 5x5, wysokość 7 bloków
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = cy; y <= cy + 6; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.BEDROCK) {
                        b.setType(Material.AIR);
                    }
                }
            }
        }
        
        // Podłoga 5x5 obsidian (y=cy)
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                world.getBlockAt(x, cy, z).setType(Material.OBSIDIAN);
            }
        }
        
        // Filary w rogach - WYSOKOŚĆ 5 bloków (y=cy+1 do cy+5)
        int[][] corners = {{cx-2, cz-2}, {cx-2, cz+2}, {cx+2, cz-2}, {cx+2, cz+2}};
        for (int[] c : corners) {
            for (int y = cy + 1; y <= cy + 5; y++) {
                world.getBlockAt(c[0], y, c[1]).setType(Material.OBSIDIAN);
            }
        }
        
        // Dach - PEŁNY kwadrat 5x5 obsidian (y=cy+5)
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                world.getBlockAt(x, cy + 5, z).setType(Material.OBSIDIAN);
            }
        }
        
        // Blackstone na środku (y=cy+1)
        world.getBlockAt(cx, cy + 1, cz).setType(Material.BLACKSTONE);
        
        // 4 schody odwrócone przy blackstonie
        setStairs(world, cx - 1, cy + 1, cz, org.bukkit.block.BlockFace.EAST);
        setStairs(world, cx + 1, cy + 1, cz, org.bukkit.block.BlockFace.WEST);
        setStairs(world, cx, cy + 1, cz - 1, org.bukkit.block.BlockFace.SOUTH);
        setStairs(world, cx, cy + 1, cz + 1, org.bukkit.block.BlockFace.NORTH);
        
        // End Crystal idealnie na środku
        Location crystalLoc = new Location(world, cx + 0.5, cy + 1.5, cz + 0.5);
        EnderCrystal crystal = world.spawn(crystalLoc, EnderCrystal.class);
        crystal.setShowingBottom(false);
    }
    
    private static void setStairs(World world, int x, int y, int z, org.bukkit.block.BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.POLISHED_BLACKSTONE_STAIRS);
        Stairs stairs = (Stairs) block.getBlockData();
        stairs.setHalf(Bisected.Half.TOP);
        stairs.setFacing(facing);
        block.setBlockData(stairs);
    }
}
