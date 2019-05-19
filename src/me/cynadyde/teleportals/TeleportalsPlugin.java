package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Main class of the Teleportals Plugin.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TeleportalsPlugin extends JavaPlugin implements Listener {

    private Map<String, List<Location>> subspaces = new HashMap<>();
    private ShapedRecipe gatewayPrismRecipe;

    private String gatewayPrismDisplay = Utils.format("&bGateway Prism");
    private String gatewayPrismDataHeader = Utils.format("&5&nSubspace Link");
    private String gatewayPrismDataPrefix = Utils.format("  &0&k");

    /**
     * Add the gateway prism recipe to the server, load subspaces, and register event listeners.
     */
    @Override
    public void onEnable() {

        NamespacedKey recipeKey = new NamespacedKey(this, "gateway_prism");
        ItemStack recipeResult = createGatewayPrism(2, "");
        gatewayPrismRecipe = new ShapedRecipe(recipeKey, recipeResult);

        gatewayPrismRecipe.shape("010", "2#4", "030");
        gatewayPrismRecipe.setIngredient('#', Material.ENCHANTED_BOOK);
        gatewayPrismRecipe.setIngredient('0', Material.ENDER_EYE);
        gatewayPrismRecipe.setIngredient('1', Material.TOTEM_OF_UNDYING);
        gatewayPrismRecipe.setIngredient('2', Material.NETHER_STAR);
        gatewayPrismRecipe.setIngredient('3', Material.DRAGON_HEAD);
        gatewayPrismRecipe.setIngredient('4', Material.CONDUIT);

        getServer().addRecipe(gatewayPrismRecipe);
        getServer().getPluginManager().registerEvents(this, this);

        loadSubspaces();
    }

    /**
     * Save subspaces to the data file.
     */
    @Override
    public void onDisable() {

        saveSubspaces();
    }

    /**
     * When crafting a gateway prism, set its subspace link using the enchanted book in the recipe.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {

        if (event.getRecipe().equals(gatewayPrismRecipe)) {

            String subspaceName = enchantsToSubspaceName(event.getInventory().getMatrix()[4]);
            setSubspaceLink(event.getInventory().getResult(), subspaceName);
        }
    }

    /**
     * Handle the activation of teleportals AND disable placing the gateway prism.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {

        // the player right clicks a block...
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        // if the player clicked with a gateway prism...
        ItemStack usedItem = event.getPlayer().getInventory().getItemInMainHand();
        if (isGatewayPrism(usedItem)) {

            // cancel interaction event...
            event.setCancelled(true);

            // if the clicked block was an ender chest...
            Block block = event.getClickedBlock();
            if (block != null && block.getType().equals(Material.ENDER_CHEST)) {

                // if the clicked block was part of a teleportal structure...
                if (isTeleportalFrameAt(block)) {

                    // activate the teleportal...
                    createTeleportalAt(block, getSubspaceLink(usedItem));

                    // consume the gateway prism...
                    if (usedItem.getAmount() > 1) {
                        usedItem.setAmount(usedItem.getAmount() - 1);
                    }
                    else {
                        event.getPlayer().getInventory().setItemInMainHand(null);
                    }
                }
            }
        }
    }

    /**
     * Handle destruction of teleportals.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        Block block = event.getBlock();

        // a teleportal was broken...
        if (isTeleportalFrameAt(block)) {

            // deactivate the teleportal...
            removeTeleportalAt(block);
        }
    }

    /**
     * Handle entities using teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {

        useTeleportal(event.getLocation().getBlock(), event.getEntity());
    }

    /**
     * Copy saved subspaces and their links from the plugin's data file.
     */
    public void loadSubspaces() {

        // get the plugin's yaml data file...
        File file = new File(getDataFolder(), "data.yml");
        YamlConfiguration ymlData = YamlConfiguration.loadConfiguration(file);

        // get the subspaces section...
        if (!ymlData.contains("subspaces")) {
            ymlData.createSection("subspaces");
        }
        ConfigurationSection ymlSubspaces = ymlData.getConfigurationSection("subspaces");
        assert ymlSubspaces != null;

        // for each subspace in the data file...
        for (String subspaceName : ymlSubspaces.getKeys(false)) {

            List<Location> subspaceLinks = new ArrayList<>();

            List<?> subspace = ymlSubspaces.getList(subspaceName);
            if (subspace == null || subspace.isEmpty()) {
                continue;
            }
            // for each link in the subspace...
            for (Object obj : subspace) {
                if (!(obj instanceof String)) {
                    continue;
                }
                // get the deserialized location...
                String[] link = ((String) obj).split(",", 4);
                if (link.length != 4) {
                    continue;
                }
                try {
                    World w = getServer().getWorld(link[0]);
                    if (w == null) {
                        continue;
                    }
                    int x = Integer.parseInt(link[1]);
                    int y = Integer.parseInt(link[2]);
                    int z = Integer.parseInt(link[3]);

                    subspaceLinks.add(new Location(w, x, y, z));
                }
                catch (NumberFormatException ignored) {

                }
            }
            // add all collected subspace links to the plugin...
            subspaces.put(subspaceName, subspaceLinks);
        }
    }

    /**
     * Copy loaded subspaces and their links to the plugin's data file.
     */
    public void saveSubspaces() {

        // get the plugin's yaml data file...
        File file = new File(getDataFolder(), "data.yml");
        YamlConfiguration ymlData = YamlConfiguration.loadConfiguration(file);

        // get a clean subspaces section...
        ymlData.set("subspaces", null);
        ConfigurationSection ymlSubspaces = ymlData.createSection("subspaces");

        // for subspace in loaded data...
        for (String subspaceName : subspaces.keySet()) {

            // for each link in the subspace...
            List<String> subspaceLinks = new ArrayList<>();
            for (Location subspaceLink : subspaces.get(subspaceName)) {

                // write the serialized link to yaml...
                if (subspaceLink.getWorld() == null) {
                    continue;
                }
                String link = String.format("%s,%d,%d,%d",
                        subspaceLink.getWorld().getName(),
                        subspaceLink.getBlockX(),
                        subspaceLink.getBlockY(),
                        subspaceLink.getBlockZ()
                );
                subspaceLinks.add(link);
            }
            ymlSubspaces.set(subspaceName, subspaceLinks);
        }
        // save the data yaml to file...
        try {
            ymlData.save(file);
        }
        catch (IOException ex) {
            getLogger().severe(String.format("Could not save plugin data! %s", ex.getMessage()));
            getLogger().severe("If the issue is not resolved, this plugin will lose data on shutdown.");
        }
    }

    /**
     * Get the subspace name specified by an enchanted book.
     */
    public String enchantsToSubspaceName(ItemStack enchantedBook) {

        if (enchantedBook == null) {
            return "";
        }
        if (!enchantedBook.getType().equals(Material.ENCHANTED_BOOK)) {
            return "";
        }
        ItemMeta itemMeta = enchantedBook.getItemMeta();
        if (itemMeta == null) {
            return "";
        }
        Map<Enchantment, Integer> enchants = itemMeta.getEnchants();
        List<String> elements = new ArrayList<>();

        for (Enchantment enchant : enchants.keySet()) {
            String enchantId = enchant.getKey().getKey();
            String enchantLvl = String.valueOf(enchants.get(enchant));
            elements.add(enchantId + ":" + enchantLvl);
        }
        Collections.sort(elements);
        return String.join(",", elements);
    }

    /**
     * Test if the given item is a gateway prism.
     */
    public boolean isGatewayPrism(ItemStack item) {

        if (item == null || !item.getType().equals(Material.END_CRYSTAL)) {
            return false;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        List<String> itemLore = itemMeta.getLore();

        if (itemLore == null || itemLore.isEmpty()) {
            return false;
        }
        if (!itemLore.contains(gatewayPrismDataHeader)) {
            return false;
        }
        int dataIndex = itemLore.indexOf(gatewayPrismDataHeader) + 1;

        if (dataIndex >= itemLore.size()) {
            return false;
        }
        //noinspection RedundantIfStatement
        if (!itemLore.get(dataIndex).startsWith(gatewayPrismDataPrefix)) {
            return false;
        }
        return true;
    }

    /**
     * Create a new gateway prism linked to the given subspace.
     */
    public ItemStack createGatewayPrism(int amount, String subspaceName) {

        ItemStack item = new ItemStack(Material.END_CRYSTAL, amount);

        ItemMeta itemMeta = item.getItemMeta();
        assert itemMeta != null;

        itemMeta.setDisplayName(gatewayPrismDisplay);

        List<String> lore = new ArrayList<>();
        lore.add(gatewayPrismDataHeader);
        lore.add(gatewayPrismDataPrefix + subspaceName);
        itemMeta.setLore(lore);

        item.setItemMeta(itemMeta);

        return item;
    }

    /**
     * Get the subspace this gateway prism is linked to.
     */
    public String getSubspaceLink(ItemStack gatewayPrism) {

        ItemMeta itemMeta = gatewayPrism.getItemMeta();
        if (itemMeta == null) {
            return "";
        }
        List<String> itemLore = itemMeta.getLore();
        if (itemLore == null) {
            itemLore = new ArrayList<>();
        }
        if (!itemLore.contains(gatewayPrismDataHeader)) {
            return "";
        }
        int dataIndex = itemLore.indexOf(gatewayPrismDataHeader) + 1;
        if (dataIndex >= itemLore.size()) {
            return "";
        }
        String dataLine = itemLore.get(dataIndex);
        if (!dataLine.startsWith(gatewayPrismDataPrefix)) {
            return "";
        }
        return dataLine.substring(gatewayPrismDataPrefix.length());
    }

    /**
     * Set the subspace this gateway prism is linked to.
     */
    public void setSubspaceLink(ItemStack gatewayPrism, String subspaceName) {

        ItemMeta itemMeta = gatewayPrism.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> itemLore = itemMeta.getLore();
        if (itemLore == null) {
            itemLore = new ArrayList<>();
        }
        if (!itemLore.contains(gatewayPrismDataHeader)) {
            itemLore.add(gatewayPrismDataHeader);
        }
        int dataIndex = itemLore.indexOf(gatewayPrismDataHeader) + 1;
        if (dataIndex < itemLore.size()) {
            itemLore.set(dataIndex, gatewayPrismDataPrefix + subspaceName);
        }
        else {
            itemLore.add(gatewayPrismDataPrefix + subspaceName);
        }
        itemMeta.setLore(itemLore);
        gatewayPrism.setItemMeta(itemMeta);
    }

    /**
     * Test if the given block is part of a teleportal structure.
     */
    public boolean isTeleportalFrameAt(Block block) {

        if (block == null) {
            return false;
        }
        for (BlockFace dir : new BlockFace[]{BlockFace.SELF, BlockFace.UP, BlockFace.DOWN}) {

            Block relBlock = block.getRelative(dir);

            if ((relBlock.getType().equals(Material.ENDER_CHEST)
                    || relBlock.getType().equals(Material.END_GATEWAY))) {

                if (relBlock.getRelative(BlockFace.DOWN).getType().equals(Material.OBSIDIAN)
                        && relBlock.getRelative(BlockFace.UP).getType().equals(Material.OBSIDIAN)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Create a teleportal linked to the given subspace at a block.
     */
    public void createTeleportalAt(Block block, ItemStack gatewayPrism) {

        block.setType(Material.END_GATEWAY);
        Location pLoc = block.getLocation().add(0.5f, 0.5f, 0.5f);
        block.getWorld().spawnParticle(Particle.FLASH, pLoc, 1);

        Location eLoc = block.getLocation().add(0.5f, 0f, 0.5f);
        ArmorStand armorStand = (ArmorStand) block.getWorld().spawnEntity(eLoc, EntityType.ARMOR_STAND);

        armorStand.setArms(false);
        armorStand.setBasePlate(true);
        armorStand.setCustomName("Teleportal");
        armorStand.setCustomNameVisible(false);
        armorStand.setCollidable(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);

        armorStand.setItemInHand(gatewayPrism);
    }

    /**
     * Remove the teleportal at a block.
     */
    public void removeTeleportalAt(Block block) {

        // TODO remove teleportal
    }

    /**
     * Have an entity use the teleportal at the given block.
     */
    public void useTeleportal(Block block, Entity entity) {

        // TODO use teleportal
    }
}
