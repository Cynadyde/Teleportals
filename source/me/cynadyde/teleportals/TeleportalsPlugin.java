package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
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
            ItemStack item = new ItemStack(Material.GOLDEN_SWORD, 4);

            String name = getConfig().getString("gateway-prism-display");
            Utils.setDisplayName(item, (name == null) ? null : Utils.format(name));
            Utils.addLoreTag(item, Utils.format("&8&o") + gatewayPrismKey.toString());

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
                    sendMsg(sender, "no-perms");
                    return false;
                }
                sendMsg(sender, "config-reloaded", sender.getName());
                reloadConfig();
                return true;
            }

            // unknown sub-command given...
            else {
                String cmd = "/" + alias + String.join(" ", args);
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
            if (args.length == 0) {

                if (sender.hasPermission("teleportals.admin.reload")) {
                    results.add("reloadconfig");
                }
            }
        }
        return results;
    }

    /**
     * Handle the activation of teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {

        // if the player right clicks a block...
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {

            // if the player was using a gateway prism...
            ItemStack usedItem = event.getPlayer().getInventory().getItemInMainHand();

            getLogger().info(Utils.format("&9Right click interaction caught with item: %s", usedItem));

            if (Utils.hasLoreTag(usedItem, gatewayPrismKey.toString())) {

                getLogger().info(Utils.format(" > Item has the lore tag '%s'", gatewayPrismKey.toString()));

                // if the clicked block was an ender chest...
                Block block = event.getClickedBlock();
                if (block != null && (block.getType() == Material.ENDER_CHEST
                        || block.getType() == Material.END_GATEWAY)) {

                    getLogger().info(Utils.format(" > An ender chest was clicked."));

                    // if the clicked block was part of a teleportal structure...
                    Teleportal teleportal = Teleportal.getFromStruct(this, block);
                    if (teleportal != null) {

                        getLogger().info(Utils.format(" > A teleportal was found!"));

                        // cancel the interaction event...
                        event.setCancelled(true);

                        // if the gateway prism is unlinked...
                        if (!Utils.hasLoreData(usedItem, "link")) {

                            teleportal.linkGatewayPrism(usedItem);

                            getLogger().info(Utils.format(" > The gateway prism was linked to this teleportal!"));
                        }
                        // else...
                        else {

                            // activate the teleportal...
                            teleportal.activate(usedItem);

                            // consume the gateway prism...
                            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                                usedItem.setAmount(usedItem.getAmount() - 1);
                            }

                            getLogger().info(Utils.format(" > The gateway prism was used to activate the teleportal!"));
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

        // if a teleportal or teleportal's frame was broken...
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
    @SuppressWarnings("SpellCheckingInspection")
    public void sendInfo(@NotNull CommandSender sender) {

        List<String> lines = new ArrayList<>();

        lines.add(Utils.format("&6---==-&f[&e %s &f]&6-==---", getDescription().getName()));
        lines.add(Utils.format("&aversion: &b%s", getDescription().getVersion()));
        lines.add(Utils.format("&aauthors: &b%s", String.join(", ", getDescription().getAuthors())));
        lines.add(Utils.format("&adescription: &b%s", getDescription().getDescription()));
        lines.add(Utils.format("&awebsite: &b%s", getDescription().getWebsite()));
        lines.add(Utils.format("&7------------------------"));
        lines.add(Utils.format("&a/teleportals &b- display plugin help and information."));
        lines.add(Utils.format("&a/teleportals reloadconfig &b- reload the plugin's configuration file."));

        sender.sendMessage(lines.toArray(new String[0]));
    }
}
