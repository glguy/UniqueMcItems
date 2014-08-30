package com.gmail.emertens.flightgem;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;
import java.util.Iterator;

/**
 * Copyright 2013 Eric Mertens
 *
 * This class implements all the event handlers needed for flight gem
 * behavior and safety.
 */
final class FlightGem implements Listener {

    private static final String DISARM_MESSAGE = ChatColor.RED + "The gem slips from your fingers!";
    private FlightGemPlugin plugin;

    public FlightGem(FlightGemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * This cleans up players who had items in their inventory during
     * shutdowns and crashes. They are silently removed upon join.
     *
     * @param event Join event
     */
    @EventHandler
    void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (plugin.hasBypass(player)) return;

        final Inventory inventory = player.getInventory();
        final ItemStack gem = plugin.getGem();

        while (inventory.containsAtLeast(gem, 1)) {
            inventory.removeItem(gem);
            plugin.info("Join with gem", player);
        }
    }

    /**
     * Detect when a player changes his equipped item by changing his
     * active hot-bar slot. Apply the corresponding flight permissions.
     * @param event Item held event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onItemSwitch(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final Inventory inventory = player.getInventory();

        if (plugin.isFlightGem(inventory.getItem(event.getNewSlot()))) {
            plugin.allowFlight(player);
            return;
        }

        final ItemStack previousItem = inventory.getItem(event.getPreviousSlot());
        if (plugin.isFlightGem(previousItem)) {
            plugin.restoreFlightSetting(player);
        }
    }

    /**
     * When a player quits with a gem it is removed from his inventory and respawned.
     * @param event Quit event
     */
    @EventHandler
    void onQuit(final PlayerQuitEvent event) {
        plugin.takeGemFromPlayer(event.getPlayer());
    }

    /**
     * When the gem despawns due to timeout, respawn it.
     * @param event Item despawn event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onDestroy(final ItemDespawnEvent event) {
        final Item item = event.getEntity();
        if (plugin.isFlightGem(item)) {
            final Location location = item.getLocation();
            plugin.info("Despawn", location);
            FlightGemPlugin.lightning(location);
            plugin.spawnGem();
        }
    }

    /**
     * When the gem burns up, respawn in.
     * @param event Combust event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBurn(final EntityCombustEvent event) {
        final Entity entity = event.getEntity();
        if (plugin.isFlightGem(entity)) {
            final Location location = entity.getLocation();
            plugin.info("Combust", location);
            FlightGemPlugin.lightning(location);
            plugin.spawnGem();
        }
    }

    /**
     * When a player drops the gem, restore flight permission.
     * @param event Drop item event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onDrop(final PlayerDropItemEvent event) {
        if (plugin.isFlightGem(event.getItemDrop())) {
            final Player player = event.getPlayer();
            plugin.restoreFlightSetting(player);
            plugin.info("Drop", player);
        }
    }

    /**
     * When a player picks up the gem, track that player for /findgem.
     * When the player picks the gem up into his hand directly, immediately
     * allow flight.
     * @param event Pickup item event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPickup(final PlayerPickupItemEvent event) {
        final Item item = event.getItem();
        if (plugin.isFlightGem(item)) {
            final Player player = event.getPlayer();
            plugin.trackGem(player);
            plugin.info("Pickup", player);

            if (playerWillPickupIntoHand(player)) {
                plugin.allowFlight(player);
            }
        }
    }

    /**
     * Determine if the next available item pickup slot is the player's
     * active hot-bar slot.
     * @param player Player picking up an item
     * @return true when the next item pickup will pickup into the player's hand
     */
    private static boolean playerWillPickupIntoHand(final Player player) {
        final PlayerInventory inventory = player.getInventory();

        if (player.getItemInHand().getType() != Material.AIR) {
            return false;
        }

        final int held = inventory.getHeldItemSlot();
        for (int i = 0; i < held; i++) {
            if (inventory.getItem(i) == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * When a player dies, restore his flight setting. Automatically drop the gem
     * in order to work around death-chest plugins.
     * @param event Death event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    void onDeath(final PlayerDeathEvent event) {

        final Collection<ItemStack> drops = event.getDrops();
        final Iterator<ItemStack> iterator = drops.iterator();
        boolean hadFlightGem = false;

        while (iterator.hasNext()) {
            if (plugin.isFlightGem(iterator.next())) {
                iterator.remove();
                hadFlightGem = true;
            }
        }

        if (hadFlightGem) {
            final Player player = event.getEntity();
            plugin.info("Death", player);
            plugin.restoreFlightSetting(player);
            player.getWorld().dropItemNaturally(player.getLocation(), plugin.getGem());
        }
    }

    /**
     * When a flight gem item spawns, track it for /findgem
     * @param event Item spawn event
     */
    @EventHandler(ignoreCancelled = true)
    void onItemSpawn(final ItemSpawnEvent event) {
        final Item item = event.getEntity();
        if (plugin.isFlightGem(item)) {

            if (!plugin.isEnabledWorld(item.getLocation().getWorld())) {
                event.setCancelled(true);
                plugin.info("Spawn in wrong world", item.getLocation());
                event.setCancelled(true);
                item.remove();
                return;
            }

            // We won't get an event if LAVA destroys the gem, so if we spawn
            // in a lava block, respawn immediately.

            final Material material = item.getLocation().getBlock().getType();
            if (Material.LAVA.equals(material)
                    || Material.STATIONARY_LAVA.equals(material)
                    || Material.FIRE.equals(material)) {
                event.setCancelled(true);
                FlightGemPlugin.lightning(item.getLocation());
                plugin.info("Lava", item.getLocation());
                plugin.spawnGem();
            } else {
                plugin.trackGem(item);
                plugin.info("Spawn", item.getLocation());
            }
        }
    }


    // It's handy to let this event run at NORMAL priority
    // because WorldGuard mistakenly listens at HIGH priority
    // and this allows disarm in non-pvp zones.

    /**
     * When a player is attacked with the gem in hand it will drop to the
     * ground. This helps reduce the impact to PvP balance.
     * @param event Entity damage by entity event
     */
    @EventHandler(ignoreCancelled = true)
    void onHit(final EntityDamageByEntityEvent event) {
        final Entity target = event.getEntity();

        if (target instanceof Player) {
            final Player player = (Player) target;

            if (plugin.isFlightGem(player.getItemInHand())) {

                if (plugin.hasBypass(player)) return;

                player.sendMessage(DISARM_MESSAGE);
                plugin.restoreFlightSetting(player);
                player.setItemInHand(null);
                plugin.info("Disarmed", player);
                player.getWorld().dropItemNaturally(player.getLocation(), plugin.getGem());
            }
        }
    }

    /**
     * Remove the gem from a player changing worlds away from the gem's home world.
     * This keeps the gem from unbalancing Nether and End worlds. Vanilla portals
     * do not fire teleport events.
     * @param event Changed world event
     */
    @EventHandler
    void onWorldChange(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final boolean playerLeftWorld = !plugin.isEnabledWorld(player.getWorld());
        if (playerLeftWorld && !plugin.hasBypass(player) && player.getInventory().containsAtLeast(plugin.getGem(), 1)) {
            plugin.info("World change", player);
            plugin.takeGemFromPlayer(player);
        }
    }

    /**
     * This event handler is needed in addition to world-change handling because
     * world change events fire AFTER Multiverse-Inventories swaps out creative
     * mode inventories.
     * @param event Teleport event
     */
    @EventHandler(ignoreCancelled = true)
    void onTeleport(final PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        final boolean playerLeftWorld = !plugin.isEnabledWorld(event.getTo().getWorld());
        if (playerLeftWorld && !plugin.hasBypass(player) && player.getInventory().containsAtLeast(plugin.getGem(), 1)) {
            plugin.info("Teleport", player);
            plugin.takeGemFromPlayer(player);
        }
    }

    /**
     * Respawn the gem before it is store (possibly for a long time) in an
     * unloading chunk. This typically happens when a player dies while on
     * his own and results in the gem practically getting lost.
     * @param event Unload chunk event
     */
    @EventHandler(ignoreCancelled = true)
    void onUnloadChunk(final ChunkUnloadEvent event) {
        for (final Entity e : event.getChunk().getEntities()) {
            if (plugin.isFlightGem(e)) {
                final Location location = e.getLocation();
                plugin.info("Chunk unload", location);
                e.remove();
                FlightGemPlugin.lightning(location);
                plugin.spawnGem();
            } else if (e instanceof LivingEntity && !(e instanceof Player)) {
                // Player check is because glitchy warps can leave the player in an unloading
                // chunk before moving him.
                final LivingEntity livingEntity = (LivingEntity) e;
                final EntityEquipment equipment = livingEntity.getEquipment();

                if (plugin.isFlightGem(equipment.getItemInHand())) {
                    equipment.setItemInHand(null);
                    final Location location = e.getLocation();
                    plugin.info("Took from mob", location);
                    FlightGemPlugin.lightning(location);
                    plugin.spawnGem();
                }
            }
        }
    }

    /**
     * Prevent players from stashing the gem in item frames.
     * @param event Interact entity event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    void onItemFramePlace(final PlayerInteractEntityEvent event) {
        final Entity target = event.getRightClicked();
        if (target instanceof ItemFrame) {
            final Player player = event.getPlayer();
            if (plugin.isFlightGem(player.getItemInHand()) && !plugin.hasBypass(player)) {
                plugin.info("Item frame", player);
                event.setCancelled(true);
            }
        }
    }

    private static boolean isBottomClick(final InventoryClickEvent event) {
        return event.getRawSlot() >= event.getView().getTopInventory().getSize();
    }

    /**
     * Bad drags slip out of the player's inventory and quickbar
     * @param event Drag event
     * @return true if the drag leaves the players inventory
     */
    private static boolean isBadDrag(final InventoryDragEvent event) {
        final int topSize = event.getView().getTopInventory().getSize();
        for (final int x : event.getRawSlots()) {
            if (x < topSize) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prevent players from transferring the gem into containers or crafting slots
     * @param event Inventory click event
     */
    @EventHandler(ignoreCancelled = true)
    void onInventoryClick(final InventoryClickEvent event) {

        final HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player)) return;
        final boolean enforce = !plugin.hasBypass(human);
        final Player player = (Player) human;

        final InventoryAction action = event.getAction();
        final boolean currentIsGem = plugin.isFlightGem(event.getCurrentItem());
        final Inventory inventory = event.getInventory();
        final InventoryType inventoryType = inventory.getType();
        final boolean bottomClick = isBottomClick(event);
        if (currentIsGem
                && bottomClick
                && InventoryAction.MOVE_TO_OTHER_INVENTORY.equals(action)
                && !InventoryType.CRAFTING.equals(inventoryType)
                && !InventoryType.WORKBENCH.equals(inventoryType)
                && !InventoryType.BREWING.equals(inventoryType)) {

            if (enforce) {
                plugin.info("Inventory move", human);
                event.setCancelled(true);
            } else {
                plugin.restoreFlightSetting(player);
            }
            return;
        }

        final boolean isGemHotBarSwap =
                (InventoryAction.HOTBAR_SWAP.equals(action) ||
                        InventoryAction.HOTBAR_MOVE_AND_READD.equals(action))
                        && plugin.isFlightGem(human.getInventory().getItem(event.getHotbarButton()));

        // Ignore no-op swap
        if (InventoryType.SlotType.QUICKBAR.equals(event.getSlotType())
                && InventoryAction.HOTBAR_SWAP.equals(action)
                && event.getHotbarButton() == event.getSlot()
                ) {
            return;
        }

        if (currentIsGem || (isGemHotBarSwap && player.getInventory().getHeldItemSlot() == event.getHotbarButton())
                ) {
            plugin.restoreFlightSetting(player);
        }

        final boolean cursorIsGem = plugin.isFlightGem(event.getCursor());

        if (cursorIsGem
                && InventoryType.SlotType.QUICKBAR.equals(event.getSlotType())
                && event.getSlot() == player.getInventory().getHeldItemSlot()
                && (InventoryAction.PLACE_ALL.equals(action) ||
                InventoryAction.PLACE_ONE.equals(action) ||
                InventoryAction.SWAP_WITH_CURSOR.equals(action))
                ||
                currentIsGem
                        && InventoryAction.HOTBAR_SWAP.equals(action)
                        && event.getHotbarButton() == player.getInventory().getHeldItemSlot()
                ||
                isGemHotBarSwap
                        && InventoryType.SlotType.QUICKBAR.equals(event.getSlotType())
                        && event.getSlot() == player.getInventory().getHeldItemSlot()
                ) {
            plugin.allowFlight(player);
            return;
        }

        if (bottomClick || event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            return;
        }

        if (isGemHotBarSwap || cursorIsGem) {
            if (enforce) {
                plugin.info("Inventory click", human);
                event.setCancelled(true);
            } else {
                plugin.restoreFlightSetting(player);
            }
        }
    }

    /**
     * Prevent players from "smearing" the gem into containers.
     * @param event Inventory drag event
     */
    @EventHandler(ignoreCancelled = true)
    void onInventoryDrag(final InventoryDragEvent event) {
        final HumanEntity player = event.getWhoClicked();

        if (plugin.isFlightGem(event.getOldCursor()) &&
                !plugin.hasBypass(player) &&
                isBadDrag(event)) {

            plugin.info("Inventory drag", player);
            event.setCancelled(true);
        }
    }


    /**
     * Prevent hoppers from picking up the flight gem.
     * @param event Pickup event for hoppers
     */
    @EventHandler(ignoreCancelled = true)
    void onInvMove(final InventoryPickupItemEvent event) {
        final Item item = event.getItem();
        if (plugin.isFlightGem(item)) {
            plugin.info("Hopper pickup", item.getLocation());
            event.setCancelled(true);
        }
    }
}
