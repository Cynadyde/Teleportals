package me.cynadyde.teleportals;

import org.bukkit.ChatColor;

import java.util.Random;

/**
 * Utilities for the Teleportals plugin.
 */
@SuppressWarnings("WeakerAccess")
public class Utils {

    public static final Random RNG = new Random();

    /**
     * translates chat colors to the message, then formats it.
     */
    public static String format(String message, Object... objects) {

        return String.format(ChatColor.translateAlternateColorCodes('&', message), objects);
    }
}
