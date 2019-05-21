package me.cynadyde.teleportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Directional;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Main class of the Teleportals plugin.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TeleportalsPlugin extends JavaPlugin implements Listener {

    private Map<String, List<SubspaceLink>> subspaces = new HashMap<>();
    private ShapedRecipe gatewayPrismRecipe;

    private String gatewayPrismDisplay = Utils.format("&bGateway Prism");
    private String gatewayPrismDataHeader = Utils.format("&r&5&o&nSubspace Link");
    private String gatewayPrismDataPrefix = Utils.format("&r&0&k  ");

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

            // cancel the interaction event...
            event.setCancelled(true);

            // if the clicked block was an ender chest...
            Block block = event.getClickedBlock();
            if (block != null && block.getType().equals(Material.ENDER_CHEST)) {

                // if the clicked block was part of a teleportal structure...
                Block teleportal = getTeleportalFrom(block);
                if (teleportal != null) {

                    Location loc = teleportal.getLocation();
                    loc.setYaw(event.getPlayer().getLocation().getYaw());

                    // activate the teleportal...
                    createTeleportalAt(loc, getSubspaceName(usedItem));

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
            removeTeleportalAt(teleportal.getLocation());
        }
    }

    /**
     * Handle entities using teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {

        getLogger().info(event.toString());

        useTeleportal(event.getFrom().getBlock(), event.getEntity());
    }

    /**
     * Handle players using teleportals.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {

        getLogger().info(event.toString());

        useTeleportal(event.getFrom().getBlock(), event.getPlayer());
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

            List<SubspaceLink> subspaceLinks = new ArrayList<>();

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
                String[] link = ((String) obj).split(",", 5);
                if (link.length != 5) {
                    continue;
                }
                try {
                    World world = getServer().getWorld(link[0]);
                    if (world == null) {
                        continue;
                    }
                    int x = Integer.parseInt(link[1]);
                    int y = Integer.parseInt(link[2]);
                    int z = Integer.parseInt(link[3]);

                    int dirI = Integer.parseInt(link[4]);
                    if (!((0 <= dirI) && (dirI < 4))) {
                        continue;
                    }
                    BlockFace dir = BlockFace.values()[dirI];

                    SubspaceLink loc = new SubspaceLink(world, x, y, z, dir);
                    if (!subspaceLinks.contains(loc)) {
                        subspaceLinks.add(loc);
                    }
                }
                catch (IllegalArgumentException ignored) {

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
            List<String> serializedLinks = new ArrayList<>();
            for (SubspaceLink subspaceLink : subspaces.get(subspaceName)) {

                // write the serialized link to yaml...
                String link = String.format("%s,%d,%d,%d,%d",
                        subspaceLink.getWorld().getName(),
                        subspaceLink.getX(),
                        subspaceLink.getY(),
                        subspaceLink.getZ(),
                        subspaceLink.getDir().ordinal()
                );
                serializedLinks.add(link);
            }
            ymlSubspaces.set(subspaceName, serializedLinks);
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
    public String getSubspaceName(ItemStack gatewayPrism) {

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

        SubspaceLink link = SubspaceLink.from(loc);

        for (String subspaceName : subspaces.keySet()) {
            if (subspaces.get(subspaceName).contains(link)) {
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
    public void createTeleportalAt(Location loc, String subspaceName) {

        if (loc.getWorld() == null) {
            return;
        }
        // get the block and the direction it is facing...
        Block block = loc.getBlock();
        BlockFace dir;

        if (block.getState() instanceof Directional) {
            dir = ((Directional) block.getState()).getFacing();
        }
        else {
            dir = BlockFace.NORTH;
        }
        SubspaceLink link = SubspaceLink.from(loc, dir);

        // if the link for that location is unique to the given subspace...
        if (!subspaces.containsKey(subspaceName)) {
            subspaces.put(subspaceName, new ArrayList<>());
        }
        List<SubspaceLink> subspaceLinks = subspaces.get(subspaceName);
        if (!subspaceLinks.contains(link)) {

            getLogger().info(String.format("Created Teleportal(%s) at %s(%d, %d, %d)!",
                    subspaceName, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

            // link the teleportal to that subspace...
            subspaceLinks.add(link);

            // spawn particle effects and play sfx...
            Location particleLoc = loc.add(0.5f, 0.5f, 0.5f);
            block.getWorld().spawnParticle(Particle.FLASH, particleLoc, 3, 0.1, 0.1, 0.1);
            block.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 100, 0, 0, 0, 0.1);
            block.getWorld().playSound(particleLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);

            // set block to an end gateway...
            block.setType(Material.END_GATEWAY);

            saveSubspaces(); // TEMPORARY
        }
    }

    /**
     * Remove the teleportal at a block.
     */
    public void removeTeleportalAt(Location loc) {

        if (loc.getWorld() == null) {
            return;
        }
        Block block = loc.getBlock();

        // for each subspace containing this link...
        SubspaceLink link = SubspaceLink.from(block.getLocation(), BlockFace.NORTH);
        for (String subspaceName : subspaces.keySet()) {

            List<SubspaceLink> subspaceLinks = subspaces.get(subspaceName);

            if (subspaceLinks.contains(link)) {
                getLogger().info(String.format("Removed teleportal(%s) at %s(%d, %d, %d)!",
                        subspaceName, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));

                // unlink the teleportal from that subspace...
                subspaceLinks.remove(link);

                // spawn particle effects and play sfx...
                Location particleLoc = loc.add(0.5f, 0.5f, 0.5f);
                block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, particleLoc, 3, 0.1, 0.1, 0.1);
                block.getWorld().spawnParticle(Particle.DRAGON_BREATH, particleLoc, 100, 0, 0, 0, 0.1);
                block.getWorld().playSound(particleLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.5f);

                // set block to an ender chest...
                block.setType(Material.ENDER_CHEST);
                if (block.getState() instanceof Directional) {
                    ((Directional) block.getState()).setFacingDirection(link.);
                }

                // drop the teleportal's gateway prism...
                ItemStack gatewayPrism = createGatewayPrism(1, subspaceName);
                block.getWorld().dropItemNaturally(loc, gatewayPrism);

                saveSubspaces(); // TEMPORARY
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

                if (subspaceLinks.size() > 1 && index == 0) {
                    int randomIndex = Utils.RNG.nextInt(subspaceLinks.size() - 2) + 1;
                    exitLink = subspaceLinks.get(randomIndex);
                }
                else {
                    exitLink = subspaceLinks.get(0);
                }
                Location tpLoc = nearestSafeLoc(exitLink);
                if (tpLoc == null) {
                    tpLoc = blockLoc;
                }
                entity.teleport(tpLoc, PlayerTeleportEvent.TeleportCause.END_GATEWAY);
            }
        }
    }
}
