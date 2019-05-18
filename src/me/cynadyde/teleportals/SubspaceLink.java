package me.cynadyde.teleportals;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 */
@SuppressWarnings("WeakerAccess")
public class SubspaceLink {

    /**
     *
     */
    public static String fromEnchBook(ItemStack book) {

        if (book == null || !book.getType().equals(Material.ENCHANTED_BOOK)) {
            return "";
        }
        ItemMeta bookMeta = book.getItemMeta();
        assert bookMeta != null;

        Map<Enchantment, Integer> enchants = bookMeta.getEnchants();
        List<String> elements = new ArrayList<>();

        for (Enchantment ench : enchants.keySet()) {

            elements.add(ench.getKey().getKey() + ":" + String.valueOf(enchants.get(ench)));
        }
        return String.join(",", elements);
    }

    /**
     *
     */
    public static ItemStack toEnchBook(String subspace) {

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta bookMeta = book.getItemMeta();
        assert bookMeta != null;

        for (String element : subspace.split(",")) {
            try {
                String name = element.substring(0, element.indexOf(':'));
                String lvl = element.substring(element.indexOf(':'));
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(name));
                if (ench == null) {
                    continue;
                }
                bookMeta.addEnchant(ench, Integer.parseInt(lvl), true);
            }
            catch (IndexOutOfBoundsException | NumberFormatException ignored) {

            }
        }
        book.setItemMeta(bookMeta);
        return book;
    }
}
