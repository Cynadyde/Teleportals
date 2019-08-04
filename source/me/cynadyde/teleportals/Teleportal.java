package me.cynadyde.teleportals;

import org.apache.commons.lang.ArrayUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.EndGateway;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class Teleportal {

    private TeleportalsPlugin plugin;
    private Block anchor;

    /**
     * Get a teleportal if the given block is part of a teleportal structure.
     */
    public static @Nullable Teleportal getFromStruct(@Nullable Block block, @NotNull TeleportalsPlugin plgn) {

//        TeleportalsPlugin plgn = (TeleportalsPlugin) Objects.requireNonNull(
//                Bukkit.getPluginManager().getPlugin("Teleportals"),
//                "Could not get 'Teleportals' from Bukkit's plugin manager."
//        );

        if (block == null) {
            return null;
        }

        for (BlockFace rel : new BlockFace[] {BlockFace.SELF, BlockFace.UP, BlockFace.DOWN}) {

            Teleportal teleportal = new Teleportal(plgn, block.getRelative(rel));

            if (teleportal.isStructOk()) {
                return teleportal;
            }
        }
        return null;
    }

    /**
     * Represent the given block as a teleportal.
     */
    private Teleportal(@NotNull TeleportalsPlugin pluginRef, @NotNull Block structAnchor) {

        plugin = pluginRef;
        anchor = structAnchor;
    }

    /**
     * Check if the teleportal's structure is what it should be.
     */
    public boolean isStructOk() {

        return (anchor.getType() == Material.ENDER_CHEST || anchor.getType() == Material.END_GATEWAY)
                && anchor.getRelative(BlockFace.DOWN).getType() == Material.OBSIDIAN
                && anchor.getRelative(BlockFace.UP).getType() == Material.OBSIDIAN;
    }

    /**
     * Get the anchor block of this teleportal's structure.
     */
    public @NotNull Block getAnchor() {

        return anchor;
    }

    /**
     * Get the direction this teleportal is facing.
     */
    public @NotNull BlockFace getFacing() {

        BlockFace result = null;

        if (anchor.getBlockData() instanceof Directional) {
            result = ((Directional) anchor.getBlockData()).getFacing();
        }
        else {
            ArmorStand marker = Utils.getMarker(anchor, plugin.teleportalKey.toString());

            if (marker != null) {
                result = marker.getFacing();
            }
        }

        if (result == null || !ArrayUtils.contains(Utils.FACES, result)) {
            result = BlockFace.NORTH;
        }
        return result;
    }

    /**
     * Get a gateway prism if this teleportal contains one.
     */
    public @Nullable ItemStack getGatewayPrism() {

        ArmorStand marker = Utils.getMarker(anchor, plugin.teleportalKey.toString());

        if (marker != null) {
            ItemStack item = marker.getItemInHand();

            if (Utils.hasLoreTag(item, plugin.gatewayPrismKey.toString())) {
                return item;
            }
        }
        return null;
    }

    /**
     * Power the given gateway key with the teleportal.
     */
    public void linkGatewayPrism(ItemStack gatewayPrism) {

        if (Utils.hasLoreTag(gatewayPrism, plugin.gatewayPrismKey.toString())) {
            Utils.setLoreData(gatewayPrism, Utils.format("&6link"), Utils.blockToKey(anchor));
        } // TODO add &k to link key
    }

    /**
     * Turn on the teleportal.
     */
    public void activate() {

        Location loc = block.getLocation();
        String blockKey = Utils.blockToKey(block);
        String subspaceKey = Utils.enchantsToKey(Utils.getEnchants(gatewayPrism));

        List<String> subspaceLinks = getSubspaces().getStringList(subspaceKey);

        // if this block is not already linked to the given subspace...
        if (!subspaceLinks.contains(blockKey)) {

            // get the block's facing direction...
            BlockFace facing = (block.getBlockData() instanceof Directional) ?
                    ((Directional) block.getBlockData()).getFacing() : BlockFace.NORTH;

            // create a marker for the teleportal...
            ArmorStand marker = Utils.createMarker(block, facing, teleportalKey.toString());
            marker.setItemInHand(gatewayPrism);

            // set block to an end gateway...
            block.setType(Material.END_GATEWAY);

            // link the teleportal to that subspace...
            subspaceLinks.add(blockKey);
            getSubspaces().set(subspaceKey, subspaceLinks);

            getLogger().info(String.format("Created Teleportal(%s) at %s(%d, %d, %d)!",
                    subspaceKey, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

            // spawn particle effects and play sfx...
            Location effectsLoc = loc.clone().add(0.5, 0.5, 0.5);
            block.getWorld().spawnParticle(Particle.END_ROD, effectsLoc, 200, 0.1, 0.1, 0.1, 0.10);
            block.getWorld().spawnParticle(Particle.DRAGON_BREATH, effectsLoc, 100, 0.25, 0.25, 0.25, 0.075);
            block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, effectsLoc, 50, 0.1, 0.1, 0.1, 0.025);
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f); // TO DO random pitches
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.5f, 1.5f);  // TO DO ambient sound?
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.5f, 0.5f);
        }
    }

    /**
     * Turn off the teleportal.
     */
    public void deactivate() {

        Location loc = block.getLocation();
        String blockKey = Utils.blockToKey(block);

        BlockFace facing = null;
        ItemStack gatewayPrism = null;

        // get the teleportal's marker...
        ArmorStand marker = Utils.getMarker(block, teleportalKey.toString());

        if (marker != null) {
            facing = marker.getFacing();
            gatewayPrism = marker.getItemInHand();

            marker.remove();
        }

        // set block to an ender chest...
        if (block.getType() == Material.END_GATEWAY) {

            block.setType(Material.ENDER_CHEST);

            if (facing != null) {

                // set the rotation of the ender chest...
                if (block.getBlockData() instanceof Directional) {

                    Directional blockData = (Directional) block.getBlockData();
                    blockData.setFacing(facing);

                    block.setBlockData(blockData);
                }
            }
        }

        if (gatewayPrism != null) {

            // drop the teleportal's gateway prism...
            block.getWorld().dropItemNaturally(loc, gatewayPrism);

            String subspaceKey = Utils.enchantsToKey(Utils.getEnchants(gatewayPrism));
            List<String> subspaceLinks = getSubspaces().getStringList(subspaceKey);

            // unlink the teleportal from that subspace...
            if (subspaceLinks.remove(blockKey)) {
                getSubspaces().set(subspaceKey, subspaceLinks);
            }

            getLogger().info(String.format("Removed teleportal(%s) at %s(%d, %d, %d)!",
                    subspaceKey, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        }

        // spawn particle effects and play sfx...
        Location effectsLoc = loc.clone().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, effectsLoc, 5, 0.1, 0.1, 0.05);
        block.getWorld().spawnParticle(Particle.DRAGON_BREATH, effectsLoc, 100, 0.25, 0.25, 0.25, 0.075);
        block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, effectsLoc, 50, 0.1, 0.1, 0.1, 0.025);
        block.getWorld().playSound(effectsLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.25f);
    }

    /**
     * Teleport the given entity through the teleportal.
     */
    public void teleport(Entity entity, BlockFace face) {

        Location loc = block.getLocation();
        String blockKey = Utils.blockToKey(block);
        //String subspaceKey = Utils.enchantsToKey(Utils.getEnchants(gatewayPrism));

        for (String subspaceKey : getSubspaces().getKeys(false)) {

            List<String> subspaceLinks = getSubspaces().getStringList(subspaceKey);
            if (subspaceLinks.contains(blockKey)) {

                getLogger().info(String.format("Using teleportal(%s) at %s(%d, %d, %d)!",
                        subspaceKey, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

                // get the next exit link for this teleportal...
                Block exitLink = null;
                boolean alteredList = false;

                int index;
                String exitKey;

                while (!subspaceLinks.isEmpty()) {

                    index = (subspaceLinks.indexOf(blockKey) + 1) % subspaceLinks.size();
                    exitKey = subspaceLinks.get(index);
                    exitLink = Utils.keyToBlock(exitKey);

                    if (exitLink != null && exitLink.getType() == Material.END_GATEWAY) {
                        break;
                    }
                    else {
                        alteredList = alteredList || subspaceLinks.remove(exitKey);
                    }
                }
                if (alteredList) {
                    getSubspaces().set(subspaceKey, subspaceLinks);
                }
                if (exitLink == null) {
                    return;
                }

                Location tpLoc = exitLink.getLocation().add(0.5, -0.5, 0.5);

                // choose the appropriate side of the teleportal to emerge from...
                BlockFace thisDir = getTeleportalFacing(block);
                BlockFace exitDir = getTeleportalFacing(exitLink);
                int dirOffset = (face.ordinal() - thisDir.ordinal());

                tpLoc.add(Utils.FACES[Math.floorMod(exitDir.ordinal() + dirOffset, Utils.FACES.length)].getDirection());

                // choose the appropriate yaw and pitch to emerge with...
                float thisYaw = Utils.blockFaceToYaw(thisDir) + 180;
                float exitYaw = Utils.blockFaceToYaw(exitDir);

                tpLoc.setYaw(exitYaw - (thisYaw - entity.getLocation().getYaw()));
                tpLoc.setPitch(entity.getLocation().getPitch());

                // play sound effects...
                float randomPitch = 0.75f + (Utils.RNG.nextFloat() % 0.75f);
                block.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.25f, randomPitch);

                // teleport the entity...
                entity.teleport(tpLoc, PlayerTeleportEvent.TeleportCause.END_GATEWAY);

                // disable the end gateway beam...
                if (block.getState() instanceof EndGateway) {
                    EndGateway endGateway = (EndGateway) block.getState();
                    endGateway.setAge(250L);
                    endGateway.update(true);
                }
            }
        }
    }
}
