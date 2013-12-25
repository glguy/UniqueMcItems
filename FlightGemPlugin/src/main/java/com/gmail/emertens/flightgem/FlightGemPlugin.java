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
import java.util.List;
import java.util.logging.Level;

/**
 * Copyright 2013 Eric Mertens
 *
 * This plugin implements a unique server-wide shared
 * object which grants its holder flight.
 */
public final class FlightGemPlugin extends JavaPlugin {

    private static final String FAILED_TO_INITIALIZE_FLIGHT_GEM = "Failed to initialize flight gem";
    private static final String COMPASS_FALLEN_GEM = ChatColor.GREEN + "Your compass points to the fallen gem.";
    private static final String COMPASS_DISPENSER_GEM = ChatColor.GREEN + "Your compass points to the dispenser.";
    private static final String COMPASS_NO_GEM = ChatColor.RED + "The gem is nowhere to be found.";
    private static final String COMPASS_PLAYER_FORMAT = ChatColor.GREEN + "Your compass points to " + ChatColor.RESET + "%1$s" + ChatColor.GREEN + ".";
    private static final String SUCCESS = ChatColor.GREEN + "Success";
    private static final String ERR_INVENTORY_FULL = ChatColor.RED + "No room in inventory";
    private static final String ERR_NO_CONTAINER_FOUND = ChatColor.RED + "No container block found";
    private static final String ERR_WORLD_NOT_FOUND = ChatColor.RED + "No such world";
    private static final String ERR_PLAYER_NOT_FOUND = ChatColor.RED + "Player not found";

    private static final String BYPASS_PERMISSION = "flightgem.bypass";

    private static final String CONFIG_RESPAWN_WORLD = "flightgem.respawn.world";
    private static final String CONFIG_RESPAWN_X = "flightgem.respawn.x";
    private static final String CONFIG_RESPAWN_Y = "flightgem.respawn.y";
    private static final String CONFIG_RESPAWN_Z = "flightgem.respawn.z";
    private static final String CONFIG_RESPAWN_MESSAGE = "flightgem.respawn.message";
    private static final String CONFIG_OBJECT_NAME = "flightgem.object.name";
    private static final String CONFIG_OBJECT_MATERIAL = "flightgem.object.material";
    private static final String CONFIG_OBJECT_LORE = "flightgem.object.lore";

    private static final String DEFAULT_OBJECT_NAME = "Flight Gem";
    private static final String DEFAULT_OBJECT_MATERIAL = "DIAMOND";
    private static final List<String> DEFAULT_OBJECT_LORE = Arrays.asList("Flight");
    private static final String DEFAULT_RESPAWN_MESSAGE = "&7Flight gem has returned!";

    private final FlightTracker flightTracker = new FlightTracker(this);
    private Entity gemTarget = null;
    private ItemStack gemPrototype = null;

    void allowFlight(final Player player) {
        gemTarget = player;
        flightTracker.allowFlight(player);
    }

    void restoreFlightSetting(final Player player) {
        flightTracker.restoreFlightSetting(player);
    }

    ItemStack getGem() {
        return gemPrototype;
    }

    boolean isEnabledWorld(final World world) {
        return world.equals(getDispenserWorld());
    }

    boolean hasBypass(final HumanEntity player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    void warning(final String message, final HumanEntity player) {
        log(message + ", " + player.getName(), player.getLocation(), Level.WARNING);
    }

    void info(final String message, final HumanEntity player) {
        info(message + ", " + player.getName(), player.getLocation());
    }

    void info(final String message, final Location location) {
        log(message, location, Level.INFO);
    }

    void log(final String message, final Location location, final Level level) {
        getLogger().log(level,
                String.format("%1$s @ %2$s %3$d %4$d %5$d",
                        message,
                        location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (initialize()) {
            Bukkit.getPluginManager().registerEvents(new FlightGem(this), this);
        } else {
            getLogger().info(FAILED_TO_INITIALIZE_FLIGHT_GEM);
            setEnabled(false);
        }

        final Runnable verifier = new Runnable() {
            @Override
            public void run() {
                flightTracker.verifyStatus();
            }
        };
        Bukkit.getScheduler().runTaskTimer(this, verifier, 20, 20);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        switch (command.getName()) {
            case "flightgem":
                return createGemCommand(sender, args);
            case "setflightgemrespawn":
                return setFlightGemDispenserCommand(sender, args);
            case "findgem":
                return findGemCommand(sender, args);
            default:
                return false;
        }
    }

    private boolean createGemCommand(final CommandSender sender, final String[] args) {

        if (args.length >= 2) {
            return false;
        }

        final Player player = selectPlayer(sender, args);
        final String msg;

        if (player == null) {
            msg = ERR_PLAYER_NOT_FOUND;
        } else if (player.getInventory().addItem(gemPrototype).isEmpty()) {
            msg = SUCCESS;
        } else {
            msg = ERR_INVENTORY_FULL;
        }
        sender.sendMessage(msg);

        return true;
    }


    /**
     * When sender is a player, return that player when no arguments are provided.
     * When an argument is provided attempt to use that argument as the player name.
     */
    private static Player selectPlayer(final CommandSender sender, final String[] args) {
        if (sender instanceof Player && args.length == 0) {
            return (Player) sender;
        } else if (args.length == 1) {
            return Bukkit.getPlayer(args[0]);
        } else {
            return null;
        }
    }

    private boolean initialize() {

        initializeVerbosity();

        if (initializePrototype()) {
            initializeGemTarget();
            return true;
        }

        return false;
    }

    private void initializeVerbosity() {
        try {
            final Level level = Level.parse (getConfig().getString("flightgem.logginglevel"));
            getLogger().setLevel(level);
        } catch (IllegalArgumentException e) {
            getLogger().warning("flightgem.logginglevel: bad level ignored");
        }
    }

    void initializeGemTarget() {

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().contains(gemPrototype)) {
                trackGem(player);
                info("Initial player", player);
                return;
            }
        }

        final World world = getDispenserWorld();
        if (world == null) {
            return;
        }

        for (final Item x : world.getEntitiesByClass(Item.class)) {
            if (isFlightGem(x.getItemStack())) {
                info("Gem on ground", x.getLocation());
                trackGem(x);
                return;
            }
        }

        resetDispenser();
    }

    private void resetDispenser() {
        final Block dispenserBlock = getDispenserBlock();
        final Inventory inventory = getDispenserInventory(dispenserBlock);
        if (inventory != null) {
            info("Dispenser reset", dispenserBlock.getLocation());
            inventory.clear();
            // Do an addItem and not setContents to get the block update
            inventory.addItem(gemPrototype);
        }
    }


    private boolean findGemCommand(final CommandSender sender, final String[] args) {

        if (args.length > 0) {
            return false;
        }

        final Player player = sender instanceof Player ? (Player) sender : null;
        findGemSetCompass(sender, player);

        return true;
    }

    private boolean setFlightGemDispenserCommand(final CommandSender sender, final String[] args) {

        if (args.length > 0) {
            return false;
        }

        if (sender instanceof Player && args.length == 0) {
            final Player player = (Player) sender;

            final BlockIterator bi = new BlockIterator(player, 10);
            while (bi.hasNext()) {
                final Block b = bi.next();
                if (b.getState() instanceof InventoryHolder) {
                    setDispenserBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                    player.sendMessage(SUCCESS);
                    return true;
                }
            }
            player.sendMessage(ERR_NO_CONTAINER_FOUND);
            return true;
        } else if (args.length == 4) {
            try {
                final World w = Bukkit.getWorld(args[0]);
                final int x = Integer.parseInt(args[1]);
                final int y = Integer.parseInt(args[2]);
                final int z = Integer.parseInt(args[3]);

                if (w == null) {
                    sender.sendMessage(ERR_WORLD_NOT_FOUND);
                } else {
                    setDispenserBlock(w.getName(), x, y, z);
                    sender.sendMessage(SUCCESS);
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private void findGemSetCompass(final CommandSender sender, final Player player) {

        final String msg;
        final Location location;

        if (gemTarget == null) {
            final Block dispenserBlock = getDispenserBlock();
            final Inventory inventory = getDispenserInventory(dispenserBlock);
            if (inventory != null && inventory.contains(gemPrototype)) {
                msg = COMPASS_DISPENSER_GEM;
                location = dispenserBlock.getLocation();
            } else {
                msg = COMPASS_NO_GEM;
                location = null;
            }
        } else {
            location = gemTarget.getLocation();
            if (gemTarget instanceof Player) {
                final Player holder = (Player) gemTarget;
                msg = String.format(COMPASS_PLAYER_FORMAT, holder.getDisplayName());
            } else {
                msg = COMPASS_FALLEN_GEM;
            }
        }
        if (player != null && location != null) {
            player.setCompassTarget(location);
        }
        sender.sendMessage(msg);
    }

    private boolean initializePrototype() {

        final FileConfiguration config = getConfig();

        final String displayName = config.getString(CONFIG_OBJECT_NAME, DEFAULT_OBJECT_NAME);
        final String materialName = config.getString(CONFIG_OBJECT_MATERIAL, DEFAULT_OBJECT_MATERIAL);
        final List<?> loreLineObjects = config.getList(CONFIG_OBJECT_LORE, DEFAULT_OBJECT_LORE);

        final List<String> loreLines = new ArrayList<>(loreLineObjects.size());
        for (final Object x : loreLineObjects) {
            if (String.class.equals(x.getClass())) {
                loreLines.add((String) x);
            } else {
                getLogger().warning("Bad lore");
                return false;
            }
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
            info("Gem removed", player);
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

    static void lightning(final Location location) {
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
        resetDispenser();
        final String msg = getSpawnMessage();
        if (msg != null) {
            Bukkit.broadcastMessage(msg);
        }
    }

    private Inventory getDispenserInventory(final Block dispenserBlock) {
        if (dispenserBlock == null) {
            return null;
        }
        final BlockState state = dispenserBlock.getState();
        if (state instanceof InventoryHolder) {
            final InventoryHolder dispenser = (InventoryHolder) state;
            return dispenser.getInventory();
        } else {
            return null;
        }
    }


    private String getSpawnMessage() {
        final String o = getConfig().getString(CONFIG_RESPAWN_MESSAGE, DEFAULT_RESPAWN_MESSAGE);
        return ChatColor.translateAlternateColorCodes('&', o);
    }

    World getDispenserWorld() {
        final String w = getConfig().getString(CONFIG_RESPAWN_WORLD);
        if (w != null) {
            return Bukkit.getWorld(w);
        } else {
            return null;
        }
    }

    private Block getDispenserBlock() {
        final FileConfiguration config = getConfig();

        final World world = getDispenserWorld();
        final int x = config.getInt(CONFIG_RESPAWN_X);
        final int y = config.getInt(CONFIG_RESPAWN_Y);
        final int z = config.getInt(CONFIG_RESPAWN_Z);

        if (world != null) {
            return world.getBlockAt(x, y, z);
        } else {
            return null;
        }
    }

    private void setDispenserBlock(String world, int x, int y, int z) {

        final FileConfiguration config = getConfig();
        config.set(CONFIG_RESPAWN_WORLD, world);
        config.set(CONFIG_RESPAWN_X, x);
        config.set(CONFIG_RESPAWN_Y, y);
        config.set(CONFIG_RESPAWN_Z, z);
        saveConfig();
    }
}
