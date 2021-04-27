package me.cynadyde.teleportals;

import org.apache.commons.lang.ArrayUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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

import java.util.Objects;

/**
 * A representative of a teleportal structure in the a world.
 */
public class Teleportal {

    final private Block anchor;

    /**
     * Get a teleportal if the given block is part of a teleportal structure, else null.
     */
    public static @Nullable Teleportal getFromStruct(@Nullable Block block) {

        if (block == null) {
            return null;
        }

        for (BlockFace rel : new BlockFace[]{BlockFace.SELF, BlockFace.UP, BlockFace.DOWN}) {

            Teleportal teleportal = new Teleportal(block.getRelative(rel));

            if (teleportal.isStructOk()) {
                return teleportal;
            }
        }
        return null;
    }

    /**
     * Represent the given block as a teleportal.
     */
    private Teleportal(@NotNull Block structAnchor) {
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
     * Get the cardinal direction this teleportal is facing.
     */
    public @NotNull BlockFace getFacing() {

        BlockFace result = null;

        if (anchor.getBlockData() instanceof Directional) {
            result = ((Directional) anchor.getBlockData()).getFacing();
        }
        else {
            ArmorStand marker = Utils.getMarker(anchor, TeleportalsPlugin.getKey("teleportal").toString());

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
     * Get the gateway prism contained in this teleportal if it has one, else null.
     */
    public @Nullable ItemStack getGatewayPrism() {

        ArmorStand marker = Utils.getMarker(anchor, TeleportalsPlugin.getKey("teleportal").toString());

        if (marker != null) {
            ItemStack item = Objects.requireNonNull(marker.getEquipment()).getItemInMainHand();

            if (Utils.hasLoreTag(item, TeleportalsPlugin.getKey("gateway_prism").toString())) {
                return item;
            }
        }
        return null;
    }

    /**
     * Get the name of this teleportal if it has one, else null.
     */
    public @Nullable String getPortalName() {

        ArmorStand marker = Utils.getMarker(getAnchor(), TeleportalsPlugin.getKey("teleportal").toString());
        if (marker != null) {
            return Utils.getDisplayName(Objects.requireNonNull(marker.getEquipment()).getHelmet());
        }
        return null;
    }

    /**
     * Set the name of this teleportal or remove its name if null.
     */
    public void setPortalName(@Nullable String name) {

        ArmorStand marker = Utils.getMarker(getAnchor(), TeleportalsPlugin.getKey("teleportal").toString());
        if (marker != null) {

            ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);
            Utils.setDisplayName(helmet, name);
            Objects.requireNonNull(marker.getEquipment()).setHelmet(helmet);
        }
    }

    /**
     * Link the given gateway prism to this teleportal.
     */
    public void linkGatewayPrism(ItemStack gatewayPrism) {

        if (Utils.hasLoreTag(gatewayPrism, TeleportalsPlugin.getKey("gateway_prism").toString())) {
            Utils.setLoreData(gatewayPrism, Utils.format("&6&klink"), Utils.blockToKey(anchor));

            Location loc = anchor.getLocation().add(0.5, 0.5, 0.5);
            anchor.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 100, 0.25, 0.25, 0.25, 0.075);
            anchor.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);
        }
    }

    /**
     * Turn on the teleportal. This only works if it is currently off.
     * The gateway prism must be linked to another teleportal.
     */
    public boolean activate(ItemStack gatewayPrism) {

        boolean success = false;
        Location loc = anchor.getLocation().add(0.5, 0.5, 0.5);

        activating:
        {
            if (!Utils.hasLoreTag(gatewayPrism, TeleportalsPlugin.getKey("gateway_prism").toString())) {
                break activating;
            }
            if (!Utils.hasLoreData(gatewayPrism, "link")) {
                break activating;
            }
            if (Utils.getLoreData(gatewayPrism, "link").equalsIgnoreCase(Utils.blockToKey(anchor))) {
                break activating;
            }
            if (anchor.getType() == Material.END_GATEWAY) {
                break activating;
            }
            if (Utils.getMarker(anchor, TeleportalsPlugin.getKey("teleportal").toString()) != null) {
                break activating;
            }
            Utils.createMarker(anchor, getFacing(), TeleportalsPlugin.getKey("teleportal").toString(), gatewayPrism);
            anchor.setType(Material.END_GATEWAY);

            if (anchor.getState() instanceof EndGateway) {
                EndGateway endGateway = (EndGateway) anchor.getState();
                endGateway.setAge(Long.MIN_VALUE); // approx. 292 million years
                endGateway.update(true);
            }
            anchor.getWorld().spawnParticle(Particle.END_ROD, loc, 200, 0.1, 0.1, 0.1, 0.10);
            anchor.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 100, 0.25, 0.25, 0.25, 0.075);
            anchor.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.1, 0.1, 0.1, 0.025);
            anchor.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);
            anchor.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.5f, 1.5f);
            anchor.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.5f, 0.5f);
            success = true;
        }
        if (!success) {
            float randomPitch = 0.90f + (Utils.RNG.nextFloat() * 0.20f);
            anchor.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.1, 0.1, 0.1, 0.025);
            anchor.getWorld().playSound(loc, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, randomPitch);
        }
        return success;
    }

    /**
     * Turn off the teleportal. This works weather it is on, off, or in a broken state.
     */
    public void deactivate(boolean dropGatewayPrism) {

        Location loc = anchor.getLocation().add(0.5, 0.5, 0.5);
        BlockFace facing = getFacing();
        ItemStack gatewayPrism = getGatewayPrism();

        Utils.removeMarker(anchor, TeleportalsPlugin.getKey("teleportal").toString());

        if (anchor.getType() != Material.ENDER_CHEST) {
            anchor.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 5, 0.1, 0.1, 0.05);
            anchor.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 100, 0.25, 0.25, 0.25, 0.075);
            anchor.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.1, 0.1, 0.1, 0.025);
            anchor.getWorld().playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.25f);
        }
        anchor.setType(Material.ENDER_CHEST);
        if (anchor.getBlockData() instanceof Directional) {
            Directional blockData = (Directional) anchor.getBlockData();
            blockData.setFacing(facing);
            anchor.setBlockData(blockData);
        }
        if (dropGatewayPrism && gatewayPrism != null) {
            anchor.getWorld().dropItemNaturally(loc, gatewayPrism);
        }
    }

    /**
     * Teleport the given entity through the teleportal.
     */
    public void teleport(Entity entity, BlockFace face) {

        if (!isActivated()) {
            return;
        }
        Location loc = anchor.getLocation();
        ItemStack gatewayPrism = getGatewayPrism();

        String exitKey = Utils.getLoreData(gatewayPrism, "link");
        Teleportal exit = Teleportal.getFromStruct(Utils.keyToBlock(exitKey));
        if (exit == null) {
            return;
        }
        Location tpLoc = exit.getAnchor().getLocation().add(0.5, -0.5, 0.5);

        BlockFace thisFace = this.getFacing();
        BlockFace exitFace = exit.getFacing();

        int faceOffset = (face.ordinal() - thisFace.ordinal());
        tpLoc.add(Utils.FACES[Math.floorMod(exitFace.ordinal() + faceOffset, Utils.FACES.length)].getDirection());

        float thisYaw = Utils.blockFaceToYaw(thisFace);
        float exitYaw = Utils.blockFaceToYaw(exitFace) - 180;

        tpLoc.setYaw(exitYaw - (thisYaw - entity.getLocation().getYaw()));
        tpLoc.setPitch(entity.getLocation().getPitch());

        float randomSoundPitch = 0.75f + (Utils.RNG.nextFloat() % 0.75f);
        anchor.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.25f, randomSoundPitch);

        entity.teleport(tpLoc, PlayerTeleportEvent.TeleportCause.END_GATEWAY);
    }
}
