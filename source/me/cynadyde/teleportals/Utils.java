package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utilities for the Teleportals plugin.
 */
@SuppressWarnings("WeakerAccess")
public class Utils {

    private static BlockFace[] directions = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /**
     * Java's random number generator.
     */
    public static final Random RNG = new Random();

    /**
     * Translate the message's chat colors, then formats the message.
     */
    public static @NotNull String format(@NotNull String message, Object... objects) {

        return String.format(ChatColor.translateAlternateColorCodes('&', message), objects);
    }

    /**
     * Get the enchantments of an item stack.
     */
    public static @NotNull Map<Enchantment, Integer> getEnchants(@NotNull ItemStack items) {

        Map<Enchantment, Integer> results = new HashMap<>();

        ItemMeta itemMeta = items.getItemMeta();
        if (itemMeta == null) {
            return results;
        }
        return itemMeta.getEnchants();
    }

    /**
     * Set the enchantments of an item stack.
     */
    public static void setEnchants(@NotNull ItemStack items, @NotNull Map<Enchantment, Integer> enchants) {

        ItemMeta itemMeta = items.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        for (Enchantment ench : enchants.keySet()) {

            itemMeta.addEnchant(ench, enchants.get(ench), true);
        }
        items.setItemMeta(itemMeta);
    }

    /**
     * Create a key representing the given enchantments.
     */
    public static @NotNull String enchantsToKey(@NotNull Map<Enchantment, Integer> enchants) {

        List<String> elements = new ArrayList<>();
        for (Enchantment enchant : enchants.keySet()) {

            String enchantId = enchant.getKey().getKey().toLowerCase();
            String enchantLvl = String.valueOf(enchants.get(enchant));
            elements.add(enchantId + "=" + enchantLvl);
        }
        Collections.sort(elements);
        return String.join(",", elements);
    }

    /**
     * Get enchantments represented by the given key.
     */
    public static @Nullable Map<Enchantment, Integer> keyToEnchants(@NotNull String key) {

        try {
            Map<Enchantment, Integer> results = new HashMap<>();

            for (String token : key.split(",")) {

                String[] subTokens = token.split("=");

                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(subTokens[0]));
                assert ench != null;
                Integer lvl = Integer.valueOf(subTokens[1]);

                results.put(ench, lvl);
            }
            return results;
        }
        catch (Exception ignored) {

        }
        return null;
    }

    /**
     * Create a key representing the given block in a world.
     */
    public static @NotNull String posToKey(@NotNull Block block) {

        return String.format("%s,%d,%d,%d", block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Get a block in a world represented the given key.
     */
    public static @Nullable Block keyToPos(@NotNull String key) {

        try {
            String[] tokens = key.split(",");

            World world = Bukkit.getWorld(tokens[0]);

            if (world != null) {

                int xPos = Integer.valueOf(tokens[1]);
                int yPos = Integer.valueOf(tokens[2]);
                int zPos = Integer.valueOf(tokens[3]);

                return world.getBlockAt(xPos, yPos, zPos);
            }
        }
        catch (Exception ignored) {

        }
        return null;
    }

    public static @NotNull BlockFace yawToBlockFace(int yaw) {
        return directions[Math.round(yaw / 90f) & 0x3];
    }

    public static float blockFaceToYaw(BlockFace facing) {
        return ((facing.ordinal() * 90f) % 360) - 180;
    }

    /**
     * Get an armor stand marker with the given key at the specified block.
     */
    public static @Nullable ArmorStand getMarker(@NotNull Block block, @NotNull String key) {

        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        BoundingBox box = new BoundingBox(
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                origin.getBlockX()+1, origin.getBlockY()+1, origin.getBlockZ()+1
        );
        for (Entity entity : block.getWorld().getNearbyEntities(origin, 0.6, 0.6, 0.6)) {

            if (entity.getType() == EntityType.ARMOR_STAND) {
                ArmorStand armorStand = (ArmorStand) entity;
                String name = armorStand.getCustomName();

                Bukkit.getLogger().info("[Teleportals] ARMOR STAND WAS FOUND: " + ((name == null) ? "null" : name));

                if (name != null) {
                    if (ChatColor.stripColor(name).equalsIgnoreCase(key)) {

                        return armorStand;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Create an armor stand marker with the given key at the specified block.
     */
    public static @NotNull ArmorStand createMarker(@NotNull Block block, @NotNull String key, BlockFace facing) {

        Location loc = block.getLocation();
        loc.add(0.5, 0.0, 0.5);
        loc.setYaw(blockFaceToYaw(facing));

        ArmorStand marker = (ArmorStand) block.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

        marker.setCustomName(ChatColor.stripColor(key.toLowerCase()));
        marker.setMarker(true);
        marker.setInvulnerable(true);
        marker.setAI(false);
        marker.setVisible(false);
        marker.setSmall(true);
        marker.setArms(true);
        marker.setRightArmPose(new EulerAngle(-Math.PI/2, 0, 0));

        return marker;
    }

    /**
     * Remove the armor stand marker with the given key at the specified block.
     */
    public static void removeMarker(@NotNull Block block, @NotNull String key) {

        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(origin, 0.5, 0.5, 0.5)) {

            if (entity.getType() == EntityType.ARMOR_STAND) {
                ArmorStand armorStand = (ArmorStand) entity;
                String name = armorStand.getCustomName();

                if (name != null) {
                    if (ChatColor.stripColor(name).equalsIgnoreCase(key)) {

                        armorStand.remove();
                    }
                }
            }
        }
    }
}
