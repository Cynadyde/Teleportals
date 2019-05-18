package me.cynadyde.teleportals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main class of the Teleportals Plugin.
 */
@SuppressWarnings("WeakerAccess")
public class TeleportalsPlugin extends JavaPlugin implements Listener {

   static final Map<String, List<Location>> subspaces = new HashMap<>();

    @Override
    public void onEnable() {

        // Add the gateway prism to server's crafting recipes...
        NamespacedKey recipeKey = new NamespacedKey(this, GatewayPrism.keyName);
        ItemStack recipeResult = GatewayPrism.create(2, "").toItem();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, recipeResult);

        recipe.shape("010", "2#4", "030");
        recipe.setIngredient('#', Material.ENCHANTED_BOOK);
        recipe.setIngredient('0', Material.ENDER_EYE);
        recipe.setIngredient('1', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('2', Material.NETHER_STAR);
        recipe.setIngredient('3', Material.DRAGON_HEAD);
        recipe.setIngredient('4', Material.CONDUIT);

        getServer().addRecipe(recipe);

        // load active teleportals from plugin data folder...
        loadSubspaces();

        // Register plugin to listen to events...
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {

        // save active teleportals to plugin data folder...
        saveSubspaces();
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
                    double x = Double.parseDouble(link[1]);
                    double y = Double.parseDouble(link[2]);
                    double z = Double.parseDouble(link[3]);

                    subspaceLinks.add(new Location(w, x, y, z));
                }
                catch (NumberFormatException ignored) {

                }
            }
            // add all collected subspace links to the plugin...
            if (!subspaces.containsKey(subspaceName)) {
                subspaces.put(subspaceName, new ArrayList<>());
            }
            subspaces.get(subspaceName).addAll(subspaceLinks);
        }
    }

    /**
     * Copy loaded subspaces and their links to the plugin's data file.
     */
    public void saveSubspaces() {

        // get the plugin's yaml data file...
        File file = new File(getDataFolder(), "data.yml");
        YamlConfiguration ymlData = YamlConfiguration.loadConfiguration(file);

        // get the subspaces section...
        if (!ymlData.contains("subspaces")) {
            ymlData.createSection("subspaces");
        }
        ConfigurationSection ymlSubspaces = ymlData.getConfigurationSection("subspaces");
        assert ymlSubspaces != null;

        for (String subspaceName : subspaces.keySet()) {

            List<String> subspaceLinks = new ArrayList<>();
            for (Location subspaceLink : subspaces.get(subspaceName)) {

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
        try {
            ymlData.save(file);
        }
        catch (IOException ex) {
            getLogger().severe(String.format("Could not save plugin data! %s", ex.getMessage()));
            getLogger().severe("If the issue is not resolved, this plugin will lose data on shutdown.");
        }
    }

    /**
     * When crafting a gateway prism, set its subspace link using the enchanted book in the recipe.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {

        ItemStack resultItem = event.getInventory().getResult();
        GatewayPrism gatewayPrism = GatewayPrism.fromItem(resultItem);
        if (gatewayPrism == null) {
            return;
        }
        ItemStack enchBook = event.getInventory().getMatrix()[4];
        String subspace = SubspaceLink.fromEnchBook(enchBook);

        gatewayPrism.setSubspaceLink(subspace);
        event.getInventory().setResult(gatewayPrism.toItem());
    }

    /**
     * Disable placing the gateway prism and handle activating teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {

        // the player right clicks a block...
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        // if the player clicked with a gateway prism...
        ItemStack usedItem = event.getPlayer().getInventory().getItemInMainHand();
        GatewayPrism gatewayPrism = GatewayPrism.fromItem(usedItem);
        if (gatewayPrism != null) {

            // cancel interaction event...
            event.setCancelled(true);

            // if the clicked block was a teleportal...
            Teleportal teleportal = Teleportal.getAt(event.getClickedBlock());
            if (teleportal != null) {

                // if the teleportal was inactive...
                if (!teleportal.isActive()) {

                    // activate the teleportal and consume the gateway prism...
                    teleportal.activate(this, gatewayPrism.getSubspaceLink());
                    event.getPlayer().getInventory().setItemInMainHand(null);
                }
            }
        }
    }

    /**
     * Handle destruction of teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        // a teleportal was broken...
        Teleportal teleportal = Teleportal.getAt(event.getBlock());
        if (teleportal != null) {

            // deactivate the teleportal if it was activated...
            if (teleportal.isActive()) {
                teleportal.deactivate(this);
            }

            // remove the teleportal from data...

        }

    }

    /**
     * Handle players using teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {

    }
}
