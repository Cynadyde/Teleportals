package me.cynadyde.teleportals;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Utilities for the Teleportals plugin.
 */
public class Utils {

    private Utils() {
    }

    /**
     * The four cardinal directions: north, east, south, and west.
     */
    static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /**
     * A random number generator.
     */
    public static final Random RNG = new Random();

    /**
     * Translate the message's chat colors then format the message, if proper objects are given.
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

    /**
     * Get the display name of a given item stack or an empty string if one does not exist.
     */
    public static @Nullable String getDisplayName(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return null;
        }
        if (itemMeta.hasDisplayName()) {
            return itemMeta.getDisplayName();
        }
        return null;
    }

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
     * Add the specified lines to the given item's lore.
     */
    public static void addLore(@Nullable ItemStack item, String... tag) {

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

        lore.addAll(Arrays.asList(tag));

        itemMeta.setLore(lore);
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
     * Get the data under the specified lore key from the given item.
     * If it doesn't exist, an empty string is returned.
     */
    public static @NotNull String getLoreData(@Nullable ItemStack item, @NotNull String key) {

        String rawKey = ChatColor.stripColor(key).trim().toLowerCase();

        if (item == null) {
            return "";
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return "";
        }

        List<String> lore = itemMeta.getLore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        for (String line : lore) {

            String rawLine = ChatColor.stripColor(line).trim().toLowerCase();
            if (rawLine.startsWith(rawKey)) {

                return line.substring(line.indexOf(": ") + 2);
            }
        }
        return "";
    }

    /**
     * Set the specified key in the given item's lore. If the value is null, the key will be removed.
     */
    public static void setLoreData(@Nullable ItemStack item, @NotNull String key, @Nullable String value) {

        if (key.contains(": ")) {
            throw new IllegalArgumentException("lore key cannot contain ': '");
        }

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
        itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
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

                int xPos = Integer.parseInt(tokens[1]);
                int yPos = Integer.parseInt(tokens[2]);
                int zPos = Integer.parseInt(tokens[3]);

                return world.getBlockAt(xPos, yPos, zPos);
            }
        }
        catch (Exception ignored) {

        }
        return null;
    }

    /**
     * Get the yaw represented by a given block face.
     */
    public static float blockFaceToYaw(BlockFace facing) {
        return (facing.ordinal() * 90f) - 180;
    }

    /**
     * Get the cardinal direction represented by a yaw.
     */
    public static BlockFace yawToBlockFace(float yaw) {
        return FACES[Math.floorMod((int) Math.floor((yaw + 180 + 45) / 90f), 4)];
    }

    /**
     * Create an armor stand marker with the given key at the specified block.
     */
    public static void createMarker(@NotNull Block block, @NotNull BlockFace facing, @NotNull String key, @Nullable ItemStack heldItem) {

        Location loc = block.getLocation();
        loc.add(0.5, 0.1, 0.5);
        loc.setYaw(blockFaceToYaw(facing));

        ArmorStand marker = (ArmorStand) block.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

        marker.setCustomName(ChatColor.stripColor(key.toLowerCase()));

        marker.setMarker(true);
        marker.setInvulnerable(true);
        marker.setAI(false);
        marker.setVisible(false);

        marker.setSmall(true);
        marker.setArms(true);

        marker.setRightArmPose(new EulerAngle(-Math.PI / 2, 0, 0));
        Objects.requireNonNull(marker.getEquipment()).setItemInMainHand(heldItem);
    }

    /**
     * Get an armor stand marker with the given key at the specified block.
     */
    public static @Nullable ArmorStand getMarker(@NotNull Block block, @NotNull String key) {

        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(origin, 0.5, 0.5, 0.5)) {

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

    /**
     * Attempt to create a specified recipe from the given config.
     * Looks for a shapeless recipe before looking for a shaped recipe.
     */
    public static @NotNull Recipe createRecipe(@NotNull NamespacedKey key, @NotNull ItemStack result, @NotNull FileConfiguration config, @NotNull String sectionName) throws IllegalArgumentException {

        ConfigurationSection section = config.getConfigurationSection(sectionName);
        if (section == null) {
            throw new IllegalArgumentException(String.format(
                    "The section '%s' does not exist.",
                    sectionName
            ));
        }
        Recipe recipe;

        if (section.get("recipe-shapeless") != null) {
            List<String> ingredients = section.getStringList("recipe-shapeless");

            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "The ingredients list at '%s.recipe-shapeless' is empty.",
                        sectionName
                ));
            }
            recipe = new ShapelessRecipe(key, result);

            for (String matName : ingredients) {

                // try to add the material enum to the recipe...
                Material mat;
                try {
                    mat = Material.valueOf(matName);
                }
                catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(String.format(
                            "The material name '%s' in '%s.recipe-shapeless' is invalid.",
                            matName, sectionName
                    ));
                }
                ((ShapelessRecipe) recipe).addIngredient(mat);  // uncaught IllegalArgumentException
            }
        }
        else if (section.isConfigurationSection("recipe-shaped")) {

            ConfigurationSection shapedSection = section.getConfigurationSection("recipe-shaped");
            assert shapedSection != null;
            recipe = new ShapedRecipe(key, result);

            // get and set the recipe's shape from the config...
            if (shapedSection.get("shape") == null) {
                throw new IllegalArgumentException(String.format(
                        "Missing shape in '%s'",
                        shapedSection.getCurrentPath()
                ));
            }
            List<String> shape = shapedSection.getStringList("shape");
            ((ShapedRecipe) recipe).shape(shape.toArray(new String[0]));  // uncaught IllegalArgumentException

            // get the ingredients mapping from the config...
            ConfigurationSection mapSection = shapedSection.getConfigurationSection("map");
            if (mapSection == null || mapSection.getKeys(false).isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "The ingredients mapping at '%s.map' is missing or empty.",
                        shapedSection
                ));
            }
            for (String mapKey : mapSection.getKeys(false)) {

                // make sure the key is 1 char...
                if (mapKey.length() != 1) {
                    throw new IllegalArgumentException(String.format(
                            "The key '%s' in '%s' is invalid.",
                            mapKey, mapSection.getCurrentPath()
                    ));
                }
                // try to add the material enum to the recipe...
                String matName = mapSection.getString(mapKey);
                Material mat;
                try {
                    mat = Material.valueOf(matName);
                }
                catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(String.format(
                            "The material name '%s' at '%s.%s' is invalid.",
                            matName, mapSection.getCurrentPath(), mapKey
                    ));
                }
                try {
                    ((ShapedRecipe) recipe).setIngredient(mapKey.charAt(0), mat);
                }
                catch (IllegalArgumentException ignored) {
                    // if the symbol does not appear in the shape, just ignore it
                }
            }
        }
        // if no recipe was defined...
        else {
            throw new IllegalArgumentException(String.format(
                    "A recipe for '%s' was not defined.",
                    sectionName
            ));
        }
        return recipe;
    }
}
