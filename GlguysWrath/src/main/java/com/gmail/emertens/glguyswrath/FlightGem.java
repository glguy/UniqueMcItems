package com.gmail.emertens.glguyswrath;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockIterator;

import java.util.*;

/**
 * Created by Eric Mertens on 12/11/13.
 * <p/>
 * This class provides the functionality of a gem that grants flight
 * when held, but which can not be horded.
 */
public class FlightGem implements Listener, CommandExecutor {

    public static final String FLIGHT = "Flight";
    private GlguysWrath plugin;
    private Entity gemTarget = null;
    private Map<String, Boolean> oldAllowFlightSettings = new HashMap<>();
    private ItemStack gemPrototype = null;

    public FlightGem(GlguysWrath plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        switch (command.getName()) {
            case "flightgem":
                return flightgemCommand(sender, args);
            case "setflightgemrespawn":
                return setflightgemdispenserCommand(sender, args);
            case "findgem":
                return findgemCommand(sender, args);
            default:
                return false;
        }
    }

    private boolean findgemCommand(final CommandSender sender, final String[] args) {

        if (args.length > 0) {
            return false;
        }

        if (sender instanceof Player) {
            final Player player = (Player) sender;
            findGemSetCompass(player, player);
        } else {
            findGemSetCompass(sender, null);
        }

        return true;
    }

    private boolean setflightgemdispenserCommand(CommandSender sender, String[] args) {

        if (args.length > 0) {
            return false;
        }

        if (sender instanceof Player) {
            final Player player = (Player) sender;

            for (final BlockIterator bi = new BlockIterator(player, 10); bi.hasNext(); ) {
                final Block b = bi.next();
                if (b.getState() instanceof InventoryHolder) {
                    setRespawnBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                    player.sendMessage(ChatColor.GREEN + "Success");
                    return true;
                }
            }
            player.sendMessage(ChatColor.RED + "Failure");
        } else {
            sender.sendMessage(ChatColor.RED + "Console can't set respawn location");
        }
        return true;
    }

    private boolean flightgemCommand(CommandSender sender, String[] args) {
        final Player player = selectPlayer(sender, args);

        if (args.length >= 2) {
            return false;
        }

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found");
        } else {
            if (player.getInventory().addItem(gemPrototype).isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "Success");
            } else {
                sender.sendMessage(ChatColor.RED + "No room in inventory");
            }
        }

        return true;
    }

    /**
     * When sender is a player, return that player when no arguments are provided.
     * When an argument is provided attempt to use that argument as the player name.
     */
    private Player selectPlayer(CommandSender sender, String[] args) {
        if (sender instanceof Player && args.length == 0) {
            return (Player) sender;
        } else if (args.length == 1) {
            return Bukkit.getPlayer(args[0]);
        } else {
            return null;
        }
    }

    public boolean initialize() {
        if (!initializePrototype()) return false;
        initializeGemTarget();
        return true;
    }

    void initializeGemTarget() {

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().contains(gemPrototype)) {
                gemTarget = player;
                plugin.getLogger().info("Flight gem found in player inventory " + player.getName());
                return;
            }
        }

        final String worldName = getRespawnWorldName();
        if (worldName == null) {
            plugin.getLogger().info("Flight gem world unspecified");
            return;
        }

        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().info("Flight gem world incorrect");
            return;
        }

        for (final Item x : world.getEntitiesByClass(Item.class)) {
            if (isFlightGem(x.getItemStack())) {
                plugin.getLogger().info("Flight gem located on ground");
                gemTarget = x;
                return;
            }
        }

        final Inventory inventory = getSpawnDispenserInventory();
        if (inventory == null) {
            plugin.getLogger().info("No dispenser found while initializing gem");
            return;
        }

        if (inventory.contains(gemPrototype)) {
            plugin.getLogger().info("Flight gem found in dispenser");
            return;
        }

        plugin.getLogger().info("Unable to locate flight gem, generating new one");
        inventory.addItem(gemPrototype);
    }

    private void findGemSetCompass(final CommandSender sender, final Player player) {

        if (gemTarget == null) {
            final Inventory inventory = getSpawnDispenserInventory();
            if (inventory != null && inventory.contains(gemPrototype)) {
                sender.sendMessage(ChatColor.GREEN + "Your compass points to the dispenser.");
                if (player != null) player.setCompassTarget(getRespawnBlock().getLocation());
            } else {
                sender.sendMessage(ChatColor.RED + "The gem is nowhere to be found.");
            }
        } else if (gemTarget instanceof Player) {
            final Player holder = (Player) gemTarget;
            if (player != null) player.setCompassTarget(gemTarget.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Your compass points to " + ChatColor.RESET + holder.getDisplayName() + ChatColor.GREEN + ".");
        } else {
            if (player != null) player.setCompassTarget(gemTarget.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Your compass points to the fallen gem.");
        }
    }

    private boolean initializePrototype() {
        FileConfiguration config = plugin.getConfig();

        try {
            final String displayName = (String)config.get("flightgem.object.name", "Flight Gem");
            final String materialName = (String)config.get("flightgem.object.material", "DIAMOND");
            final List<?> loreLineObjects = (List<?>)config.get("flightgem.object.lore", Arrays.asList("Flight"));

            // Java generics require us to do this in order to verify the types of the elements of the list
            final List<String> loreLines = new ArrayList<>(loreLineObjects.size());
            for (final Object x : loreLineObjects) {
                loreLines.add((String)x);
            }

            final Material material = Material.getMaterial(materialName);
            if (material == null) return false;

            final ItemStack gem = new ItemStack(material);
            final ItemMeta itemMeta = gem.getItemMeta();
            itemMeta.setDisplayName(displayName);
            itemMeta.setLore(loreLines);
            gem.setItemMeta(itemMeta);

            gemPrototype = gem;
            return true;
        } catch (ClassCastException e) {
            return false;
        }

    }

    /**
     * This cleans up players who had items in their inventory during
     * shutdowns and crashes
     *
     * @param event Join event
     */
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (plugin.hasBypass(player)) return;

        final Inventory inventory = player.getInventory();

        while (inventory.contains(gemPrototype)) {
            inventory.remove(gemPrototype);
            plugin.getLogger().info("Removing a flight gem on join from " + player.getName());
        }
    }

    public void restoreFlightSetting(final Player player) {
        final String name = player.getName();
        final Boolean old = oldAllowFlightSettings.get(name);
        if (old != null) {
            player.setAllowFlight(old);
            player.sendMessage(ChatColor.RED + "You feel drawn to the earth.");
            player.playSound(player.getLocation(), Sound.FIZZ, 1, 1);

        }
        oldAllowFlightSettings.remove(name);
    }

    public void allowFlight(final Player player) {
        player.sendMessage(ChatColor.GREEN + "You feel as light as air!");
        oldAllowFlightSettings.put(player.getName(), player.getAllowFlight());
        player.setAllowFlight(true);
        player.playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSwitch(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final Inventory inventory = player.getInventory();

        if (isFlightGem(inventory.getItem(event.getNewSlot()))) {
            allowFlight(player);
            return;
        }

        final ItemStack previousItem = inventory.getItem(event.getPreviousSlot());
        if (isFlightGem(previousItem)) {
            restoreFlightSetting(player);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        takeGemFromPlayer(event.getPlayer());
    }

    private void takeGemFromPlayer(Player player) {
        final Inventory inventory = player.getInventory();
        final Collection<ItemStack> gems = new ArrayList<>();

        boolean hadGem = inventory.contains(gemPrototype);
        while(inventory.contains(gemPrototype)) {
            inventory.remove(gemPrototype);
        }

        if (hadGem) {
            restoreFlightSetting(player);
            spawnGem();
            plugin.getLogger().info("Flight gem removed gem from " + player.getName());
        }
    }

    boolean isFlightGem(final ItemStack heldItem) {
        return gemPrototype.isSimilar(heldItem);
    }

    private void lightning(final Location location) {
        location.getWorld().strikeLightningEffect(location);
    }

    private void trackGem(final Entity e) {
        gemTarget = e;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDestroy(final ItemDespawnEvent event) {
        final ItemStack item = event.getEntity().getItemStack();
        if (isFlightGem(item)) {
            plugin.getLogger().info("Flight gem despawned");
            final Location location = event.getEntity().getLocation();
            location.getWorld().strikeLightningEffect(location);
            spawnGem();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(final EntityCombustEvent event) {
        final Entity entity = event.getEntity();
        if (entity instanceof Item) {
            final Item item = (Item) entity;

            if (isFlightGem(item.getItemStack())) {
                plugin.getLogger().info("Flight gem burned");
                lightning(item.getLocation());
                spawnGem();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (isFlightGem(event.getItemDrop().getItemStack())) {
            final Player player = event.getPlayer();
            restoreFlightSetting(player);
            plugin.getLogger().info("Flight gem dropped by " + player.getName());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final PlayerPickupItemEvent event) {
        final Item drop = event.getItem();
        if (isFlightGem(drop.getItemStack())) {
            final Player player = event.getPlayer();
            trackGem(player);
            plugin.getLogger().info("Flight gem picked up by " + player.getName() + " at " + drop.getLocation());

            if (playerWillPickupIntoHand(player)) {
                allowFlight(player);
            }
        }
    }

    private boolean playerWillPickupIntoHand(final Player player) {
        final PlayerInventory inventory = player.getInventory();

        if (player.getItemInHand().getType() != Material.AIR) return false;

        for (int i = 0; i < inventory.getHeldItemSlot(); i++) {
            if (inventory.getItem(i) == null) return false;
        }

        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(final PlayerDeathEvent event) {
        if (event.getDrops().contains(gemPrototype)) {
            final Player player = event.getEntity();
            plugin.getLogger().info("Flight gem dropped by dead " + player.getName());
            restoreFlightSetting(player);
        }
    }

    /**
     * Prevent hoppers from picking up the flight gem.
     * @param event Pickup event for hoppers
     */
    @EventHandler(ignoreCancelled = true)
    public void onInvMove(final InventoryPickupItemEvent event) {
        if (isFlightGem(event.getItem().getItemStack())) {
            plugin.getLogger().info("Prohibiting inventory pickup of flight gem");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(final ItemSpawnEvent event) {
        final Item item = event.getEntity();
        if (isFlightGem(item.getItemStack())) {
            trackGem(item);
            plugin.getLogger().info("Flight gem spawned at " + item.getLocation());
        }
    }

    private Inventory getSpawnDispenserInventory() {
        final Block block = getRespawnBlock();
        if (block == null) return null;
        final BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            final InventoryHolder dispenser = (InventoryHolder) state;
            return dispenser.getInventory();
        } else {
            return null;
        }
    }
    /**
     * Attempt to return the gem to its home dispenser.
     */
    private void spawnGem() {
        trackGem(null);
        final Inventory inventory = getSpawnDispenserInventory();
        if (inventory != null) {
            if (inventory.addItem(gemPrototype).isEmpty()) {
                final String msg = getSpawnMessage();
                if (msg != null) {
                    Bukkit.broadcastMessage(msg);
                }
            }
        }
    }

    private String getSpawnMessage() {
        final Object o = plugin.getConfig().get("flightgem.respawn.message");
        if (o instanceof String) {
            return ChatColor.translateAlternateColorCodes('&', (String) o);
        } else {
            return null;
        }
    }

    private String getRespawnWorldName() {
        final Object w = plugin.getConfig().get("flightgem.respawn.world");
        if (w instanceof String) {
            return (String) w;
        } else {
            return null;
        }
    }

    private Block getRespawnBlock() {
        final FileConfiguration config = plugin.getConfig();

        final String w = getRespawnWorldName();
        final Object x = config.get("flightgem.respawn.x");
        final Object y = config.get("flightgem.respawn.y");
        final Object z = config.get("flightgem.respawn.z");

        if (w != null && x != null && y != null && z != null &&
                x instanceof Integer &&
                y instanceof Integer &&
                z instanceof Integer) {

            final World world = Bukkit.getWorld(w);

            if (world == null) return null;

            return world.getBlockAt((Integer) x, (Integer) y, (Integer) z);
        }

        return null;
    }

    private void setRespawnBlock(String world, int x, int y, int z) {
        final FileConfiguration config = plugin.getConfig();
        config.set("flightgem.respawn.world", world);
        config.set("flightgem.respawn.x", x);
        config.set("flightgem.respawn.y", y);
        config.set("flightgem.respawn.z", z);
        plugin.saveConfig();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final HumanEntity human = event.getWhoClicked();
        if (human instanceof Player) {
            final Player player = (Player) human;

            if (!isFlightGem(event.getCurrentItem()) && !isFlightGem(event.getCursor())) return;

            if (isFlightGem(event.getCurrentItem())) {
                restoreFlightSetting(player);
            }

            if (plugin.hasBypass(player)) return;

            final InventoryType invType = event.getInventory().getType();
            final InventoryType.SlotType slotType = event.getSlotType();

            if (invType == InventoryType.CRAFTING && slotType == InventoryType.SlotType.CONTAINER ||
                    invType == InventoryType.CRAFTING && slotType == InventoryType.SlotType.QUICKBAR) {
                return;
            }

            plugin.getLogger().info("Canceling flight gem inventory click " + invType + ":" + slotType + " for " + player.getName());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIventoryDrag(final InventoryDragEvent event) {
        final HumanEntity human = event.getWhoClicked();
        if (human instanceof Player) {
            final Player player = (Player) human;

            if (!isFlightGem(event.getOldCursor())) return;

            if (plugin.hasBypass(player)) return;

            event.setCancelled(true);
            plugin.getLogger().info("Canceling inventory drag for " + player.getName());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(final EntityDamageByEntityEvent event) {
        final Entity target = event.getEntity();

        if (target instanceof Player) {
            final Player player = (Player) target;

            if (isFlightGem(player.getItemInHand())) {

                if (plugin.hasBypass(player)) return;

                player.sendMessage(ChatColor.RED + "The gem slips from your fingers!");
                restoreFlightSetting(player);
                player.setItemInHand(null);
                player.getWorld().dropItemNaturally(player.getLocation(), gemPrototype);
                plugin.getLogger().info("Flight gem slipped from " + player.getName());
            }
        }
    }

    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        if (player.getWorld().getName().equalsIgnoreCase(getRespawnWorldName())) return;
        if (plugin.hasBypass(player)) return;
        takeGemFromPlayer(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        if (event.getTo().getWorld().getName().equalsIgnoreCase(getRespawnWorldName())) return;
        if (plugin.hasBypass(player)) return;
        takeGemFromPlayer(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnloadChunk(final ChunkUnloadEvent event) {
        for (final Entity e : event.getChunk().getEntities()) {
            if (e instanceof Item) {
                final Item item = (Item) e;
                if (isFlightGem(item.getItemStack())) {
                    plugin.getLogger().info("Flight gem unloaded");
                    item.remove();
                    lightning(item.getLocation());
                    spawnGem();
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemFramePlace(final PlayerInteractEntityEvent event) {
        final Entity target = event.getRightClicked();
        if (target instanceof ItemFrame) {
            final Player player = event.getPlayer();
            if (plugin.hasBypass(player)) return;
            if (isFlightGem(player.getItemInHand())) {
                event.setCancelled(true);
                plugin.getLogger().info("Prevented item frame placement by " + player.getName());
            }
        }
    }
}
