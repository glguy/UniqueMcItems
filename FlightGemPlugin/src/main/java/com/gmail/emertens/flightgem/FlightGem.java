package com.gmail.emertens.flightgem;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Created by Eric Mertens on 12/11/13.
 * <p/>
 * This class provides the functionality of a gem that grants flight
 * when held, but which can not be horded.
 */
class FlightGem implements Listener {

    private FlightGemPlugin plugin;

    public FlightGem(FlightGemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * This cleans up players who had items in their inventory during
     * shutdowns and crashes
     *
     * @param event Join event
     */
    @EventHandler
    void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (plugin.hasBypass(player)) return;

        final Inventory inventory = player.getInventory();
        final ItemStack gem = plugin.getGem();

        while (inventory.contains(gem)) {
            inventory.remove(gem);
            plugin.getLogger().info("Removing a flight gem on join from " + player.getName());
        }
    }


    @EventHandler(ignoreCancelled = true)
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

    @EventHandler
    void onQuit(final PlayerQuitEvent event) {
        plugin.takeGemFromPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    void onDestroy(final ItemDespawnEvent event) {
        final Item item = event.getEntity();
        if (plugin.isFlightGem(item)) {
            final Location location = item.getLocation();
            plugin.info("Despawn", location);
            plugin.lightning(location);
            plugin.spawnGem();
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onBurn(final EntityCombustEvent event) {
        final Entity entity = event.getEntity();
        if (plugin.isFlightGem(entity)) {
            final Location location = entity.getLocation();
            plugin.info("Combust", location);
            plugin.lightning(location);
            plugin.spawnGem();
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(final PlayerDropItemEvent event) {
        if (plugin.isFlightGem(event.getItemDrop())) {
            final Player player = event.getPlayer();
            plugin.restoreFlightSetting(player);
            plugin.info("Drop by " + player.getName(), player.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onPickup(final PlayerPickupItemEvent event) {
        final Item item = event.getItem();
        if (plugin.isFlightGem(item)) {
            final Player player = event.getPlayer();
            plugin.trackGem(player);
            plugin.info("Pickup by " + player.getName(), player.getLocation());

            if (playerWillPickupIntoHand(player)) {
                plugin.allowFlight(player);
            }
        }
    }

    private boolean playerWillPickupIntoHand(final Player player) {
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

    @EventHandler(ignoreCancelled = true)
    void onDeath(final PlayerDeathEvent event) {
        if (event.getDrops().contains(plugin.getGem())) {
            final Player player = event.getEntity();
            plugin.info("Death of " + player.getName(), player.getLocation());
            plugin.restoreFlightSetting(player);
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

    @EventHandler(ignoreCancelled = true)
    void onItemSpawn(final ItemSpawnEvent event) {
        final Item item = event.getEntity();
        if (plugin.isFlightGem(item)) {
            plugin.trackGem(item);
            plugin.info("Spawn", item.getLocation());
        }
    }

    private boolean isBottomClick(final InventoryClickEvent event) {
        return event.getRawSlot() >= event.getView().getTopInventory().getSize();
    }

    private boolean isBottomDrag(final InventoryDragEvent event) {
        final int topSize = event.getView().getTopInventory().getSize();
        for (final int x : event.getRawSlots()) {
            if (x < topSize) {
                return false;
            }
        }
        return true;
    }

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
                && action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && inventoryType != InventoryType.CRAFTING
                && inventoryType != InventoryType.WORKBENCH
                && inventoryType != InventoryType.BREWING) {

            if (enforce) {
                plugin.info("Inventory move by " + human.getName(), human.getLocation());
                event.setCancelled(true);
            } else {
                plugin.restoreFlightSetting(player);
            }
            return;
        }

        final boolean isGemHotBarSwap =
                action == InventoryAction.HOTBAR_SWAP
                && plugin.isFlightGem(human.getInventory().getItem(event.getHotbarButton()));

        // Ignore no-op swap
        if (InventoryType.SlotType.QUICKBAR.equals(event.getSlotType())
                && InventoryAction.HOTBAR_SWAP.equals(action)
                && event.getHotbarButton() == event.getSlot()
                ) {
            return;
        }

        if (currentIsGem || (isGemHotBarSwap && player.getInventory().getHeldItemSlot() == event.getHotbarButton())) {
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
                plugin.info("Inventory click by " + human.getName(), human.getLocation());
                event.setCancelled(true);
            } else {
                plugin.restoreFlightSetting(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onIventoryDrag(final InventoryDragEvent event) {
        final HumanEntity player = event.getWhoClicked();

        if (plugin.isFlightGem(event.getOldCursor()) &&
            !plugin.hasBypass(player) &&
            !isBottomDrag(event)) {

            plugin.info("Inventory drag by " + player.getName(), player.getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHit(final EntityDamageByEntityEvent event) {
        final Entity target = event.getEntity();

        if (target instanceof Player) {
            final Player player = (Player) target;

            if (plugin.isFlightGem(player.getItemInHand())) {

                if (plugin.hasBypass(player)) return;

                player.sendMessage(ChatColor.RED + "The gem slips from your fingers!");
                plugin.restoreFlightSetting(player);
                player.setItemInHand(null);
                player.getWorld().dropItemNaturally(player.getLocation(), plugin.getGem());
                plugin.info("Disarm of " + player.getName(), player.getLocation());
            }
        }
    }

    @EventHandler
    void onWorldChange(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final boolean playerLeftWorld = !plugin.isEnabledWorld(player.getWorld());
        if (playerLeftWorld && !plugin.hasBypass(player)) {
            plugin.info("World change by " + player.getName(), player.getLocation());
            plugin.takeGemFromPlayer(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onTeleport(final PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        final boolean playerLeftWorld = !plugin.isEnabledWorld(event.getTo().getWorld());
        if (playerLeftWorld && !plugin.hasBypass(player)) {
            plugin.info("Teleport by " + player.getName(), player.getLocation());
            plugin.takeGemFromPlayer(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onUnloadChunk(final ChunkUnloadEvent event) {
        for (final Entity e : event.getChunk().getEntities()) {
            if (plugin.isFlightGem(e)) {
                final Location location = e.getLocation();
                plugin.info("Chunk unload", location);
                e.remove();
                plugin.lightning(location);
                plugin.spawnGem();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onItemFramePlace(final PlayerInteractEntityEvent event) {
        final Entity target = event.getRightClicked();
        if (target instanceof ItemFrame) {
            final Player player = event.getPlayer();
            if (plugin.isFlightGem(player.getItemInHand()) && !plugin.hasBypass(player)) {
                plugin.info("Item frame by " + player.getName(), player.getLocation());
                event.setCancelled(true);
            }
        }
    }
}
