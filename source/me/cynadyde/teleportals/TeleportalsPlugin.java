package me.cynadyde.teleportals;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Main class of the Teleportals plugin.
 */
@SuppressWarnings({ "WeakerAccess" })
public class TeleportalsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabExecutor {

    public final NamespacedKey teleportalKey = new NamespacedKey(this, "teleportal");
    public final NamespacedKey gatewayPrismKey = new NamespacedKey(this, "gateway_prism");

    private final File dataFile = new File(getDataFolder(), "metadata.yml");
    private YamlConfiguration dataYaml = new YamlConfiguration();

    private final Map<UUID, Long> interactCooldown = new HashMap<>();

    /**
     * The plugin enabled.
     */
    @Override
    public void onEnable() {

        // set up plugin configuration file...
        saveDefaultConfig();
        reloadConfig();

        getConfig().options().copyDefaults(true);

        // set up the metadata file...
        reloadDataYaml();

        // set up the auto-saving feature...
        int interval = Math.max(60, getConfig().getInt("autosave.interval")) * 20;  // convert seconds to ticks
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::saveDataYaml, interval, interval);

        // create and add the plugin recipes...
        Recipe recipe;
        {
            // create the result item...
            int amount = getConfig().getInt("gateway-prism.amount");

            if (amount < 1) {
                getLogger().warning(String.format(
                        "[Config] The amount '%d' at '%s' is invalid.",
                        amount, "gateway-prism.amount"
                ));
                getLogger().warning("[Config] Using the default value: '1'.");

                amount = 1;
            }
            ItemStack item = makeGatewayPrism(amount);

            // try to load the recipe in the configuration file...
            try {
                recipe = Utils.createRecipe(gatewayPrismKey, item, getConfig(), "gateway-prism");
            }
            catch (Exception ex) {

                getLogger().warning(String.format(
                        "[Config] The Gateway Prism recipe defined in the config was malformed: %s",
                        ex.getMessage()
                ));
                getLogger().warning("[Config] Using the default recipe instead.");

                recipe = new ShapedRecipe(gatewayPrismKey, item);
                ((ShapedRecipe) recipe).shape("OwO", "*u*", "-v-");

                ((ShapedRecipe) recipe).setIngredient('O', Material.ENDER_EYE);
                ((ShapedRecipe) recipe).setIngredient('*', Material.SHULKER_SHELL);
                ((ShapedRecipe) recipe).setIngredient('-', Material.GOLD_INGOT);
                ((ShapedRecipe) recipe).setIngredient('w', Material.DRAGON_HEAD);
                ((ShapedRecipe) recipe).setIngredient('u', Material.END_CRYSTAL);
                ((ShapedRecipe) recipe).setIngredient('v', Material.BEACON);
            }
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
     * The plugin disabled.
     */
    @Override
    public void onDisable() {

        interactCooldown.clear();
    }

    /**
     * Executes the plugin's commands.
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

            // give sub-command given...
            else if (args[0].equalsIgnoreCase("spawnitem")) {

                if (!sender.hasPermission("teleportals.admin.spawn")) {
                    sendMsg(sender, "no-perms-cmd");
                    return false;
                }

                if (!(sender instanceof Player)) {
                    sendMsg(sender, "not-player");
                    return false;
                }

                sendMsg(sender, "spawned-item");
                ((Player) sender).getInventory().addItem(makeGatewayPrism(1));
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
     * Tab completes the plugin's commands.
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {

        List<String> results = new ArrayList<>();

        // the main plugin command...
        if (alias.equalsIgnoreCase("teleportals")) {

            // no sub-commands specified...
            if (args.length == 1) {

                if (sender.hasPermission("teleportals.admin.spawn")) {
                    if ("spawnitem".startsWith(args[0])) {
                        results.add("spawnitem");
                    }
                }

                if (sender.hasPermission("teleportals.admin.reload")) {
                    if ("reloadconfig".startsWith(args[0])) {
                        results.add("reloadconfig");
                    }
                }
            }
        }
        return results;
    }

    /**
     * Blocks players from crafting gateway prisms if they lack permission.
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
     * Handles the activation of teleportals.
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

                    if ((System.currentTimeMillis() - lastInteract) >= 500L) {
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

                            // make sure any limits for this player haven't been reached...
                            Integer limit = getMaxActivePortalLimit(event.getPlayer());
                            if (limit != null && (getActivePortalCount(event.getPlayer()) >= limit)) {
                                sendMsg(event.getPlayer(), "active-portal-limit", limit);
                                return;
                            }

                            // activate the teleportal...
                            if (teleportal.activate(usedItem)) {

                                augActivePortalCount(event.getPlayer(), 1);

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
     * Handles destruction of teleportals.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {

        // if a teleportal structure is about to be broken...
        Teleportal teleportal = Teleportal.getFromStruct(this, event.getBlock());

        if (teleportal != null) {

            // deactivate the teleportal...
            teleportal.deactivate(getConfig().getBoolean("gateway-prism.reusable", true));

            augActivePortalCount(event.getPlayer(), -1);
        }
    }

    /**
     * Handles players ender-pearling into teleportals.
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
     * Reloads metadata from file, if any enabled.
     */
    public void reloadDataYaml() {

        // if no meta data options were enabled, just return an empty data yaml...

        if (!anyMetadataEnabled()) {
            dataYaml = new YamlConfiguration();
            return;
        }

        // else, load meta data from file...

        if (!dataFile.exists()) {
            try {
                Files.createFile(dataFile.toPath());
            }
            catch (IOException ex) {
                getLogger().severe("Unable to create the metadata.yml file: " + ex.getMessage());
                getLogger().severe("Plugin will have reduced functionality.");
            }
        }
        dataYaml = YamlConfiguration.loadConfiguration(dataFile);

        // only keep sections that are enabled...

        if (!getConfig().getBoolean("metadata.track-active-portal-counts", false)) {
            dataYaml.set("player-active-portal-counts", null);
        }
    }

    /**
     * Saves collected metadata to file, if any enabled.
     */
    public void saveDataYaml() {

        getLogger().info("[DEBUG] saved data yaml!");

        if (anyMetadataEnabled()) {
            try {
                dataYaml.save(dataFile);
            }
            catch (IOException ex) {
                getLogger().severe("Unable to save to the metadata.yml file: " + ex.getMessage());
                getLogger().severe("Plugin will have reduced functionality.");
            }
        }
    }

    /**
     * Tests if any metadata options are enabled in the config.
     */
    public boolean anyMetadataEnabled() {

        boolean result = false;
        ConfigurationSection ymlMetadata = getConfig().getConfigurationSection("metadata");
        if (ymlMetadata != null) {
            for (String key : ymlMetadata.getKeys(false)) {
                getLogger().info(Utils.format("[DEBUG] %s is %b", key, ymlMetadata.getBoolean(key)));
                if (ymlMetadata.getBoolean(key)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Gets the plugin's chat tag.
     */
    public @NotNull String getTag() {
        String tag = getConfig().getString("messages.tag");
        return (tag == null) ? "" : Utils.format(tag);
    }

    /**
     * Gets the player's active teleportal limit if they have one, else null.
     * If track-active-portal-counts is not enabled in the config, returns null.
     */
    public Integer getMaxActivePortalLimit(Player player) {

        if (!getConfig().getBoolean("metadata.track-active-portal-counts")) {
            return null;
        }
        ConfigurationSection ymlGroups = getConfig().getConfigurationSection("groups");
        if (ymlGroups != null) {

            Set<String> groups = ymlGroups.getKeys(false);
            for (String group : groups) {

                if (player.hasPermission("teleportals.group." + group)) {
                    String key = group + ".max-active-portals";
                    return ymlGroups.contains(key) ? ymlGroups.getInt(key) : null;
                }
            }
        }
        return null;
    }

    /**
     * Gets the player's active teleportal count.
     * If track-active-portal-counts is not enabled in the config, returns 0.
     */
    public int getActivePortalCount(Player player) {

        if (!getConfig().getBoolean("metadata.track-active-portal-counts")) {
            return 0;
        }
        ConfigurationSection ymlPlayerActivePortalCounts =
                dataYaml.getConfigurationSection("player-active-portal-counts");

        if (ymlPlayerActivePortalCounts == null) {
            dataYaml.createSection("player-active-portal-counts");
            return 0;
        }
        String key = player.getUniqueId().toString();
        if (ymlPlayerActivePortalCounts.contains(key)) {
            return ymlPlayerActivePortalCounts.getInt(key);
        }
        return 0;
    }

    /**
     * Augments the player's active teleportal count by the specified amount.
     * If track-active-portal-counts is not enabled in the config, does nothing.
     */
    public void augActivePortalCount(Player player, int amount) {

        if (!getConfig().getBoolean("metadata.track-active-portal-counts")) {
            return;
        }
        ConfigurationSection ymlPlayerActivePortalCounts =
                dataYaml.getConfigurationSection("player-active-portal-counts");

        if (ymlPlayerActivePortalCounts == null) {
            dataYaml.createSection("player-active-portal-counts");
            return;
        }
        String key = player.getUniqueId().toString();
        int total = amount;

        if (ymlPlayerActivePortalCounts.contains(key)) {
            total += ymlPlayerActivePortalCounts.getInt(key);
        }
        ymlPlayerActivePortalCounts.set(key, Math.max(total, 0));
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

    /**
     * Create a new gateway prism item stack.
     */
    public @NotNull ItemStack makeGatewayPrism(int amount) {

        String matName = getConfig().getString("gateway-prism.material");
        Material mat;
        try {
            mat = Material.valueOf(matName);
        }
        catch (IllegalArgumentException ex) {

            getLogger().warning(String.format(
                    "[Config] The material '%s' at '%s' is invalid.",
                    matName, "gateway-prism.material"
            ));
            getLogger().warning("Using the default value: 'GOLDEN_SWORD'.");

            mat = Material.GOLDEN_SWORD;
        }

        ItemStack item = new ItemStack(mat, amount);

        String name = getConfig().getString("gateway-prism.display");
        List<String> lore = getConfig().getStringList("gateway-prism.lore");

        Utils.setDisplayName(item, (name == null) ? null : Utils.format(name));
        Utils.addLore(item, Utils.format("&8&o") + gatewayPrismKey.toString());
        for (String line : lore) {
            Utils.addLore(item, Utils.format(line));
        }

        return item;
    }
}
