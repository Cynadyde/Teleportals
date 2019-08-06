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

/**
 * Represents a teleportal structure in the world.
 */
@SuppressWarnings("WeakerAccess")
public class Teleportal {

    private TeleportalsPlugin plugin;
    private Block anchor;

    /**
     * Get a teleportal if the given block is part of a teleportal structure.
     */
    public static @Nullable Teleportal getFromStruct(@NotNull TeleportalsPlugin plugin, @Nullable Block block) {

        if (block == null) {
            return null;
        }

        for (BlockFace rel : new BlockFace[] {BlockFace.SELF, BlockFace.UP, BlockFace.DOWN}) {

            Teleportal teleportal = new Teleportal(plugin, block.getRelative(rel));

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
     * Check if the teleportal is properly activated or not.
     */
    @SuppressWarnings("unused")
    public boolean isActivated() {
        return anchor.getType() == Material.END_GATEWAY && getGatewayPrism() != null;
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

        // if the given item actually is a gateway prism...
        if (Utils.hasLoreTag(gatewayPrism, plugin.gatewayPrismKey.toString())) {

            // set the link on the gateway prism...
            //noinspection SpellCheckingInspection
            Utils.setLoreData(gatewayPrism, Utils.format("&6&klink"), Utils.blockToKey(anchor));

            // spawn particle effects and play sfx...
            Location loc = anchor.getLocation().add(0.5, 0.5, 0.5);
            anchor.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 100, 0.25, 0.25, 0.25, 0.075);
            anchor.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);
        }
    }

    /**
     * Turn on the teleportal.
     * Only works if it is currently off.
     * The gateway prism must be linked to another teleportal.
     */
    public boolean activate(ItemStack gatewayPrism) {

        boolean success = false;
        Location loc = anchor.getLocation().add(0.5, 0.5, 0.5);

        activating: {

            // make sure the given item is a linked gateway prism...
            if (!Utils.hasLoreTag(gatewayPrism, plugin.gatewayPrismKey.toString())) {
                break activating;
            }
            if (!Utils.hasLoreData(gatewayPrism, "link")) {
                break activating;
            }

            // make this the gateway prism is linked to a different teleportal...
            if (Utils.getLoreData(gatewayPrism, "link").equalsIgnoreCase(Utils.blockToKey(anchor))) {
                break activating;
            }

            // make sure this teleportal isn't already activated...
            if (anchor.getType() == Material.END_GATEWAY) {
                break activating;
            }
            if (Utils.getMarker(anchor, plugin.teleportalKey.toString()) != null) {
                break activating;
            }

            // create a new marker for the teleportal...
            Utils.createMarker(anchor, getFacing(), plugin.teleportalKey.toString(), gatewayPrism);

            // set the anchor block to an end gateway...
            anchor.setType(Material.END_GATEWAY);

            // disable the end gateway beam...
            if (anchor.getState() instanceof EndGateway) {
                EndGateway endGateway = (EndGateway) anchor.getState();
                endGateway.setAge(Long.MIN_VALUE); // approx. 292 million years
                endGateway.update(true);
            }

            // spawn particle effects and play sfx...
            anchor.getWorld().spawnParticle(Particle.END_ROD, loc, 200, 0.1, 0.1, 0.1, 0.10);
            anchor.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 100, 0.25, 0.25, 0.25, 0.075);
            anchor.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.1, 0.1, 0.1, 0.025);
            anchor.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);
            anchor.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.5f, 1.5f);
            anchor.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.5f, 0.5f);

            success = true;
        }
        if (!success) {

            // spawn particle effects and play sfx upon failure...
            float randomPitch = 0.90f + (Utils.RNG.nextFloat() * 0.20f);
            anchor.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.1, 0.1, 0.1, 0.025);
            anchor.getWorld().playSound(loc, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, randomPitch);
        }
        return success;
    }

    /**
     * Turn off the teleportal.
     * Works weather it is on, off, or in a broken state.
     */
    public void deactivate() {

        Location loc = anchor.getLocation().add(0.5, 0.5, 0.5);
        BlockFace facing = getFacing();
        ItemStack gatewayPrism = getGatewayPrism();

        // remove the teleportal's marker...
        Utils.removeMarker(anchor, plugin.teleportalKey.toString());

        // spawn particle effects and play sfx if the portal is being changed...
        if (anchor.getType() != Material.ENDER_CHEST) {

            anchor.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 5, 0.1, 0.1, 0.05);
            anchor.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 100, 0.25, 0.25, 0.25, 0.075);
            anchor.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.1, 0.1, 0.1, 0.025);
            anchor.getWorld().playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.25f);
        }

        // set block to an ender chest...
        anchor.setType(Material.ENDER_CHEST);

        // set the rotation of the ender chest...
        if (anchor.getBlockData() instanceof Directional) {

            Directional blockData = (Directional) anchor.getBlockData();
            blockData.setFacing(facing);
            anchor.setBlockData(blockData);
        }

        // drop the teleportal's gateway prism...
        if (gatewayPrism != null) {
            anchor.getWorld().dropItemNaturally(loc, gatewayPrism);
        }
    }

    /**
     * Teleport the given entity through the teleportal.
     */
    public void teleport(Entity entity, BlockFace face) {

        Location loc = anchor.getLocation();
        ItemStack gatewayPrism = getGatewayPrism();

        // get the exit link for this teleportal...
        String exitKey = Utils.getLoreData(gatewayPrism, "link");
        Teleportal exit = Teleportal.getFromStruct(plugin, Utils.keyToBlock(exitKey));
        if (exit == null) {
            return;
        }
        Location tpLoc = exit.getAnchor().getLocation().add(0.5, -0.5, 0.5);

        // choose the appropriate side of the teleportal to emerge from...
        BlockFace thisFace = this.getFacing();
        BlockFace exitFace = exit.getFacing();

        int faceOffset = (face.ordinal() - thisFace.ordinal());
        tpLoc.add(Utils.FACES[Math.floorMod(exitFace.ordinal() + faceOffset, Utils.FACES.length)].getDirection());

        // choose the appropriate yaw and pitch to emerge with...
        float thisYaw = Utils.blockFaceToYaw(thisFace);
        float exitYaw = Utils.blockFaceToYaw(exitFace) - 180;

        tpLoc.setYaw(exitYaw - (thisYaw - entity.getLocation().getYaw()));
        tpLoc.setPitch(entity.getLocation().getPitch());

        // play sound effects...
        float randomPitch = 0.75f + (Utils.RNG.nextFloat() % 0.75f);
        anchor.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.25f, randomPitch);

        // teleport the entity...
        entity.teleport(tpLoc, PlayerTeleportEvent.TeleportCause.END_GATEWAY);
    }
}
