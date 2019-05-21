package me.cynadyde.teleportals;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Utilities for the Teleportals plugin.
 */
@SuppressWarnings("WeakerAccess")
public class Utils {

    /**
     * Java's random number generator.
     */
    public static final Random RNG = new Random();

    /**
     * translates chat colors to the message, then formats it.
     */
    @NotNull
    public static String format(@NotNull String message, Object... objects) {

        return String.format(ChatColor.translateAlternateColorCodes('&', message), objects);
    }

    /**
     * Create a location-string from the given location object.
     */
    @NotNull
    public static String locationToString(@NotNull Location loc) {

        return String.format("%s,%d,%d,%d",
                loc.getWorld() != null ? loc.getWorld().getName() : "",
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    /**
     * Create a location object from the given location-string.
     */
    @Nullable
    public static Location stringToLocation(@NotNull String string) {

        String[] link = string.split(",", 4);
        if (link.length != 4) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(link[0]);
            int x = Integer.parseInt(link[1]);
            int y = Integer.parseInt(link[2]);
            int z = Integer.parseInt(link[3]);

            return new Location(world, x, y, z);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Get the index of the prefix string in the list, discounting color codes (or -1 if not found)
     */
    public static int colorStripIndexOf(List<String> list, String prefix) {
        prefix = ChatColor.stripColor(prefix);
        for (int i = 0; i < list.size(); i++) {
            if (ChatColor.stripColor(list.get(i)).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
