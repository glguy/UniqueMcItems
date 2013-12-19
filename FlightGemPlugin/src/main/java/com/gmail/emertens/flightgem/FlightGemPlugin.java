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

public final class FlightGemPlugin extends JavaPlugin {

    private static final String FAILED_TO_INITIALIZE_FLIGHT_GEM = "Failed to initialize flight gem";
    private static final String COMPASS_FALLEN_GEM = ChatColor.GREEN + "Your compass points to the fallen gem.";
    private static final String COMPASS_DISPENSER_GEM = ChatColor.GREEN + "Your compass points to the dispenser.";
    private static final String COMPASS_NO_GEM = ChatColor.RED + "The gem is nowhere to be found.";
    private static final String COMPASS_PLAYER_FORMAT = ChatColor.GREEN + "Your compass points to " + ChatColor.RESET + "%1$S" + ChatColor.GREEN + ".";
    private static final String BYPASS_PERMISSION = "flightgem.bypass";

    private final FlightTracker flightTracker = new FlightTracker();
    private Entity gemTarget = null;
    private ItemStack gemPrototype = null;
    private Block dispenserBlock = null;



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
        return world.equals(getDispenserWorld());
    }

    boolean hasBypass(final HumanEntity player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    void info(final String message, final Location location) {
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
            getLogger().info(FAILED_TO_INITIALIZE_FLIGHT_GEM);
            setEnabled(false);
        }
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
    private Player selectPlayer(final CommandSender sender, final String[] args) {
        if (sender instanceof Player && args.length == 0) {
            return (Player) sender;
        } else if (args.length == 1) {
            return Bukkit.getPlayer(args[0]);
        } else {
            return null;
        }
    }


    public boolean initialize() {
        dispenserBlock = getDispenserBlock();
        if (!initializePrototype()) {
            return false;
        }
        initializeGemTarget();
        return true;
    }

    void initializeGemTarget() {

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().contains(gemPrototype)) {
                trackGem(player);
                info("Initial player is " + player.getName(), player.getLocation());
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
        final Inventory inventory = getDispenserInventory();
        if (inventory != null) {
            info("Dispenser reset", dispenserBlock.getLocation());
            inventory.setContents(new ItemStack[]{gemPrototype});
        }
    }


    private boolean findGemCommand(final CommandSender sender, final String[] args) {

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
                    player.sendMessage(ChatColor.GREEN + "Success");
                    return true;
                }
            }
            player.sendMessage(ChatColor.RED + "Failure");
            return true;
        } else if (args.length == 4) {
            try {
                final World w = Bukkit.getWorld(args[0]);
                final int x = Integer.parseInt(args[1]);
                final int y = Integer.parseInt(args[2]);
                final int z = Integer.parseInt(args[3]);

                if (w == null) {
                    sender.sendMessage(ChatColor.RED + "No such world");
                } else {
                    setDispenserBlock(w.getName(), x, y, z);
                    sender.sendMessage(ChatColor.GREEN + "Success");
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

        if (gemTarget == null) {
            final Inventory inventory = getDispenserInventory();
            if (inventory != null && inventory.contains(gemPrototype)) {
                sender.sendMessage(COMPASS_DISPENSER_GEM);
                if (player != null) player.setCompassTarget(dispenserBlock.getLocation());
            } else {
                sender.sendMessage(COMPASS_NO_GEM);
            }
        } else {
            if (player != null) player.setCompassTarget(gemTarget.getLocation());

            if (gemTarget instanceof Player) {
                final Player holder = (Player) gemTarget;
                sender.sendMessage(String.format(COMPASS_PLAYER_FORMAT, holder.getDisplayName()));
            } else {
                sender.sendMessage(COMPASS_FALLEN_GEM);
            }
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
            info("Gem removed from " + player.getName(), player.getLocation());
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

    private Inventory getDispenserInventory() {
        final BlockState state = dispenserBlock.getState();
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

    World getDispenserWorld() {
        final Object w = getConfig().get("flightgem.respawn.world");
        if (w instanceof String) {
            return Bukkit.getWorld((String) w);
        } else {
            return null;
        }
    }

    private Block getDispenserBlock() {
        final FileConfiguration config = getConfig();

        final World world = getDispenserWorld();
        final Object x = config.get("flightgem.respawn.x");
        final Object y = config.get("flightgem.respawn.y");
        final Object z = config.get("flightgem.respawn.z");

        if (world != null && x != null && y != null && z != null &&
                x instanceof Integer &&
                y instanceof Integer &&
                z instanceof Integer) {

            return world.getBlockAt((Integer) x, (Integer) y, (Integer) z);
        }

        return null;
    }

    private void setDispenserBlock(String world, int x, int y, int z) {

        dispenserBlock = Bukkit.getWorld(world).getBlockAt(x, y, z);

        final FileConfiguration config = getConfig();
        config.set("flightgem.respawn.world", world);
        config.set("flightgem.respawn.x", x);
        config.set("flightgem.respawn.y", y);
        config.set("flightgem.respawn.z", z);
        saveConfig();
    }
}
