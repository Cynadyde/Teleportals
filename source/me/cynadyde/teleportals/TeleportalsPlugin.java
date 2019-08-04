package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.EndGateway;
import org.bukkit.block.data.Directional;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Main class of the Teleportals plugin.
 */
@SuppressWarnings("WeakerAccess")
public class TeleportalsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabExecutor {

    private File subspacesFile = new File(getDataFolder(), "subspaces.yml");
    private YamlConfiguration subspacesYaml = null;

    private NamespacedKey teleportalKey = new NamespacedKey(this, "teleportal");
    private NamespacedKey gatewayPrismKey = new NamespacedKey(this, "gateway_prism");

    /**
     * The plugin is enabled.
     */
    @Override
    public void onEnable() {

        // set up plugin configuration file...
        getConfig().options().copyDefaults(true);

        saveDefaultConfig();
        reloadConfig();

        // load the plugin's subspaces yaml...
        reloadSubspaces();

        // create and add the plugin recipes...
        ShapedRecipe gatewayPrismRecipe = new ShapedRecipe(
                gatewayPrismKey, createGatewayPrism(
                        getConfig().getInt("recipes.gateway_prism.result.amount"),
                        getConfig().getString("recipes.gateway_prism.result.display"),
                        null, null
        ));
        List<String> shape = getConfig().getStringList("recipes.gateway_prism.shape");
        gatewayPrismRecipe.shape(shape.toArray(new String[0]));

        ConfigurationSection section = getConfig().getConfigurationSection("recipes.gateway_prism.key");
        assert section != null;
        for (String chr : section.getKeys(false)) {
            String mat = getConfig().getString("recipes.gateway_prism.key." + chr);
            if (mat != null) {
                try {
                    Material material = Material.valueOf(mat.toUpperCase());
                    gatewayPrismRecipe.setIngredient(chr.charAt(0), material);
                }
                catch (IllegalArgumentException ignored) {

                }
            }
        }

        gatewayPrismRecipe.setIngredient('O', Material.valueOf(getConfig().getString("recipes.gateway_prism.key.O").toLowerCase()) Material.ENDER_EYE);
        gatewayPrismRecipe.setIngredient('*', Material.CONDUIT);
        gatewayPrismRecipe.setIngredient('w', Material.DRAGON_HEAD);
        gatewayPrismRecipe.setIngredient('#', Material.END_CRYSTAL);
        gatewayPrismRecipe.setIngredient('v', Material.BEACON);

        getServer().addRecipe(gatewayPrismRecipe);

        // set up the plugin's commands...
        PluginCommand pluginCmd = Objects.requireNonNull(getCommand("teleportals"));

        pluginCmd.setExecutor(this);
        pluginCmd.setTabCompleter(this);

        // set up the plugin's listeners...
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * The plugin is disabled.
     */
    @Override
    public void onDisable() {

        // save the plugin's subspace yaml...
        saveSubspaces();
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
            else if (args[0].equalsIgnoreCase("reload")) {

                if (!sender.hasPermission("teleportals.admin.reload")) {
                    sender.sendMessage(newMsg("no-perms"));
                    return false;
                }
                sender.sendMessage(newMsg("reloaded", sender.getName()));
                reloadConfig();
                return true;
            }

            // unknown sub-command given...
            else {
                String cmd = "/" + alias + String.join(" ", args);
                sender.sendMessage(newMsg("unknown-cmd", cmd));
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
                    results.add("reload");
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
            if (isGatewayPrism(usedItem)) {

                // if the clicked block was an ender chest...
                Block block = event.getClickedBlock();
                if (block != null && block.getType() == Material.ENDER_CHEST) {

                    // if the clicked block was part of a teleportal structure...
                    Block anchorBlock = findTeleportal(block);
                    if (anchorBlock != null) {

                        // cancel the interaction event...
                        event.setCancelled(true);

                        // activate the teleportal...
                        createTeleportalAt(anchorBlock, usedItem);

                        // consume the gateway prism...
                        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                            usedItem.setAmount(usedItem.getAmount() - 1);
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
        Block anchorBlock = findTeleportal(event.getBlock());

        if (anchorBlock != null) {

            // deactivate the teleportal...
            removeTeleportalAt(anchorBlock);
        }
    }

    /**
     * Handle player ender-pearling into teleportal.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHitEvent(@NotNull ProjectileHitEvent event) {

        // if the projectile entity was an ender pearl...
        if (event.getEntity() instanceof EnderPearl) {

            EnderPearl enderPearl = (EnderPearl) event.getEntity();

            // if the ender pearl hit an end gateway block...
            Block block = event.getHitBlock();
            if (block != null && event.getHitBlockFace() != null
                    && block.getType() == Material.END_GATEWAY) {

                // if the end gateway was a teleportal...
                Block anchorBlock = findTeleportal(block);
                if (anchorBlock != null) {

                    // if the ender pearl shooter was an entity that can be teleported...
                    if (enderPearl.getShooter() instanceof Entity) {

                        Entity shooter = (Entity) enderPearl.getShooter();

                        // teleport the entity...
                        useTeleportal(anchorBlock, event.getHitBlockFace(), shooter);
                    }
                }
            }
        }
    }

    /**
     * Get the plugin's subspace data from file.
     */
    public YamlConfiguration getSubspaces() {

        if (subspacesYaml == null) {
            subspacesYaml = YamlConfiguration.loadConfiguration(subspacesFile);
        }
        return subspacesYaml;
    }

    @Override
    public void reloadConfig() {

        super.reloadConfig();


    }

    /**
     * Reload the plugin's subspace data from file.
     */
    public void reloadSubspaces() {

        subspacesYaml = YamlConfiguration.loadConfiguration(subspacesFile);
    }

    /**
     * Save the plugin's subspace data to file.
     */
    public void saveSubspaces() {

        try {
            getSubspaces().save(subspacesFile);
        }
        catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save data to " + subspacesFile, ex);
        }
    }

    /**
     * Get the plugin's chat tag.
     */
    public @NotNull String getTag() {

        return Objects.requireNonNull(getConfig().getString("messages.tag"));
    }

    /**
     * Create a new plugin message using the given key and formatter objects.
     */
    public @NotNull String newMsg(@NotNull String key, Object...objs) {

        return Utils.format(getTag() + Objects.requireNonNull(getConfig().getString("messages." + key)), objs);
    }

    /**
     * Send plugin info to the given sender.
     */
    public void sendInfo(@NotNull CommandSender sender) {

        // TODO display plugin info
        //      --=[ name ]=--
        //      version: -
        //      authors: -, -, -
        //      website: -
        //      description: - -
        //          - - - - - -
        //      --------
        //      commands
    }

    /**
     * Test if the given item is a gateway prism.
     */
    public boolean isGatewayPrism(@Nullable ItemStack item) {

        // the item is a golden sword...
        if (item == null) {
            return false;
        }
        if (item.getType() != Material.GOLDEN_SWORD) {
            return false;
        }

        // the item has the gateway prism key in its lore...
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        List<String> itemLore = itemMeta.getLore();

        if (itemLore == null || itemLore.isEmpty()) {
            return false;
        }
        for (String line : itemLore) {
            if (ChatColor.stripColor(line).equals(gatewayPrismKey.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a new gateway prism.
     */
    public @NotNull ItemStack createGatewayPrism(int amount, @Nullable String display, @Nullable String[] loreLines,
                                                 @Nullable Map<Enchantment, Integer> enchants) {

        ItemStack item = new ItemStack(Material.GOLDEN_SWORD, amount);

        ItemMeta itemMeta = Objects.requireNonNull(item.getItemMeta());

        itemMeta.setDisplayName(Utils.format((display != null) ? display : "&bGateway Prism"));

        List<String> lore = new ArrayList<>();

        lore.add(Utils.format("&7" + gatewayPrismKey.toString()));
        if (loreLines != null) {
            lore.addAll(Arrays.asList(loreLines));
        }
        itemMeta.setLore(lore);

        if (enchants != null) {
            for (Enchantment ench : enchants.keySet()) {

                itemMeta.addEnchant(ench, enchants.get(ench), true);
            }
        }
        item.setItemMeta(itemMeta);
        return item;
    }

    /**
     * If the given block is part of a structure that matches a teleportal, return that block.
     */
    public @Nullable Block findTeleportal(@NotNull Block block) {

        for (BlockFace dir : new BlockFace[]{BlockFace.SELF, BlockFace.UP, BlockFace.DOWN}) {

            Block relBlock = block.getRelative(dir);

            if (relBlock.getType() == Material.ENDER_CHEST
                    || relBlock.getType() == Material.END_GATEWAY) {

                if (relBlock.getRelative(BlockFace.DOWN).getType() == Material.OBSIDIAN
                        && relBlock.getRelative(BlockFace.UP).getType() == Material.OBSIDIAN) {

                    return relBlock;
                }
            }
        }
        return null;
    }

    /**
     * Get the direction this teleportal is facing. Defaults to NORTH.
     */
    public @NotNull BlockFace getTeleportalFacing(@Nullable Block anchor) {

        if (anchor != null) {

            ArmorStand marker = Utils.getMarker(anchor, teleportalKey.toString());
            if (marker != null) {
                return marker.getFacing();
            }
        }
        return BlockFace.NORTH;
    }

    /**
     * Get the gateway prism this teleportal contains.
     */
    public @Nullable ItemStack getTeleportalGatewayPrism(@Nullable Block anchor) {

        if (anchor != null) {

            ArmorStand marker = Utils.getMarker(anchor, teleportalKey.toString());
            if (marker != null) {
                return marker.getItemInHand();
            }
        }
        return null;
    }

    /**
     * Create a teleportal linked to the given subspace at a block.
     */
    public void createTeleportalAt(Block block, ItemStack gatewayPrism) {

        Location loc = block.getLocation();
        String blockKey = Utils.posToKey(block);
        String subspaceKey = Utils.enchantsToKey(Utils.getEnchants(gatewayPrism));

        List<String> subspaceLinks = getSubspaces().getStringList(subspaceKey);

        // if this block is not already linked to the given subspace...
        if (!subspaceLinks.contains(blockKey)) {

             getLogger().info(String.format("Created Teleportal(%s) at %s(%d, %d, %d)!",
                     subspaceKey, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

            // get the block's facing direction...
            BlockFace facing = (block.getBlockData() instanceof Directional) ?
                    ((Directional) block.getBlockData()).getFacing() : BlockFace.NORTH;

            // create a marker for the teleportal...
            ArmorStand marker = Utils.createMarker(block, teleportalKey.toString(), facing);
            marker.setItemInHand(gatewayPrism);

            // set block to an end gateway...
            block.setType(Material.END_GATEWAY);

            // link the teleportal to that subspace...
            subspaceLinks.add(blockKey);
            getSubspaces().set(subspaceKey, subspaceLinks);

            // TODO use server commands to do effects

            // spawn particle effects and play sfx...
            Location effectsLoc = loc.clone().add(0.5, 0.5, 0.5);
            block.getWorld().spawnParticle(Particle.END_ROD, effectsLoc, 200, 0.1, 0.1, 0.1, 0.10);
            block.getWorld().spawnParticle(Particle.DRAGON_BREATH, effectsLoc, 100, 0.25, 0.25, 0.25, 0.075);
            block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, effectsLoc, 50, 0.1, 0.1, 0.1, 0.025);
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f); // TO DO random pitches
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.5f, 1.5f);  // TO DO ambient sound?
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.5f, 0.5f);
        }
    }

    /**
     * Remove the teleportal at a block.
     */
    public void removeTeleportalAt(Block block) {

        Location loc = block.getLocation();
        String blockKey = Utils.posToKey(block);

        // TODO should still set end gate to ender chest, remove marker even if not in subspaces yaml.

        // for each subspace containing this link...
        for (String subspaceKey : getSubspaces().getKeys(false)) {

            List<String> subspaceLinks = getSubspaces().getStringList(subspaceKey);
            if (subspaceLinks.contains(blockKey)) {

                 getLogger().info(String.format("Removed teleportal(%s) at %s(%d, %d, %d)!",
                         subspaceKey, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

                // set block to an ender chest...
                block.setType(Material.ENDER_CHEST);

                BlockFace facing;
                ItemStack gatewayPrism;

                // get the teleportal's marker...
                ArmorStand marker = Utils.getMarker(block, teleportalKey.toString());

                if (marker != null) {
                    facing = marker.getFacing();
                    gatewayPrism = marker.getItemInHand();

                    marker.remove();
                }
                else {
                    facing = BlockFace.NORTH;
                    gatewayPrism = createGatewayPrism(1, null, null, null);
                }

//                // remove the marker...
//                Utils.removeMarker(block, teleportalKey.toString());

                // set the rotation of the ender chest...
                if (block.getBlockData() instanceof Directional) {

                    Directional blockData = (Directional) block.getBlockData();
                    blockData.setFacing(facing);

                    block.setBlockData(blockData);
                }

                // drop the teleportal's gateway prism...
                block.getWorld().dropItemNaturally(loc, gatewayPrism);

                // unlink the teleportal from that subspace...
                subspaceLinks.remove(blockKey);
                getSubspaces().set(subspaceKey, subspaceLinks);

                // TODO use server commands to do effects

                // spawn particle effects and play sfx...
                Location effectsLoc = loc.clone().add(0.5, 0.5, 0.5);
                block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, effectsLoc, 5, 0.1, 0.1, 0.05);
                block.getWorld().spawnParticle(Particle.DRAGON_BREATH, effectsLoc, 100, 0.25, 0.25, 0.25, 0.075);
                block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, effectsLoc, 50, 0.1, 0.1, 0.1, 0.025);
                block.getWorld().playSound(effectsLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.25f);
            }
        }
    }

    /**
     * Have an entity use the teleportal at the given block.
     */
    public void useTeleportal(Block block, BlockFace face, Entity entity) {

        Location loc = block.getLocation();
        String blockKey = Utils.posToKey(block);
        //String subspaceKey = Utils.enchantsToKey(Utils.getEnchants(gatewayPrism));

        for (String subspaceKey : getSubspaces().getKeys(false)) {

            List<String> subspaceLinks = getSubspaces().getStringList(subspaceKey);
            if (subspaceLinks.contains(blockKey)) {

                 getLogger().info(String.format("Using teleportal(%s) at %s(%d, %d, %d)!",
                         subspaceKey, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

                // get the next exit link for this teleportal...
                int index = (subspaceLinks.indexOf(blockKey) + 1) % subspaceLinks.size();
                String exitKey = subspaceLinks.get(index);
                Block exitLink = Utils.keyToPos(exitKey);

                if (exitLink == null) {
                    return;
                }

                Location tpLoc = exitLink.getLocation().add(0.5, -0.5, 0.5);

                // choose the appropriate side of the teleportal to emerge from...
                BlockFace thisDir = getTeleportalFacing(block);
                BlockFace exitDir = getTeleportalFacing(exitLink);
                int dirOffset = (face.ordinal() - thisDir.ordinal());

                tpLoc.add(BlockFace.values()[(exitDir.ordinal() + dirOffset) % 4].getDirection());

                // choose the appropriate yaw and pitch to emerge with...
                float thisYaw = (thisDir.ordinal() * 90) + 180;
                float exitYaw = exitDir.ordinal() * 90;

                tpLoc.setYaw(exitYaw - (thisYaw - entity.getLocation().getYaw()));
                tpLoc.setPitch(entity.getLocation().getPitch());

                // play sound effects...
                block.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.5f, 1.5f); // TO DO random pitch

                // teleport the entity...
                entity.teleport(tpLoc, PlayerTeleportEvent.TeleportCause.END_GATEWAY);

                // disable the end gateway beam...
                if (block.getState() instanceof EndGateway) {
                    EndGateway endGateway = (EndGateway) block.getState();
                    endGateway.setAge(250L);
                    endGateway.update(true);
                }
            }
        }
    }
}
