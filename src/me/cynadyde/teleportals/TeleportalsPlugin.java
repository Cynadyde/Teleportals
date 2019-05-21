package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Main class of the Teleportals plugin.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TeleportalsPlugin extends JavaPlugin implements Listener {

    private Map<String, List<Location>> subspaces = new HashMap<>();
    private Map<Location, BlockFace> directions = new HashMap<>();
    private BukkitTask portalEnterListener;
    private ShapedRecipe gatewayPrismRecipe;

    private String gatewayPrismDisplay = Utils.format("&bGateway Prism");
    private String gatewayPrismDataHeader = Utils.format("&r&7Subspace Link:");
    private String gatewayPrismDataPrefix = Utils.format("&r&8&k ");

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

        portalEnterListener = new BukkitRunnable() {
            @Override public void run() {

                for (World world : getServer().getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        for (Entity entity : chunk.getEntities()) {

                            Block block = entity.getLocation().getBlock();
                            if (block.getType() == Material.END_GATEWAY) {

                                useTeleportal(block, entity);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        loadPluginData();

        // TODO craft empty gateway prism

        // TODO extract enchanted book from gateway prism

        // TODO combine enchanted book into empty gateway prism

        // TODO autosave for plugin data?

        // TODO save plugin data in sqlite database?
    }

    /**
     * Save subspaces to the data file.
     */
    @Override
    public void onDisable() {

        portalEnterListener.cancel();

        savePluginData();
    }

    /**
     * When crafting a gateway prism, set its subspace link using the enchanted book in the recipe.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepareCraftItem(PrepareItemCraftEvent event) {

        if (event.getRecipe() instanceof Keyed) {
            if (((Keyed) event.getRecipe()).getKey().equals(gatewayPrismRecipe.getKey())) {

                String subspaceName = "";
                for (ItemStack ingredient : event.getInventory().getMatrix()) {

                    if (ingredient.getType().equals(Material.ENCHANTED_BOOK)) {
                        subspaceName = createSubspaceName(ingredient);
                        break;
                    }
                }
                setSubspaceName(event.getInventory().getResult(), subspaceName);
            }
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
        // if the player was using a gateway prism...
        ItemStack usedItem = event.getPlayer().getInventory().getItemInMainHand();
        if (isGatewayPrism(usedItem)) {

            getLogger().info("OOF");

            // cancel the interaction event...
            event.setCancelled(true);

            // if the clicked block was an ender chest...
            Block block = event.getClickedBlock();
            if (block != null && block.getType().equals(Material.ENDER_CHEST)) {

                // if the clicked block was part of a teleportal structure...
                Block teleportal = getTeleportalFrom(block);
                if (teleportal != null) {

                    // activate the teleportal...
                    createTeleportalAt(teleportal, getSubspaceName(usedItem));

                    // consume the gateway prism...
                    if (!event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
                        usedItem.setAmount(usedItem.getAmount() - 1);
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

        // if a teleportal or teleportal's frame was broken...
        Block teleportal = getTeleportalFrom(block);
        if (teleportal != null) {

            // deactivate the teleportal...
            removeTeleportalAt(teleportal);
        }
    }

    // FIXME cannot get these events to work... must regularly look for entities inside of end gateways :/
    // look by entity? BETTER
    // look by subspace link? NO WAY

//    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
//    public void onProjectileHitEvent(ProjectileHitEvent event) {
//
//        getLogger().info(event.toString());
//
//        Block block = event.getHitBlock();
//        if (block == null || block.getType() != Material.END_GATEWAY) {
//            return;
//        }
//        Block teleportal = getTeleportalFrom(block);
//        if (teleportal == null) {
//            return;
//        }
//        useTeleportal(teleportal, event.getEntity());
//    }
//
//    /**
//     * Handle entities using teleportals.
//     */
//    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
//    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
//
//        getLogger().info(event.toString());
//
//        useTeleportal(event.getLocation().getBlock(), event.getEntity());
//    }
//
//    /**
//     * Handle players using teleportals.
//     */
//    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
//    public void onPlayerPortalEnter(PlayerPortalEvent event) {
//
//        getLogger().info(event.toString());
//
//        useTeleportal(event.getFrom().getBlock(), event.getPlayer());
//    }

    /**
     * Copy saved subspaces and their links from the plugin's data file.
     */
    public void loadPluginData() {

        // get the plugin's yaml data file...
        File file = new File(getDataFolder(), "data.yml");
        YamlConfiguration ymlData = YamlConfiguration.loadConfiguration(file);

        // get the enderchest dirs section...
        ConfigurationSection ymlDirections = ymlData.getConfigurationSection("directions");
        if (ymlDirections == null) {
            ymlDirections = ymlData.createSection("directions");
        }
        // load each ender-chest direction in the data file...
        for (String key : ymlDirections.getKeys(false)) {
            try {
                Location loc = Utils.stringToLocation(key);
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                BlockFace dir = BlockFace.valueOf(ymlDirections.getString(key));
                directions.put(loc, dir);
            }
            catch (IllegalArgumentException ignored) {

            }
        }

        // get the subspaces section...
        ConfigurationSection ymlSubspaces = ymlData.getConfigurationSection("subspaces");
        if (ymlSubspaces == null) {
            ymlSubspaces = ymlData.createSection("subspaces");
        }

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
                Location loc = Utils.stringToLocation((String) obj);
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                if (!subspaceLinks.contains(loc)) {
                    subspaceLinks.add(loc);
                }
            }
            // add all collected subspace links to the plugin...
            subspaces.put(subspaceName, subspaceLinks);
        }
    }

    /**
     * Copy loaded subspaces and their links to the plugin's data file.
     */
    public void savePluginData() {

        // get the plugin's yaml data file...
        File file = new File(getDataFolder(), "data.yml");
        YamlConfiguration ymlData = YamlConfiguration.loadConfiguration(file);

        // get a clean directions section...
        ymlData.set("directions", null);
        ConfigurationSection ymlDirections = ymlData.createSection("directions");

        // for each direction in loaded data...
        for (Location loc : directions.keySet()) {

            // write the serialized direction to yaml...
            String key = Utils.locationToString(loc);
            String val = directions.get(loc).name();

            ymlDirections.set(key, val);
        }

        // get a clean subspaces section...
        ymlData.set("subspaces", null);
        ConfigurationSection ymlSubspaces = ymlData.createSection("subspaces");

        // for subspace in loaded data...
        for (String subspaceName : subspaces.keySet()) {

            // for each link in the subspace...
            List<String> serializedLinks = new ArrayList<>();
            for (Location subspaceLink : subspaces.get(subspaceName)) {

                // write the serialized link to yaml...
                if (subspaceLink.getWorld() == null) {
                    continue;
                }
                String link = Utils.locationToString(subspaceLink);
                serializedLinks.add(link);
            }
            ymlSubspaces.set(subspaceName, serializedLinks);
        }
        // save the yaml data to file...
        try {
            ymlData.save(file);
        }
        catch (IOException ex) {
            getLogger().severe(String.format("Could not save plugin data! %s", ex.getMessage()));
            getLogger().severe("If the issue is not resolved, this plugin will lose data on shutdown.");
        }
    }

    /**
     * Test if the given item is a gateway prism.
     */
    public boolean isGatewayPrism(ItemStack item) {

        if (item == null || !item.getType().equals(Material.END_CRYSTAL)) {
            return false;
        }
        getLogger().info("IS AN END CRYSTAL");

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        getLogger().info("HAS ITEM META");

        List<String> itemLore = itemMeta.getLore();

        if (itemLore == null || itemLore.isEmpty()) {
            return false;
        }
        getLogger().info("HAS LORE");

        int dataIndex = Utils.colorStripIndexOf(itemLore, gatewayPrismDataHeader) + 1;

        if (dataIndex <= 0 || dataIndex >= itemLore.size()) {
            getLogger().info("BAD DATA INDEX: " + dataIndex);
            return false;
        }
        getLogger().info("AAAAAAAAA");
        //noinspection RedundantIfStatement
        if (!ChatColor.stripColor(itemLore.get(dataIndex))
                .startsWith(ChatColor.stripColor(gatewayPrismDataPrefix))) {
            getLogger().info("NOOOOOOOOO");
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
    public String getSubspaceName(ItemStack gatewayPrism) {

        ItemMeta itemMeta = gatewayPrism.getItemMeta();
        if (itemMeta == null) {
            return "";
        }
        List<String> itemLore = itemMeta.getLore();
        if (itemLore == null) {
            itemLore = new ArrayList<>();
        }
        int dataIndex = Utils.colorStripIndexOf(itemLore, gatewayPrismDataHeader) + 1;

        if (dataIndex <= 0 || dataIndex >= itemLore.size()) {
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
    public void setSubspaceName(ItemStack gatewayPrism, String subspaceName) {

        if (gatewayPrism == null) {
            return;
        }
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
        int dataIndex = Utils.colorStripIndexOf(itemLore, gatewayPrismDataHeader) + 1;

        if (dataIndex <= 0 || dataIndex < itemLore.size()) {
            itemLore.set(dataIndex, gatewayPrismDataPrefix + subspaceName);
        }
        else {
            itemLore.add(gatewayPrismDataPrefix + subspaceName);
        }
        itemMeta.setLore(itemLore);
        gatewayPrism.setItemMeta(itemMeta);
    }

    /**
     * Get the subspace name specified by an enchanted book.
     */
    public String createSubspaceName(ItemStack enchantedBook) {

        Map<Enchantment, Integer> enchants;
        {
            if (enchantedBook == null) {
                return "";
            }
            ItemMeta itemMeta = enchantedBook.getItemMeta();
            if (!(itemMeta instanceof EnchantmentStorageMeta)) {
                return "";
            }
            enchants = ((EnchantmentStorageMeta) itemMeta).getStoredEnchants();
        }

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
     * Test if the given location is linked to a subspace.
     */
    public boolean isTeleportal(Location loc) {

        for (String subspaceName : subspaces.keySet()) {
            if (subspaces.get(subspaceName).contains(loc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if the given block is part of a teleportal structure.
     */
    public Block getTeleportalFrom(Block block) {

        if (block == null) {
            return null;
        }
        for (BlockFace dir : new BlockFace[]{BlockFace.SELF, BlockFace.UP, BlockFace.DOWN}) {

            Block relBlock = block.getRelative(dir);

            if ((relBlock.getType().equals(Material.ENDER_CHEST)
                    || relBlock.getType().equals(Material.END_GATEWAY))) {

                if (relBlock.getRelative(BlockFace.DOWN).getType().equals(Material.OBSIDIAN)
                        && relBlock.getRelative(BlockFace.UP).getType().equals(Material.OBSIDIAN)) {
                    return relBlock;
                }
            }
        }
        return null;
    }

    /**
     * Create a teleportal linked to the given subspace at a block.
     */
    public void createTeleportalAt(Block block, String subspaceName) {

        Location loc = block.getLocation();

        // if the link for that location is unique to the given subspace...
        if (!subspaces.containsKey(subspaceName)) {
            subspaces.put(subspaceName, new ArrayList<>());
        }
        List<Location> subspaceLinks = subspaces.get(subspaceName);
        if (!block.getType().equals(Material.END_GATEWAY)) {
            subspaceLinks.remove(loc);
        }
        if (!subspaceLinks.contains(loc)) {

            getLogger().info(String.format("Created Teleportal(%s) at %s(%d, %d, %d)!",
                    subspaceName, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

            // link the teleportal to that subspace...
            subspaceLinks.add(loc);

            // spawn particle effects and play sfx...
            Location effectsLoc = loc.clone().add(0.5, 0.5, 0.5);
            block.getWorld().spawnParticle(Particle.FLASH, effectsLoc, 3, 0.1, 0.1, 0.1); // FIXME different effect plz?
            block.getWorld().spawnParticle(Particle.END_ROD, effectsLoc, 100, 0, 0, 0, 0.1);
            block.getWorld().playSound(effectsLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);

            // save the block's facing direction if possible...
            if (block.getBlockData() instanceof Directional) {
                directions.put(loc, ((Directional) block.getBlockData()).getFacing());
            }
            // set block to an end gateway...
            block.setType(Material.END_GATEWAY);

            /*// make the end gateway functional if possible...
            if (block.getState() instanceof EndGateway) {
                if (subspaceLinks.size() > 1) {

                    int prevIndex = subspaceLinks.indexOf(loc) - 1;
                    Location prevLoc = subspaceLinks.get(prevIndex);

                    Block prevBlock = prevLoc.getBlock();
                    if (prevBlock.getState() instanceof EndGateway) {
                        EndGateway prevEndGateway = (EndGateway) prevBlock.getState();
                        prevEndGateway.setExactTeleport(true);
                        prevEndGateway.setExitLocation(loc);
                        prevEndGateway.update();
                    }

                    int nextIndex = 0;
                    Location nextLoc = subspaceLinks.get(0);

                    EndGateway endGateway = (EndGateway) block.getState();
                    endGateway.setExactTeleport(true);
                    endGateway.setExitLocation(nextLoc);
                    endGateway.update();
                }
            }*/
            savePluginData(); // FIXME TEMPORARY
        }
    }

    /**
     * Remove the teleportal at a block.
     */
    public void removeTeleportalAt(Block block) {

        Location loc = block.getLocation();

        // for each subspace containing this link...
        for (String subspaceName : subspaces.keySet()) {
            List<Location> subspaceLinks = subspaces.get(subspaceName);

            if (subspaceLinks.contains(loc)) {

                getLogger().info(String.format("Removed teleportal(%s) at %s(%d, %d, %d)!",
                        subspaceName, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

                // unlink the teleportal from that subspace...
                subspaceLinks.remove(loc);

                // spawn particle effects and play sfx...
                Location effectsLoc = loc.clone().add(0.5, 0.5, 0.5);
                block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, effectsLoc, 3, 0.1, 0.1, 0.1);
                block.getWorld().spawnParticle(Particle.DRAGON_BREATH, effectsLoc, 100, 0, 0, 0, 0.1);
                block.getWorld().playSound(effectsLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.5f);

                // set block to an ender chest...
                block.setType(Material.ENDER_CHEST);
                if (block.getBlockData() instanceof Directional) {

                    BlockFace dir = directions.remove(loc);
                    if (dir == null) {
                        dir = BlockFace.NORTH;
                    }
                    Directional blockData = (Directional) block.getBlockData();
                    blockData.setFacing(dir);
                    block.setBlockData(blockData);
                }
                // drop the teleportal's gateway prism...
                ItemStack gatewayPrism = createGatewayPrism(1, subspaceName);
                block.getWorld().dropItemNaturally(loc, gatewayPrism);

                savePluginData(); // FIXME TEMPORARY
            }
        }
    }

    /**
     * Have an entity use the teleportal at the given block.
     */
    public void useTeleportal(Block block, Entity entity) {

        Location blockLoc = block.getLocation();
        for (String subspaceName : subspaces.keySet()) {

            List<Location> subspaceLinks = subspaces.get(subspaceName);
            if (subspaceLinks.contains(blockLoc)) {

                getLogger().info(String.format("Using teleportal(%s) at %s(%d, %d, %d)!",
                        subspaceName, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

                Location exitLink;
                int index = subspaceLinks.indexOf(blockLoc);

                if (index == 0 && subspaceLinks.size() > 1) {
                    int randomIndex = (int) Math.floor(Utils.RNG.nextFloat() * (subspaceLinks.size() - 1.0)) + 1;
                    exitLink = subspaceLinks.get(randomIndex);
                }
                else {
                    exitLink = subspaceLinks.get(0);
                }
                /*
                Location tpLoc = nearestSafeLoc(exitLink);
                if (tpLoc == null) {
                    tpLoc = blockLoc;
                }
                */
                Location offset = entity.getLocation().subtract(blockLoc);
                Location tpLoc = exitLink.clone().add(offset);

                entity.teleport(tpLoc, PlayerTeleportEvent.TeleportCause.END_GATEWAY);
            }
        }
    }

    /**
     * Returns the nearest safe block from the given loc.
     */
    public Location nearestSafeLoc(Location loc) {

        return loc;  // TODO nearest safe location algorithm
    }
}
