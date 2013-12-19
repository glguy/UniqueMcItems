package com.gmail.emertens.flightgem;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FlightGemPlugin extends JavaPlugin {

    private Entity gemTarget = null;
    private FlightTracker flightTracker = new FlightTracker();
    private ItemStack gemPrototype = null;

    void allowFlight(final Player player) {
        flightTracker.allowFlight(player);
    }

    void restoreFlightSetting(final Player player) {
        flightTracker.restoreFlightSetting(player);
    }

    ItemStack getGem() {
        return gemPrototype;
    }

    boolean isEnabledWorld(final World world) {
        return world.getName().equalsIgnoreCase(getRespawnWorldName());
    }

    boolean hasBypass(final HumanEntity player) {
        return player.hasPermission("flightgem.bypass");
    }

    void info(final String message, final Location location) {
        if (location == null)
            getLogger().info(message);
        else
            getLogger().info(message
                    + " @ " + location.getWorld().getName()
                    + " " + location.getBlockX()
                    + ", " + location.getBlockY()
                    + ", " + location.getBlockZ()
            );
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (initialize()) {
            Bukkit.getPluginManager().registerEvents(new FlightGem(this), this);
        } else {
            getLogger().info("Failed to initialize flight gem");
            setEnabled(false);
        }
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
                getLogger().info("Flight gem found in player inventory " + player.getName());
                return;
            }
        }

        final String worldName = getRespawnWorldName();
        if (worldName == null) {
            getLogger().info("Flight gem world unspecified");
            return;
        }

        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().info("Flight gem world incorrect");
            return;
        }

        for (final Item x : world.getEntitiesByClass(Item.class)) {
            if (isFlightGem(x.getItemStack())) {
                getLogger().info("Flight gem located on ground");
                gemTarget = x;
                return;
            }
        }

        final Inventory inventory = getSpawnDispenserInventory();
        if (inventory == null) {
            getLogger().info("No dispenser found while initializing gem");
            return;
        }

        if (inventory.contains(gemPrototype)) {
            getLogger().info("Flight gem found in dispenser");
            return;
        }

        getLogger().info("Unable to locate flight gem, generating new one");
        inventory.addItem(gemPrototype);
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

        final FileConfiguration config = getConfig();
        final String displayName;
        final String materialName;
        final List<?> loreLineObjects;
        final List<String> loreLines;

        try {
            displayName = (String) config.get("flightgem.object.name", "Flight Gem");
            materialName = (String) config.get("flightgem.object.material", "DIAMOND");
            loreLineObjects = (List<?>) config.get("flightgem.object.lore", Arrays.asList("Flight"));

            loreLines = new ArrayList<>(loreLineObjects.size());
            for (final Object x : loreLineObjects) {
                loreLines.add((String) x);
            }
        } catch (ClassCastException e) {
            return false;
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
    }

    void takeGemFromPlayer(final Player player) {
        final Inventory inventory = player.getInventory();
        boolean hadGem = inventory.contains(gemPrototype);
        while (inventory.contains(gemPrototype)) {
            inventory.remove(gemPrototype);
        }

        if (hadGem) {
            restoreFlightSetting(player);
            spawnGem();
            getLogger().info("Flight gem removed gem from " + player.getName());
        }
    }

    boolean isFlightGem(final ItemStack itemStack) {
        return gemPrototype.isSimilar(itemStack);
    }

    boolean isFlightGem(final Item item) {
        return isFlightGem(item.getItemStack());
    }

    boolean isFlightGem(final Entity entity) {
        return entity instanceof Item && isFlightGem((Item)entity);
    }

    void lightning(final Location location) {
        location.getWorld().strikeLightningEffect(location);
    }

    void trackGem(final Entity e) {
        gemTarget = e;
    }

    /**
     * Attempt to return the gem to its home dispenser.
     */
    void spawnGem() {
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


    private String getSpawnMessage() {
        final Object o = getConfig().get("flightgem.respawn.message");
        if (o instanceof String) {
            return ChatColor.translateAlternateColorCodes('&', (String) o);
        } else {
            return null;
        }
    }

    String getRespawnWorldName() {
        final Object w = getConfig().get("flightgem.respawn.world");
        if (w instanceof String) {
            return (String) w;
        } else {
            return null;
        }
    }

    private Block getRespawnBlock() {
        final FileConfiguration config = getConfig();

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
        final FileConfiguration config = getConfig();
        config.set("flightgem.respawn.world", world);
        config.set("flightgem.respawn.x", x);
        config.set("flightgem.respawn.y", y);
        config.set("flightgem.respawn.z", z);
        saveConfig();
    }
}
