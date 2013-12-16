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
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Eric Mertens on 12/11/13.
 *
 * This class provides the functionality of a gem that grants flight
 * when held, but which can not be horded.
 *
 */
public class FlightGem implements Listener, CommandExecutor {

    public static final String FLIGHT = "Flight";
    private GlguysWrath plugin;
    private final static String CONSOLE_CREATE_MSG = "Console can't create flight gems";
    private Entity gemTarget = null;

    public FlightGem(GlguysWrath plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (command.getName().equalsIgnoreCase("flightgem")) {

            final Player player;
            if (sender instanceof Player && args.length == 0) {
                player = (Player) sender;
            } else if (args.length == 1) {
                player = Bukkit.getPlayer(args[0]);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found");
                    return true;
                }
            } else {
                return false;
            }

            final ItemStack gem = new ItemStack(Material.DIAMOND);
            createGem(gem);

            final Map<Integer, ItemStack> leftover = player.getInventory().addItem(gem);
            if (leftover != null && !leftover.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No room in inventory");
            }

            return true;
        } else if (command.getName().equalsIgnoreCase("setflightgemrespawn") && args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                for (final BlockIterator bi = new BlockIterator(player, 10); bi.hasNext(); ) {
                    final Block b = bi.next();
                    if (b.getState() instanceof InventoryHolder) {
                        setRespawnBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                        player.sendMessage(ChatColor.GREEN + "Success");
                        return true;
                    }
                }

                player.sendMessage(ChatColor.RED + "Failure");
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Console can't set respawn location");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("findgem") && args.length == 0) {
            if (sender instanceof Player) {
                final Player player = (Player) sender;
                findGemCommand(player, player);
            } else {
                findGemCommand(sender, null);
            }
            return true;
        } else {
            return false;
        }
    }

    private void findGemCommand(final CommandSender sender, final Player player) {
        if (gemTarget == null) {
            final Block block = getRespawnBlock();
            if (block == null) {
                sender.sendMessage(ChatColor.RED + "The gem is nowhere to be found.");
            } else {
                final BlockState state = block.getState();
                if (state instanceof InventoryHolder) {
                    final InventoryHolder holder = (InventoryHolder)state;
                    for (final ItemStack x : holder.getInventory()) {
                        if (isFlightGem(x)) {
                            sender.sendMessage(ChatColor.GREEN + "Your compass points to the dispenser.");
                            if (player != null) player.setCompassTarget(block.getLocation());
                            return;
                        }
                    }
                }
                sender.sendMessage(ChatColor.RED + "The gem is nowhere to be found.");
            }
        } else if (gemTarget instanceof Player) {
            final Player holder = (Player)gemTarget;
            if (player != null) player.setCompassTarget(gemTarget.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Your compass points to " + ChatColor.RESET + holder.getDisplayName() + ChatColor.GREEN + ".");
        } else {
            if (player != null) player.setCompassTarget(gemTarget.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Your compass points to the fallen gem.");
        }
    }

    private void createGem(ItemStack itemInHand) {
        final ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName("astro's gem");
        meta.setLore(Arrays.asList(FLIGHT));
        itemInHand.setItemMeta(meta);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (plugin.hasBypass(player)) return;

        final List<ItemStack> gems = new ArrayList<ItemStack>();
        final Inventory inventory = player.getInventory();

        for (final ItemStack x : inventory) {
            if (isFlightGem(x)) {
                gems.add(x);
            }
        }

        for (final ItemStack x : gems) {
            inventory.remove(x);
            plugin.getLogger().info("Removing a flight gem on join from " + player.getName());
        }
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
        takeGemFromPlayer(player);
    }

    private void takeGemFromPlayer(Player player) {
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
            respawnGem();
            plugin.getLogger().info("Flight gem removed gem from " + player.getName());
        }
    }

    static boolean isFlightGem(final ItemStack heldItem) {
        if (heldItem == null) return false;
        final ItemMeta meta = heldItem.getItemMeta();
        if (meta == null) return false;
        final List<String> lore = meta.getLore();
        return lore != null && !lore.isEmpty() && lore.get(0).equals(FLIGHT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDestroy(final ItemDespawnEvent event) {
        final ItemStack item = event.getEntity().getItemStack();
        if (isFlightGem(item)) {
            plugin.getLogger().info("Flight gem despawned");
            final Location location = event.getEntity().getLocation();
            location.getWorld().strikeLightning(location);
            respawnGem();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(final EntityCombustEvent event) {
        final Entity entity = event.getEntity();
        if (entity instanceof Item) {
            final Item item = (Item) entity;

            if (isFlightGem(item.getItemStack())) {
                plugin.getLogger().info("Flight gem burned");
                final Location location = event.getEntity().getLocation();
                location.getWorld().strikeLightning(location);
                respawnGem();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        final Item drop = event.getItemDrop();
        if (isFlightGem(drop.getItemStack())) {
            gemTarget = drop;
            final Player player = event.getPlayer();
            player.setAllowFlight(false);
            plugin.getLogger().info("Flight gem dropped by " + player.getName() + " at " + drop.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final PlayerPickupItemEvent event) {
        final Item drop = event.getItem();
        if (isFlightGem(drop.getItemStack())) {
            final Player player = event.getPlayer();
            gemTarget = player;
            plugin.getLogger().info("Flight gem picked up by " + player.getName() + " at " + drop.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(final PlayerDeathEvent event) {
        // XXX: Figure out what to do here
        for (ItemStack x : event.getDrops()) {
            if (isFlightGem(x)) {
                final Player player = event.getEntity();

                player.setAllowFlight(false);
                plugin.getLogger().info("Flight gem dropped by dead " + player.getName());
                event.getDrops().remove(x);

                final Item drop = player.getWorld().dropItemNaturally(player.getLocation(), x);
                gemTarget = drop;

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
            respawnGem();
        }
    }

    private void respawnGem() {
        gemTarget = null;
        final Block block = getRespawnBlock();
        if (block == null) return;
        final BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            final InventoryHolder dispenser = (InventoryHolder) state;
            final ItemStack gem = new ItemStack(Material.DIAMOND);
            createGem(gem);
            dispenser.getInventory().addItem(gem);
            final String msg = respawnMessage();
            if (msg != null) {
                Bukkit.broadcastMessage(msg);
            }
        }
    }

    private String respawnMessage() {
        final Object o = plugin.getConfig().get("flightgem.respawn.message");
        if (o instanceof String) {
            final String msg = (String) o;
            return ChatColor.translateAlternateColorCodes('&', msg);
        }
        return null;
    }

    private Block getRespawnBlock() {
        final FileConfiguration config = plugin.getConfig();

        final Object w = config.get("flightgem.respawn.world");
        final Object x = config.get("flightgem.respawn.x");
        final Object y = config.get("flightgem.respawn.y");
        final Object z = config.get("flightgem.respawn.z");

        if (w != null && x != null && y != null && z != null &&
                w instanceof String &&
                x instanceof Integer &&
                y instanceof Integer &&
                z instanceof Integer) {

            final World world = Bukkit.getWorld((String) w);

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
            if (plugin.hasBypass(player)) return;

            player.setAllowFlight(false);

            final InventoryType invType = event.getInventory().getType();
            final InventoryType.SlotType slotType = event.getSlotType();

            if (invType == InventoryType.CRAFTING && slotType == InventoryType.SlotType.CONTAINER ||
                invType == InventoryType.CRAFTING && slotType == InventoryType.SlotType.QUICKBAR) {
                return;
            }

            player.sendMessage(ChatColor.RED + "It would be tragic to lock this gem away.");
            plugin.getLogger().info("Canceling flight gem inventory click " + invType + ":" + slotType + " for " + player.getName());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIventoryDrag(final InventoryDragEvent event) {
        final HumanEntity human = event.getWhoClicked();
        if (human instanceof Player) {
            final Player player = (Player)human;

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

            final ItemStack gem = player.getItemInHand();
            if (isFlightGem(gem)) {

                if (plugin.hasBypass(player)) return;

                player.setAllowFlight(false);
                player.setItemInHand(null);
                gemTarget = player.getWorld().dropItemNaturally(player.getLocation(), gem);
                player.sendMessage(ChatColor.RED + "The gem slips from your fingers!");
                plugin.getLogger().info("Flight gem slipped from " + player.getName());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (event.getTo().getWorld().getName().equals("Snaketopia")) return;
        final Player player = event.getPlayer();
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
                    final Location location = getRespawnBlock().getLocation();
                    item.remove();
                    location.getWorld().strikeLightning(location);
                    respawnGem();
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
            if (!isFlightGem(player.getItemInHand())) return;
            event.setCancelled(true);
            plugin.getLogger().info("Prevented item frame placement by " + player.getName());
        }
    }
}
