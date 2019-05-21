package me.cynadyde.teleportals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a block location within a world, facing N, S, E, or W.
 */
@SuppressWarnings("WeakerAccess")
public class SubspaceLink {

    private World world;
    private int x;
    private int y;
    private int z;
    private BlockFace dir;

    @Nullable
    public static SubspaceLink from(Location loc, BlockFace dir) {
        if (loc.getWorld() == null) {
            return null;
        }
        return new SubspaceLink(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), dir);
    }

    public SubspaceLink(@NotNull World world, int x, int y, int z, @NotNull BlockFace dir) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dir = dir;
    }

    @NotNull
    public Location getLocation() {
        return new Location(world, x, y, z);
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @NotNull
    public BlockFace getDir() {
        return dir;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SubspaceLink) {
            SubspaceLink other = (SubspaceLink) obj;
            return this.world.equals(other.world)
                    && this.x == other.x
                    && this.y == other.y
                    && this.z == other.z;
        }
        return false;
    }
}
