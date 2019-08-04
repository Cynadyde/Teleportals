package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utilities for the Teleportals plugin.
 */
@SuppressWarnings({ "WeakerAccess" })
public class Utils {

    /**
     * Block faces in the four cardinal directions.
     */
    public static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /**
     * Java's random number generator.
     */
    public static final Random RNG = new Random();

    /**
     * Translate the message's chat colors, then format the message if proper objects are given.
     */
    public static @NotNull String format(@NotNull String message, Object... objects) {

        String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
        try {
            return String.format(coloredMessage, objects);
        }
        catch (IllegalFormatException ex) {
            return coloredMessage;
        }
    }

//    /**
//     * Get the enchantments of an item stack.
//     */
//    public static @NotNull Map<Enchantment, Integer> getEnchants(@NotNull ItemStack items) {
//
//        Map<Enchantment, Integer> results = new HashMap<>();
//
//        ItemMeta itemMeta = items.getItemMeta();
//        if (itemMeta == null) {
//            return results;
//        }
//        return itemMeta.getEnchants();
//    }
//
//    /**
//     * Set the enchantments of an item stack.
//     */
//    public static void setEnchants(@NotNull ItemStack items, @NotNull Map<Enchantment, Integer> enchants) {
//
//        ItemMeta itemMeta = items.getItemMeta();
//        if (itemMeta == null) {
//            return;
//        }
//        for (Enchantment ench : enchants.keySet()) {
//
//            itemMeta.addEnchant(ench, enchants.get(ench), true);
//        }
//        items.setItemMeta(itemMeta);
//    }
//
//    /**
//     * Create a key representing the given enchantments.
//     */
//    public static @NotNull String enchantsToKey(@NotNull Map<Enchantment, Integer> enchants) {
//
//        List<String> elements = new ArrayList<>();
//        for (Enchantment enchant : enchants.keySet()) {
//
//            String enchantId = enchant.getKey().getKey().toLowerCase();
//            String enchantLvl = String.valueOf(enchants.get(enchant));
//            elements.add(enchantId + "=" + enchantLvl);
//        }
//        Collections.sort(elements);
//        return String.join(",", elements);
//    }
//
//    /**
//     * Get enchantments represented by the given key.
//     */
//    public static @Nullable Map<Enchantment, Integer> keyToEnchants(@NotNull String key) {
//
//        try {
//            Map<Enchantment, Integer> results = new HashMap<>();
//
//            for (String token : key.split(",")) {
//
//                String[] subTokens = token.split("=");
//
//                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(subTokens[0]));
//                assert ench != null;
//                Integer lvl = Integer.valueOf(subTokens[1]);
//
//                results.put(ench, lvl);
//            }
//            return results;
//        }
//        catch (Exception ignored) {
//
//        }
//        return null;
//    }

    /**
     * Set the display name of a given item stack.
     */
    public static void setDisplayName(@Nullable ItemStack item, @Nullable String displayName) {

        if (item == null) {
            return;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        itemMeta.setDisplayName(displayName);
        item.setItemMeta(itemMeta);
    }

    /**
     * Check if the given item has the specified tag in its lore.
     */
    public static boolean hasLoreTag(@Nullable ItemStack item, @NotNull String tag) {

        String rawTag = ChatColor.stripColor(tag).trim();

        if (item == null) {
            return false;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return false;
        }

        List<String> lore = itemMeta.getLore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }

        for (String line : lore) {

            if (ChatColor.stripColor(line).trim().equalsIgnoreCase(rawTag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the specified tag to the given item's lore, if it doesn't already exist.
     */
    public static void addLoreTag(@Nullable ItemStack item, @NotNull String tag) {

        if (item == null) {
            return;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return;
        }

        List<String> lore = itemMeta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        lore.add(tag);

        itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
    }

    /**
     * Check if the given item has the specified data key in its lore.
     */
    public static boolean hasLoreData(@Nullable ItemStack item, @NotNull String key) {

        String rawKey = ChatColor.stripColor(key).trim().toLowerCase();

        if (item == null) {
            return false;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return false;
        }

        List<String> lore = itemMeta.getLore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }
        for (String line : lore) {

            String rawLine = ChatColor.stripColor(line).trim().toLowerCase();
            if (rawLine.startsWith(rawKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the specified key in the given item's lore. If the value is null, the key will be removed.
     */
    public static void setLoreData(@Nullable ItemStack item, @NotNull String key, @Nullable String value) {

        String rawKey = ChatColor.stripColor(key).trim().toLowerCase();

        if (item == null) {
            return;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return;
        }

        List<String> lore = itemMeta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        Integer index = null;
        for (int i = 0; i < lore.size(); i++) {

            String line = ChatColor.stripColor(lore.get(i)).trim().toLowerCase();
            if (line.startsWith(rawKey)) {
                index = i;
                break;
            }
        }
        if (index == null) {

            if (value != null) {
                lore.add(key + ": " + value);
            }
        }
        else {
            if (value == null) {
                lore.remove((int) index);
            }
            else {
                lore.set(index, key + ": " + value);
            }
        }
    }

    /**
     * Create a key representing the given block in a world.
     */
    public static @NotNull String blockToKey(@NotNull Block block) {

        return String.format("%s,%d,%d,%d", block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Get a block in a world represented by the given key.
     */
    public static @Nullable Block keyToBlock(@NotNull String key) {

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

//    public static @NotNull BlockFace yawToBlockFace(int yaw) {
//        return FACES[Math.round(yaw / 90f) & 0x3];
//    }

    /**
     * Get the yaw represented by a given block face.
     */
    public static float blockFaceToYaw(BlockFace facing) {
        return (facing.ordinal() * 90f);
    }

    /**
     * Create an armor stand marker with the given key at the specified block.
     */
    public static @NotNull ArmorStand createMarker(@NotNull Block block, @NotNull BlockFace facing, @NotNull String key) {

        Location loc = block.getLocation();
        loc.add(0.5, 0.0, 0.5);
        loc.setYaw(blockFaceToYaw(facing) + 180); // TODO add 180 inside of blockFaceToYaw function, then fix usages

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
     * Get an armor stand marker with the given key at the specified block.
     */
    public static @Nullable ArmorStand getMarker(@NotNull Block block, @NotNull String key) {

        Location origin = block.getLocation().add(0.5, 0.5, 0.5);

        // TODO improve nearby entity search. don't use 0.6 radius. see if bounding box works.

//        BoundingBox box = new BoundingBox(
//                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
//                origin.getBlockX()+1, origin.getBlockY()+1, origin.getBlockZ()+1
//        );
        for (Entity entity : block.getWorld().getNearbyEntities(origin, 0.6, 0.6, 0.6)) {

            if (entity.getType() == EntityType.ARMOR_STAND) {
                ArmorStand armorStand = (ArmorStand) entity;
                String name = armorStand.getCustomName();

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
     * Remove the armor stand marker with the given key at the specified block.
     */
    public static void removeMarker(@NotNull Block block, @NotNull String key) {

        Location origin = block.getLocation().add(0.5, 0.5, 0.5);

        // TODO improve nearby entity search. don't use 0.6 radius. see if bounding box works.

//        BoundingBox box = new BoundingBox(
//                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
//                origin.getBlockX()+1, origin.getBlockY()+1, origin.getBlockZ()+1
//        );
        for (Entity entity : block.getWorld().getNearbyEntities(origin, 0.6, 0.6, 0.6)) {

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
