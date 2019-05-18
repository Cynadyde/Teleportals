package me.cynadyde.teleportals;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 *
 */
@SuppressWarnings("WeakerAccess")
public class Teleportal {

    /**
     *
     */
    public static Teleportal getAt(Block block) {

        if (block == null) {
            return null;
        }
        switch (block.getType()) {

            case END_GATEWAY:
            case ENDER_CHEST:
                if (block.getRelative(BlockFace.DOWN).getType().equals(Material.OBSIDIAN)
                        && block.getRelative(BlockFace.UP).getType().equals(Material.OBSIDIAN)) {
                    return new Teleportal(block);
                }
                break;

            case OBSIDIAN:
                for (BlockFace dir : new BlockFace[] {BlockFace.UP, BlockFace.DOWN}) {
                    Block relBlock = block.getRelative(dir);

                    switch (relBlock.getType()) {

                        case END_GATEWAY:
                        case ENDER_CHEST:
                            if (relBlock.getRelative(BlockFace.DOWN).getType().equals(Material.OBSIDIAN)
                                    && relBlock.getRelative(BlockFace.UP).getType().equals(Material.OBSIDIAN)) {
                                return new Teleportal(relBlock);
                            }
                            break;
                    }
                }
                break;
        }
        return null;
    }

    private Block anchor;

    private Teleportal(Block block) {

        this.anchor = block;
    }

    public Block getAnchor() {

        return anchor;
    }

    public boolean isActive() {

        return anchor.getType().equals(Material.END_GATEWAY);
    }

    public void activate(String subspaceLink) {


    }
}
