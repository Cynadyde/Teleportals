package me.cynadyde.teleportals;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

/**
 * Main class of the Teleportals Plugin.
 */
@SuppressWarnings("WeakerAccess")
public class TeleportalsPlugin extends JavaPlugin implements Listener {

    public static boolean isUnactivatedTeleportal(Block block) {

        return block != null
                && block.getType().equals(Material.ENDER_CHEST)
                && block.getRelative(BlockFace.UP).getType().equals(Material.OBSIDIAN)
                && (block.getRelative(BlockFace.DOWN).getType().equals(Material.OBSIDIAN));
    }

    @Override
    public void onEnable() {

        // Register plugin to listen to events...
        getServer().getPluginManager().registerEvents(this, this);

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
    }

    @Override
    public void onDisable() {

    }

    /**
     * Set the subspace link of the gateway prism using the recipe's enchanted book.
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
     *
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
                    teleportal.activate(gatewayPrism.getSubspaceLink());
                    event.getPlayer().getInventory().setItemInMainHand(null);
                }
            }
        }
    }

    /**
     *
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

    }

    /**
     *
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {

    }
}
