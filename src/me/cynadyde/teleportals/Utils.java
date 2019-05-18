package me.cynadyde.teleportals;

import org.bukkit.ChatColor;

/**
 *
 */
@SuppressWarnings("WeakerAccess")
public class Utils {

    public static String format(String message, Object...objects) {

        return String.format(ChatColor.translateAlternateColorCodes('&', message), objects);
    }
}
