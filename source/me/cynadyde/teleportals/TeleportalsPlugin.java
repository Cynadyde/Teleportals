package me.cynadyde.teleportals;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Main class of the Teleportals plugin.
 */
public class TeleportalsPlugin extends JavaPlugin implements Listener {

    private static TeleportalsPlugin instance;

    private final File metadataFile = new File(getDataFolder(), "metadata.yml");
    private final Map<String, NamespacedKey> namespacedKeys = new HashMap<>();
    private final Map<UUID, Long> interactionCooldowns = new HashMap<>();
    private final Set<EnderPearl> launchedPearls = new HashSet<>();

    private PluginCommand pluginCmd;
    private YamlConfiguration metadataYaml;
    private boolean isEndGatewaySolid;

    /**
     * Get the instance of this enabled plugin.
     */
    public static TeleportalsPlugin getInstance() {
        try {
            return Objects.requireNonNull(instance);
            // return (TeleportalsPlugin) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Teleportals"));
        }
        catch (NullPointerException | ClassCastException | IllegalArgumentException ex) {
            throw new IllegalStateException("plugin not yet enabled");
        }
    }

    /**
     * Get a namespaced key for this plugin with the given name.
     */
    public static NamespacedKey getKey(String name) {

        TeleportalsPlugin plugin = getInstance();
        NamespacedKey key = plugin.namespacedKeys.get(name);
        if (key == null) {
            plugin.namespacedKeys.put(name, key = new NamespacedKey(plugin, name));
        }
        return key;
    }

    /**
     * Reloads the plugin's configuration settings, schedules the
     * metadata autosaver if enabled, and creates the gateway prism recipe.
     */
    public void refresh() {

        saveDefaultConfig();
        reloadConfig();

        getServer().getScheduler().cancelTasks(this);
        getServer().getScheduler().runTaskTimer(this, this::trackEnderpearls, 0L, 1L);

        if (getConfig().getBoolean("metadata.autosave.enabled")) {
            int interval = Math.max(60, getConfig().getInt("metadata.autosave.interval")) * 20;
            getServer().getScheduler().scheduleSyncRepeatingTask(this, this::saveDataYaml, interval, interval);
        }

        Recipe recipe;
        {
            int amount = Math.max(1, getConfig().getInt("gateway-prism.amount"));
            ItemStack item = makeGatewayPrism(amount);

            try {
                recipe = Utils.createRecipe(TeleportalsPlugin.getKey("gateway_prism"), item, getConfig(), "gateway-prism");
            }
            catch (Exception ex) {
                getLogger().warning(String.format("[Config] The Gateway Prism recipe defined in the config was malformed: %s", ex.getMessage()));
                getLogger().warning("[Config] Using the default recipe instead.");

                recipe = new ShapedRecipe(TeleportalsPlugin.getKey("gateway_prism"), item);
                ((ShapedRecipe) recipe).shape("OwO", "*u*", "-v-");
                ((ShapedRecipe) recipe).setIngredient('O', Material.ENDER_EYE);
                ((ShapedRecipe) recipe).setIngredient('*', Material.SHULKER_SHELL);
                ((ShapedRecipe) recipe).setIngredient('-', Material.GOLD_INGOT);
                ((ShapedRecipe) recipe).setIngredient('w', Material.DRAGON_HEAD);
                ((ShapedRecipe) recipe).setIngredient('u', Material.END_CRYSTAL);
                ((ShapedRecipe) recipe).setIngredient('v', Material.BEACON);
            }
        }
        getServer().removeRecipe(TeleportalsPlugin.getKey("gateway-prism"));
        getServer().addRecipe(recipe);
    }

    @Override
    public void onEnable() {

        instance = this;

        isEndGatewaySolid = false;
        try {
            String[] versionParts = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3].split("_");
            isEndGatewaySolid = (versionParts[0].equals("v1") && Integer.parseInt(versionParts[1]) < 16);
        }
        catch (IndexOutOfBoundsException | NumberFormatException ignored) {
        }

        pluginCmd = getCommand("teleportals");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(this);
            pluginCmd.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        refresh();
        reloadDataYaml();
    }

    @Override
    public void onDisable() {

        getServer().removeRecipe(TeleportalsPlugin.getKey("gateway_prism"));
        getServer().getScheduler().cancelTasks(this);

        saveDataYaml();
        interactionCooldowns.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        if (command.equals(pluginCmd)) {
            if (args.length == 0) {
                sendInfo(sender);
                return true;
            }
            // give sub-command...
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
            // reload sub-command...
            else if (args[0].equalsIgnoreCase("reloadconfig")) {
                if (!sender.hasPermission("teleportals.admin.reload")) {
                    sendMsg(sender, "no-perms-cmd");
                    return false;
                }
                sendMsg(sender, "config-reloaded", sender.getName());
                refresh();
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        List<String> results = new ArrayList<>();
        if (command.equals(pluginCmd)) {
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
     * Prevent a player from crafting the gateway prism if they lack permission.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareCraftItem(@NotNull PrepareItemCraftEvent event) {

        if (event.getRecipe() instanceof ShapedRecipe) {
            if (((ShapedRecipe) event.getRecipe()).getKey().equals(TeleportalsPlugin.getKey("gateway_prism"))) {
                if (!event.getView().getPlayer().hasPermission("teleportals.player.craft")) {

                    sendMsg(event.getView().getPlayer(), "no-perms-craft");
                    event.getInventory().setResult(null);
                }
            }
        }
    }

    /**
     * Handle the potential activation of a teleportal or linking of a gateway prism.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {

        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Block block = event.getClickedBlock();
            if (block != null && (block.getType() == Material.ENDER_CHEST || block.getType() == Material.END_GATEWAY)) {

                // make sure the cooldown for this player is over...
                if (!interactionCooldowns.containsKey(event.getPlayer().getUniqueId())) {
                    interactionCooldowns.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
                }
                else {
                    Long lastInteract = interactionCooldowns.get(event.getPlayer().getUniqueId());
                    if ((System.currentTimeMillis() - lastInteract) >= 500L) {
                        interactionCooldowns.remove(event.getPlayer().getUniqueId());
                    }
                    else {
                        return;
                    }
                }
                ItemStack usedItem = event.getPlayer().getInventory().getItemInMainHand();
                if (Utils.hasLoreTag(usedItem, TeleportalsPlugin.getKey("gateway_prism").toString())) {

                    Teleportal teleportal = Teleportal.getFromStruct(block);
                    if (teleportal != null) {

                        if (event.getPlayer().isSneaking()) {
                            if (!event.getPlayer().hasPermission("teleportals.player.link")) {
                                sendMsg(event.getPlayer(), "no-perms-link");
                                return;
                            }
                            teleportal.linkGatewayPrism(usedItem);
                        }
                        else {
                            // make sure the player can activate a teleportal
                            if (!event.getPlayer().hasPermission("teleportals.player.activate")) {
                                sendMsg(event.getPlayer(), "no-perms-activate");
                                return;
                            }
                            if (!isWorldOkayForPortalActivation(event.getPlayer(), event.getPlayer().getWorld())) {
                                sendMsg(event.getPlayer(), "no-perms-activate");
                                return;
                            }
                            Integer limit = getMaxActivePortalLimit(event.getPlayer());
                            if (limit != null && (getActivePortalCount(event.getPlayer().getUniqueId()) >= limit)) {
                                sendMsg(event.getPlayer(), "active-portal-limit", limit);
                                return;
                            }
                            if (!isCrossWorldActivationAllowed()) {
                                String exitKey = Utils.getLoreData(usedItem, "link");
                                Block exit = Utils.keyToBlock(exitKey);
                                if (exit != null) {
                                    if (!block.getWorld().equals(exit.getWorld())) {
                                        sendMsg(event.getPlayer(), "cant-cross-worlds",
                                                block.getWorld().getName(),
                                                exit.getWorld().getName());
                                        return;
                                    }
                                }
                            }

                            if (teleportal.activate(usedItem)) {
                                augActivePortalCount(event.getPlayer().getUniqueId(), 1);
                                if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                                    usedItem.setAmount(usedItem.getAmount() - 1);
                                }
                                teleportal.setPortalName(event.getPlayer().getUniqueId().toString());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle the potential destruction of a teleportal.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {

        Teleportal teleportal = Teleportal.getFromStruct(event.getBlock());
        if (teleportal != null) {
            teleportal.deactivate(getConfig().getBoolean("gateway-prism.reusable", true));

            String name = teleportal.getPortalName();
            if (name != null) {
                augActivePortalCount(UUID.fromString(name), -1);
            }
        }
    }

    /**
     * Allow players to discover the gateway prism recipe by using an end gateway.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            event.getPlayer().discoverRecipe(TeleportalsPlugin.getKey("gateway_prism"));
        }
    }

    /**
     * Track ender pearls potentially entering a teleportal's end gateway.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {

        if (!isEndGatewaySolid) {
            if (event.getEntity().getType() == EntityType.ENDER_PEARL) {
                EnderPearl pearl = (EnderPearl) event.getEntity();
                if (pearl.getShooter() instanceof Entity) {
                    launchedPearls.add(pearl);
                }
            }
        }
    }

    /**
     * Handle a player potentially ender-pearling into a teleportal.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHitEvent(@NotNull ProjectileHitEvent event) {

        if (event.getEntity() instanceof EnderPearl) {

            EnderPearl pearl = (EnderPearl) event.getEntity();
            if (pearl.getShooter() instanceof Entity) {
                Entity shooter = (Entity) pearl.getShooter();

                Block block = event.getHitBlock();
                if (block != null && block.getType() == Material.END_GATEWAY && event.getHitBlockFace() != null) {

                    Teleportal teleportal = Teleportal.getFromStruct(block);
                    if (teleportal != null) {
                        if (shooter instanceof Player) {
                            if (!shooter.hasPermission("teleportals.player.use")) {
                                sendMsg(shooter, "no-perms-use");
                                return;
                            }
                        }
                        teleportal.teleport(shooter, event.getHitBlockFace());
                    }
                }
            }
        }
    }

    /**
     * Manually fire a projectile collision event when an ender pearl collides with an end gateway.
     */
    public void trackEnderpearls() {

        for (EnderPearl pearl : launchedPearls) {
            if (pearl.isValid()) {
                Block hitBlock;

                findCollision:
                {
                    Vector start = pearl.getLocation().toVector();
                    Vector finish = pearl.getVelocity().add(start);

                    if (start.toBlockVector().equals(finish.toBlockVector())) {
                        Block block = pearl.getLocation().getBlock();
                        if (block.getType() == Material.END_GATEWAY) {
                            hitBlock = block;
                            break findCollision;
                        }
                    }
                    else {
                        BlockIterator ray = new BlockIterator(
                                pearl.getWorld(),
                                start,
                                pearl.getVelocity().normalize(),
                                0,
                                (int) Math.ceil(pearl.getVelocity().length()));
                        while (ray.hasNext()) {
                            Block block = ray.next();
                            if (block.getType() == Material.END_GATEWAY) {
                                hitBlock = block;
                                break findCollision;
                            }
                        }
                    }
                    continue;
                }
                float yaw = 0 - pearl.getLocation().getYaw();
                BlockFace hitFace = Utils.yawToBlockFace(yaw).getOppositeFace();
                // TODO find collided face instead of using the ender pearl's yaw

                ProjectileHitEvent event = new ProjectileHitEvent(pearl, null, hitBlock, hitFace);
                getServer().getPluginManager().callEvent(event);
                getServer().getScheduler().runTaskLater(this, pearl::remove, 2L);
            }
            launchedPearls.remove(pearl);
        }
    }

    /**
     * Reload any collected metadata from file.
     */
    public void reloadDataYaml() {

        if (!anyMetadataEnabled()) {
            metadataYaml = new YamlConfiguration();
            return;
        }
        if (!metadataFile.exists()) {
            try {
                Files.createFile(metadataFile.toPath());
            }
            catch (IOException ex) {
                getLogger().severe("Unable to create the metadata.yml file: " + ex.getMessage());
            }
        }
        metadataYaml = YamlConfiguration.loadConfiguration(metadataFile);

        if (!getConfig().getBoolean("metadata.track-active-portal-counts", false)) {
            metadataYaml.set("player-active-portal-counts", null);
        }
    }

    /**
     * Save metadata to file if any was collected, else remove the file if it exists.
     */
    public void saveDataYaml() {

        if (anyMetadataEnabled()) {
            try {
                metadataYaml.save(metadataFile);
            }
            catch (IOException ex) {
                getLogger().severe("Unable to save to the metadata.yml file: " + ex.getMessage());
            }
        }
        else {
            try {
                Files.deleteIfExists(metadataFile.toPath());
            }
            catch (IOException ex) {
                getLogger().warning("Unable to remove the metadata.yml file: " + ex.getMessage());
            }
        }
    }

    /**
     * Test if any metadata options are enabled in the config.
     */
    public boolean anyMetadataEnabled() {

        boolean result = false;
        ConfigurationSection ymlMetadata = getConfig().getConfigurationSection("metadata");
        if (ymlMetadata != null) {
            for (String key : ymlMetadata.getKeys(false)) {
                if (ymlMetadata.isBoolean(key) && ymlMetadata.getBoolean(key)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Get the plugin's chat tag.
     */
    public @NotNull String getTag() {

        String tag = getConfig().getString("messages.tag");
        return (tag == null) ? "" : Utils.format(tag);
    }

    /**
     * Get the player's active teleportal limit if they have one, else null.
     * If track-active-portal-counts is not enabled in the config, this returns null.
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
     * Get the player with the given UUID's active teleportal count.
     * If track-active-portal-counts is not enabled in the config, this returns 0.
     */
    public int getActivePortalCount(UUID uuid) {

        if (!getConfig().getBoolean("metadata.track-active-portal-counts")) {
            return 0;
        }
        ConfigurationSection ymlPlayerActivePortalCounts =
                metadataYaml.getConfigurationSection("player-active-portal-counts");

        if (ymlPlayerActivePortalCounts == null) {
            metadataYaml.createSection("player-active-portal-counts");
            return 0;
        }
        String key = uuid.toString();
        if (ymlPlayerActivePortalCounts.contains(key)) {
            return ymlPlayerActivePortalCounts.getInt(key);
        }
        return 0;
    }

    /**
     * Augment the player's active teleportal count by the specified amount.
     * If track-active-portal-counts is not enabled in the config, this does nothing.
     */
    public void augActivePortalCount(UUID uuid, int amount) {

        if (!getConfig().getBoolean("metadata.track-active-portal-counts")) {
            return;
        }
        ConfigurationSection ymlPlayerActivePortalCounts =
                metadataYaml.getConfigurationSection("player-active-portal-counts");

        if (ymlPlayerActivePortalCounts == null) {
            metadataYaml.createSection("player-active-portal-counts");
            return;
        }
        String key = uuid.toString();
        int total = amount;

        if (ymlPlayerActivePortalCounts.contains(key)) {
            total += ymlPlayerActivePortalCounts.getInt(key);
        }
        ymlPlayerActivePortalCounts.set(key, Math.max(total, 0));
    }

    /**
     * Test if players are allowed to link teleportals across different worlds.
     */
    public boolean isCrossWorldActivationAllowed() {
        return getConfig().getBoolean("teleportals.cross-world", true);
    }

    /**
     * Test if the player can activate a teleportal in the given world.
     */
    public boolean isWorldOkayForPortalActivation(Player player, World world) {
        ConfigurationSection ymlGroups = getConfig().getConfigurationSection("groups");
        if (ymlGroups != null) {

            Set<String> groups = ymlGroups.getKeys(false);
            for (String group : groups) {

                if (player.hasPermission("teleportals.group." + group)) {
                    List<String> whitelist = ymlGroups.getStringList(group + ".worlds-can-activate");
                    if (!whitelist.isEmpty()) {
                        return whitelist.contains(world.getName());
                    }
                    else {
                        List<String> blacklist = ymlGroups.getStringList(group + ".worlds-cannot-activate");
                        if (!blacklist.isEmpty()) {
                            return !blacklist.contains(world.getName());
                        }

                    }
                }
            }
        }
        return true;
    }

    /**
     * Send a message from the plugin using the given key and formatter objects.
     * If the key does not exist, it will be used as a message template itself.
     */
    public void sendMsg(CommandSender sender, @NotNull String key, @Nullable Object... objs) {

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
            getLogger().warning(String.format("[Config] The material '%s' at '%s' is invalid.", matName, "gateway-prism.material"));
            getLogger().warning("Using the default value: 'GOLDEN_SWORD'.");
            mat = Material.GOLDEN_SWORD;
        }

        ItemStack item = new ItemStack(mat, amount);

        String name = getConfig().getString("gateway-prism.display");
        List<String> lore = getConfig().getStringList("gateway-prism.lore");

        Utils.setDisplayName(item, (name == null) ? null : Utils.format(name));
        Utils.addLore(item, Utils.format("&8&o") + TeleportalsPlugin.getKey("gateway_prism").toString());
        for (String line : lore) {
            Utils.addLore(item, Utils.format(line));
        }
        return item;
    }
}
