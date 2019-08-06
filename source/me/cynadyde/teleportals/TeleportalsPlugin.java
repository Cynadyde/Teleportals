package me.cynadyde.teleportals;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Main class of the Teleportals plugin.
 */
@SuppressWarnings({ "WeakerAccess" })
public class TeleportalsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabExecutor {

    public final NamespacedKey teleportalKey = new NamespacedKey(this, "teleportal");
    public final NamespacedKey gatewayPrismKey = new NamespacedKey(this, "gateway_prism");

    private final Map<UUID,Long> interactCooldown = new HashMap<>();

    /**
     * The plugin is enabled.
     */
    @Override
    public void onEnable() {

        // set up plugin configuration file...
        saveDefaultConfig();
        reloadConfig();

        getConfig().options().copyDefaults(true);

        // create and add the plugin recipes...
        ShapedRecipe recipe;
        {
            ItemStack item = new ItemStack(Material.GOLDEN_SWORD, getConfig().getInt("gateway-prism.amount"));

            String name = getConfig().getString("gateway-prism.display");
            List<String> lore = getConfig().getStringList("gateway-prism.lore");

            Utils.setDisplayName(item, (name == null) ? null : Utils.format(name));
            Utils.addLore(item, Utils.format("&8&o") + gatewayPrismKey.toString());
            for (String line : lore) {
                Utils.addLore(item, Utils.format(line));
            }

            recipe = new ShapedRecipe(gatewayPrismKey, item);
            recipe.shape("OwO", "*#*", "-v-");

            recipe.setIngredient('O', Material.ENDER_EYE);
            recipe.setIngredient('*', Material.SHULKER_SHELL);
            recipe.setIngredient('-', Material.GOLD_INGOT);
            recipe.setIngredient('w', Material.DRAGON_HEAD);
            recipe.setIngredient('#', Material.END_CRYSTAL);
            recipe.setIngredient('v', Material.BEACON);
        }
        getServer().addRecipe(recipe);

        // set up the plugin's commands...
        PluginCommand pluginCmd = getCommand("teleportals");
        if (pluginCmd != null) {

            pluginCmd.setExecutor(this);
            pluginCmd.setTabCompleter(this);
        }

        // set up the plugin's listeners...
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * The plugin is disabled.
     */
    @Override
    public void onDisable() {

        interactCooldown.clear();
    }

    /**
     * Execute the plugin's commands.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String[] args) {

        // the main plugin command...
        if (alias.equalsIgnoreCase("teleportals")) {

            // no arguments given...
            if (args.length == 0) {

                sendInfo(sender);
                return true;
            }

            // reload sub-command given...
            else if (args[0].equalsIgnoreCase("reloadconfig")) {

                if (!sender.hasPermission("teleportals.admin.reload")) {
                    sendMsg(sender, "no-perms-cmd");
                    return false;
                }
                sendMsg(sender, "config-reloaded", sender.getName());
                saveDefaultConfig();
                reloadConfig();
                getConfig().options().copyDefaults(true);
                return true;
            }

            // unknown sub-command given...
            else {
                String cmd = "/" + alias + " " + String.join(" ", args);
                sendMsg(sender, "unknown-cmd", cmd);
                return false;
            }
        }
        return false;
    }

    /**
     * Tab complete the plugin's commands.
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {

        List<String> results = new ArrayList<>();

        // the main plugin command...
        if (alias.equalsIgnoreCase("teleportals")) {

            // no sub-commands specified...
            if (args.length == 1) {

                if (sender.hasPermission("teleportals.admin.reload")) {
                    results.add("reloadconfig");
                }
            }
        }
        return results;
    }

    /**
     * Block the player from crafting a gateway prism if they lack permission.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareCraftItem(@NotNull PrepareItemCraftEvent event) {

        // if the recipe is a shaped recipe...
        if (event.getRecipe() instanceof ShapedRecipe) {

            // if the recipe is the gateway prism recipe...
            if (((ShapedRecipe) event.getRecipe()).getKey().equals(gatewayPrismKey)) {

                // if the crafter lacks permission to craft the gateway prism...
                if (!event.getView().getPlayer().hasPermission("teleportals.player.craft")) {

                    // fail their crafting...
                    sendMsg(event.getView().getPlayer(), "no-perms-craft");
                    event.getInventory().setResult(null);
                }
            }
        }
    }

    /**
     * Handle the activation of teleportals.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {

        // if the player right clicks a block...
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {

            // if the clicked block was the same material as a teleportal...
            Block block = event.getClickedBlock();
            if (block != null && (block.getType() == Material.ENDER_CHEST
                    || block.getType() == Material.END_GATEWAY)) {

                // make sure the cooldown for this player is over...
                if (!interactCooldown.containsKey(event.getPlayer().getUniqueId())) {
                    interactCooldown.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
                }
                else {
                    Long lastInteract = interactCooldown.get(event.getPlayer().getUniqueId());

                    if ((System.currentTimeMillis() - lastInteract) >= 1000L) {
                        interactCooldown.remove(event.getPlayer().getUniqueId());
                    }
                    else {
                        return;
                    }
                }

                // if the player was using a gateway prism...
                ItemStack usedItem = event.getPlayer().getInventory().getItemInMainHand();

                if (Utils.hasLoreTag(usedItem, gatewayPrismKey.toString())) {

                    // if the clicked block was part of a teleportal structure...
                    Teleportal teleportal = Teleportal.getFromStruct(this, block);
                    if (teleportal != null) {

                        // if the player is sneaking...
                        if (event.getPlayer().isSneaking()) {

                            // make sure the player has permission to link a gateway prism...
                            if (!event.getPlayer().hasPermission("teleportals.player.link")) {
                                sendMsg(event.getPlayer(), "no-perms-link");
                                return;
                            }
                            // link the gateway prism to this teleportal...
                            teleportal.linkGatewayPrism(usedItem);
                        }
                        // else...
                        else {

                            // make sure the player has permission to activate a teleportal...
                            if (!event.getPlayer().hasPermission("teleportals.player.activate")) {
                                sendMsg(event.getPlayer(), "no-perms-activate");
                                return;
                            }

                            // activate the teleportal...
                            if (teleportal.activate(usedItem)) {

                                // consume the gateway prism...
                                if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                                    usedItem.setAmount(usedItem.getAmount() - 1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle destruction of teleportals.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {

        // if a teleportal structure is about to be broken...
        Teleportal teleportal = Teleportal.getFromStruct(this, event.getBlock());

        if (teleportal != null) {

            // deactivate the teleportal...
            teleportal.deactivate();
        }
    }

    /**
     * Handle player ender-pearling into teleportal.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHitEvent(@NotNull ProjectileHitEvent event) {

        // if the projectile entity was an ender pearl...
        if (event.getEntity() instanceof EnderPearl) {

            EnderPearl enderPearl = (EnderPearl) event.getEntity();

            // if its shooter was an entity that can be teleported...
            if (enderPearl.getShooter() instanceof Entity) {

                Entity shooter = (Entity) enderPearl.getShooter();

                // if the ender pearl hit an end-gateway block...
                Block block = event.getHitBlock();
                if (block != null && block.getType() == Material.END_GATEWAY
                        && event.getHitBlockFace() != null) {

                    // if the end-gateway was a teleportal...
                    Teleportal teleportal = Teleportal.getFromStruct(this, block);
                    if (teleportal != null) {

                        if (shooter instanceof Player) {

                            // make sure the player has permission to use a teleportal...
                            if (!shooter.hasPermission("teleportals.player.use")) {
                                sendMsg(shooter, "no-perms-use");
                                return;
                            }
                        }

                        // teleport the entity...
                        teleportal.teleport(shooter, event.getHitBlockFace());
                    }
                }
            }
        }
    }

    /**
     * Get the plugin's chat tag.
     */
    public @NotNull String getTag() {
        String tag = getConfig().getString("messages.tag");
        return (tag == null) ? "" : Utils.format(tag);
    }

    /**
     * Send a message from the plugin using the given key and formatter objects.
     * If the key does not exist, it will be used as a message template itself.
     */
    public void sendMsg(CommandSender sender, @NotNull String key, Object... objs) {

        String template = getConfig().getString("messages." + key);
        sender.sendMessage(getTag() + Utils.format((template == null) ? key : template, objs));
    }

    /**
     * Send plugin info to the given sender.
     */
    public void sendInfo(@NotNull CommandSender sender) {

        PluginDescriptionFile info = getDescription();

        sendMsg(sender, "plugin-info",
                info.getName(),
                info.getVersion(),
                String.join(", ", info.getAuthors()),
                info.getDescription(),
                info.getWebsite()
        );
    }
}
