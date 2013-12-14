package com.gmail.emertens.glguyswrath;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

/**
 * Created by emertens on 12/11/13.
 */
public class CursedListener implements Listener, CommandExecutor {

    private static final int EXP_MAX = 40;
    private static final String CONSOLE_CURSE_MSG = ChatColor.RED
            + "Only players can curse items";
    private static final String FAILED_DROP_MSG = ChatColor.GOLD
            + "Don't drop me; I don't do this on my own!";
    private static final String FAILED_INVENTORY_CLICK_MSG = ChatColor.RED
            + "The sword is terrified of being dropped and cuts your hand.";
    private static final String DEATH_CLING_MSG = ChatColor.RED
            + "The sword clings to you.";
    private static final String FAILED_EAT_MSG = ChatColor.GOLD
            + "There's no time for food now!";
    private static final String ATTACK_NONPLAYER_MSG = ChatColor.RED
            + "The sword demands only player blood and cuts your hand instead!";
    private static final String FAILED_PICKUP_MSG = ChatColor.RED
            + "The sword consumes an item to hold off its hunger";
    private static final String LORE1 = ChatColor.DARK_PURPLE + "Cursed";
    private static final String CURSED_ITEM_NAME = "glguy's wrath";

    private final GlguysWrath plugin;

    public CursedListener(GlguysWrath plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (command.getName().equalsIgnoreCase("curseitem") && args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                curseItem(player.getItemInHand());
            } else {
                sender.sendMessage(CONSOLE_CURSE_MSG);
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean isCursed(final ItemStack stack) {
        if (stack == null)
            return false;
        final ItemMeta im = stack.getItemMeta();
        if (im == null)
            return false;
        final String name = im.getDisplayName();
        if (name == null || !name.equals(CURSED_ITEM_NAME))
            return false;
        final List<String> lore = im.getLore();
        if (lore == null || lore.isEmpty() || !lore.get(0).equals(LORE1))
            return false;
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(final PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        if (plugin.hasBypass(player)) {
            return;
        }

        if (isCursed(event.getItemDrop().getItemStack())) {
            player.sendMessage(FAILED_DROP_MSG);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryTransfer(final InventoryClickEvent event) {

        if (!isCursed(event.getCurrentItem())) {
            return;
        }

        final HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        final Player player = (Player) who;

        if (plugin.hasBypass(player)) {
            return;
        }

        player.sendMessage(FAILED_INVENTORY_CLICK_MSG);
        player.damage(1);
        event.setCancelled(true);

        // The close inventory event must not be called while handling inventory
        // click events
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                player.closeInventory();
            }
        });
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        final List<ItemStack> drops = event.getDrops();
        final Player victim = event.getEntity();
        final ItemStack victimWrath = findCursedItem(drops);
        final ItemStack transferItem;

        // Couldn't find wrath, maybe the killer was holding it
        if (victimWrath == null) {
            final Player killer = victim.getKiller();
            if (killer == null) {
                return; // No killer, no wrath found, all done
            }

            final ItemStack murderWeapon = killer.getItemInHand();
            if (!isCursed(murderWeapon)) {
                // Victim wasn't cursed and wrath wasn't murder weapon, all done
                return;
            }

            transferItem = murderWeapon;
            killer.getInventory().remove(murderWeapon);

            event.setDeathMessage(killer.getDisplayName()
                    + " left the cursed sword in " + victim.getDisplayName()
                    + "'s chest");
        } else {
            drops.remove(victimWrath);
            transferItem = victimWrath;
        }

        // The item must be added to the player's inventory AFTER the death
        // event is completed
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                victim.getInventory().addItem(transferItem);
                victim.sendMessage(DEATH_CLING_MSG);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(final EntityDamageByEntityEvent event) {

        // Only consider events where a player is damaging someone
        final Entity damager = event.getDamager();
        if (!(damager instanceof Player)) {
            return;
        }
        final Player player = (Player) damager;

        // Check permissions
        if (plugin.hasBypass(player)) {
            return;
        }

        final ItemStack itemStack = player.getItemInHand();
        if (isCursed(itemStack)) {
            itemStack.setDurability((short) 0);

            if (event.getEntity() instanceof Player) {
                if (getCursedExp(itemStack) < EXP_MAX) {
                    event.setDamage(1);
                } else {
                    event.setDamage(100);
                    setCursedExp(itemStack, 0);
                }
            } else {
                player.damage(2);
                event.setCancelled(true);
                player.sendMessage(ATTACK_NONPLAYER_MSG);
            }
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onExpPickup(final PlayerExpChangeEvent event) {
        // Only worry about gains
        if (event.getAmount() < 0) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack cursed = findCursedItem(player.getInventory());
        if (cursed == null) {
            return;
        }

        final int cursedExp = getCursedExp(cursed);

        if (cursedExp >= EXP_MAX) {
            return;
        }

        final int divertedExp = Math.min(EXP_MAX - cursedExp, event.getAmount());
        final int newExp      = cursedExp + divertedExp;
        setCursedExp(cursed,newExp);
        event.setAmount(event.getAmount() - divertedExp);

        if (newExp == EXP_MAX) {
            player.sendMessage(ChatColor.GOLD + "My energy is restored!");
        } else {
            player.sendMessage(ChatColor.GOLD + "I still need  " + ChatColor.AQUA + (EXP_MAX - newExp) + ChatColor.GOLD + " more experience.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final PlayerPickupItemEvent event) {

        // Only check 5% of pickups
        if (Math.random() > 0.05) {
            return;
        }

        final Item item = event.getItem();
        if (FlightGem.isFlightGem(item.getItemStack())) return;

        final Player player = event.getPlayer();
        if (plugin.hasBypass(player) || !isCursedPlayer(player)) {
            return;
        }

        player.sendMessage(FAILED_PICKUP_MSG);
        event.setCancelled(true);
        item.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSwitch(final PlayerItemHeldEvent event) {

        final ItemStack heldItem = event.getPlayer().getInventory()
                .getItem(event.getNewSlot());
        if (!isCursed(heldItem)) {
            return;
        }
        final Player player = event.getPlayer();

        if (getCursedExp(heldItem) < EXP_MAX) {
            player.sendMessage(ChatColor.GOLD + "I'm weak; Let's find experience orbs.");
            return;
        }


        Player nearestPlayer = null;
        double distanceSquared = 0;

        for (final Entity e : player.getNearbyEntities(10, 10, 10)) {
            if (e instanceof Player) {
                Player neighbor = (Player) e;
                final double neighborDistance = player.getLocation()
                        .distanceSquared(neighbor.getLocation());
                if (nearestPlayer == null || neighborDistance < distanceSquared) {
                    nearestPlayer = neighbor;
                    distanceSquared = neighborDistance;
                }
            }
        }

        if (nearestPlayer == null) {
            player.sendMessage(ChatColor.GOLD + "There's no one near; let's find a victim.");
        } else {
            player.sendMessage(ChatColor.GOLD + "I'm ready! Let's kill "
                    + nearestPlayer.getDisplayName());
        }
    }


    private boolean isCursedPlayer(final Player player) {
        return findCursedItem(player.getInventory()) != null;
    }

    private int getCursedExp(final ItemStack item) {
        if (item == null)
            return 0;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        final List<String> lore = meta.getLore();
        if (lore == null || lore.size() < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(lore.get(1).substring(5), 10);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setCursedExp(final ItemStack item, final int exp) {
        if (item == null)
            return;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        final List<String> lore = meta.getLore();
        if (lore == null) {
            return;
        }
        final String newline = "Exp: " + exp;
        if (lore.size() < 2) {
            lore.add(newline);
        } else {
            lore.set(1, newline);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }




    private ItemStack findCursedItem(Iterable<ItemStack> xs) {
        for (final ItemStack x : xs) {
            if (isCursed(x)) {
                return x;
            }
        }
        return null;
    }


    private void curseItem(final ItemStack stack) {
        if (stack == null) {
            return;
        }

        final ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(CURSED_ITEM_NAME);
        meta.setLore(Arrays.asList(LORE1));
        stack.setItemMeta(meta);
    }
}
