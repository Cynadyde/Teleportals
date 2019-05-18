package me.cynadyde.teleportals;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@SuppressWarnings("WeakerAccess")
public class GatewayPrism {

    public static final String keyName = "gateway_prism";
    public static final String displayName = Utils.format("&bGateway Prism");
    public static final String loreDataHeader = Utils.format("&5&nSubspace Link");
    public static final String loreDataPrefix = Utils.format("  &0&k");

    /**
     *
     */
    public static GatewayPrism create(int amount, String subspace) {

        ItemStack item = new ItemStack(Material.END_CRYSTAL, amount);

        ItemMeta itemMeta = item.getItemMeta();
        assert itemMeta != null;

        itemMeta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(loreDataHeader);
        lore.add(loreDataPrefix + subspace);
        itemMeta.setLore(lore);

        item.setItemMeta(itemMeta);

        return new GatewayPrism(item);
    }

    /**
     *
     */
    public static GatewayPrism fromItem(ItemStack item) {

        if (item == null || !item.getType().equals(Material.END_CRYSTAL)) {
            return null;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return null;
        }
        List<String> itemLore = itemMeta.getLore();

        if (itemLore == null || itemLore.isEmpty()) {
            return null;
        }
        if (!itemLore.contains(loreDataHeader)) {
            return null;
        }
        int dataIndex = itemLore.indexOf(loreDataHeader) + 1;

        if (dataIndex >= itemLore.size()) {
            return null;
        }
        if (!itemLore.get(dataIndex).startsWith(loreDataPrefix)) {
            return null;
        }
        return new GatewayPrism(item);
    }

    private ItemStack item;

    private GatewayPrism(ItemStack item) {

        this.item = item;
    }

    public ItemStack toItem() {

        return item.clone();
    }

    public String getSubspaceLink() {

        ItemMeta itemMeta = item.getItemMeta();
        assert itemMeta != null;

        List<String> itemLore = itemMeta.getLore();
        if (itemLore == null) {
            return "";
        }
        if (!itemLore.contains(loreDataHeader)) {
            return "";
        }
        int dataIndex = itemLore.indexOf(loreDataHeader) + 1;
        if (dataIndex >= itemLore.size()) {
            return "";
        }
        String result = itemLore.get(dataIndex);
        if (!result.startsWith(loreDataPrefix)) {
            return "";
        }
        return result.substring(loreDataPrefix.length());
    }

    @SuppressWarnings("UnusedReturnValue")
    public GatewayPrism setSubspaceLink(String subspace) {

        ItemMeta itemMeta = item.getItemMeta();
        assert itemMeta != null;

        List<String> itemLore = itemMeta.getLore();
        if (itemLore == null) {
            itemLore = new ArrayList<>();
        }
        if (!itemLore.contains(loreDataHeader)) {
            itemLore.add(loreDataHeader);
        }
        int dataIndex = itemLore.indexOf(loreDataHeader) + 1;

        if (dataIndex >= itemLore.size()) {
            itemLore.add(loreDataPrefix + subspace);
        }
        else {
            itemLore.set(dataIndex, loreDataPrefix + subspace);
        }
        itemMeta.setLore(itemLore);
        item.setItemMeta(itemMeta);

        return this;
    }
}
