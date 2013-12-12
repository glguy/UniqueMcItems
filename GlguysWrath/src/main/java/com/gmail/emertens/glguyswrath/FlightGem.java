package com.gmail.emertens.glguyswrath;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * Created by Eric Mertens on 12/11/13.
 */
public class FlightGem implements Listener, CommandExecutor {

    public static final String FLIGHT = "Flight";
    private GlguysWrath plugin;
    private final static String CONSOLE_CREATE_MSG = "Console can't create flight gems";

    public FlightGem(GlguysWrath plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (command.getName().equalsIgnoreCase("flightgem") && args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                createGem(player.getItemInHand());
            } else {
                sender.sendMessage(CONSOLE_CREATE_MSG);
            }
            return true;
        } else {
            return false;
        }    }

    private void createGem(ItemStack itemInHand) {
        final ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName("astro's gem");
        meta.setLore(Arrays.asList(FLIGHT));
        itemInHand.setItemMeta(meta);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSwitch(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final Inventory inventory = player.getInventory();

        final ItemStack heldItem = inventory.getItem(event.getNewSlot());
        if (isFlightGem(heldItem)) {
            player.setAllowFlight(true);
            player.sendMessage(ChatColor.GREEN + "You feel as light as air!");
            return;
        }

        final ItemStack previousItem = inventory.getItem(event.getPreviousSlot());
        if (isFlightGem(previousItem)) {
            player.setAllowFlight(false);
            player.sendMessage(ChatColor.RED + "You feel drawn to the earth.");
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final Inventory inventory = player.getInventory();
        final List<ItemStack> gems = new ArrayList<ItemStack>();
        for (final ItemStack x : inventory) {
            if (isFlightGem(x)) {
                gems.add(x);
            }
        }
        for (final ItemStack x : gems) {
            inventory.remove(x);
        }
        if (!gems.isEmpty()) {
            plugin.getLogger().info("Flight gem removed gem from " + player.getName() + " on logout");
        }
    }

    private boolean isFlightGem(final ItemStack heldItem) {
        if (heldItem == null) return false;
        final ItemMeta meta = heldItem.getItemMeta();
        if (meta == null) return false;
        final List<String> lore = meta.getLore();
        if (lore == null || lore.size() == 0) return false;
        return lore.get(0).equals(FLIGHT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDestroy(final ItemDespawnEvent event) {
        final ItemStack item = event.getEntity().getItemStack();
        if (isFlightGem(item)) {
            plugin.getLogger().info("Flight gem despawned");
            final Location location = event.getEntity().getLocation();
            location.getWorld().strikeLightning(location);
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onBurn(final EntityCombustEvent event) {
        final Entity entity = event.getEntity();
        if (entity instanceof Item) {
            final Item item = (Item)entity;

            if (isFlightGem(item.getItemStack())) {
                plugin.getLogger().info("Flight gem burned");
                final Location location = event.getEntity().getLocation();
                location.getWorld().strikeLightning(location);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        final Item drop = event.getItemDrop();
        if (isFlightGem(drop.getItemStack())) {
            plugin.getLogger().info("Flight gem dropped by " + event.getPlayer().getName() + " at " + drop.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final PlayerPickupItemEvent event) {
        final Item drop = event.getItem();
        if (isFlightGem(drop.getItemStack())) {
            plugin.getLogger().info("Flight gem picked up by " + event.getPlayer().getName() + " at " + drop.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(final PlayerDeathEvent event) {
        for (ItemStack x : event.getDrops()) {
            if (isFlightGem(x)) {
                plugin.getLogger().info("Flight gem dropped by dead " + event.getEntity().getName());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvMove(final InventoryPickupItemEvent event) {
        final Item item = event.getItem();
        if (isFlightGem(item.getItemStack())) {
            item.remove();
            plugin.getLogger().info("Prohibiting inventory pickup of flight gem");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final HumanEntity human = event.getWhoClicked();
        if (human instanceof Player) {
            final Player player = (Player)human;

            if (plugin.hasBypass(player)) return;

            if (!isFlightGem(event.getCurrentItem())) return;

            if (event.getInventory().getType() == InventoryType.PLAYER) return;

            player.sendMessage(ChatColor.RED + "It would be tragic to lock this gem away.");
            plugin.getLogger().info("Canceling flight gem inventory click");
            event.setCancelled(true);
        }
    }
}
