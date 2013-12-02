package com.gmail.emertens.glguyswrath;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class GlguysWrath extends JavaPlugin implements Listener {

	private static final String CONSOLE_CURSE_MSG = ChatColor.RED
			+ "Only players can curse items";
	private static final String FAILED_DROP_MSG = ChatColor.RED
			+ "You'd rather kill with the sword than drop it!";
	private static final String FAILED_INVENTORY_CLICK_MSG = ChatColor.RED
			+ "The sword is terrified of being dropped and cuts your hand.";
	private static final String DEATH_CLING_MSG = ChatColor.RED
			+ "The sword clings to you.";
	private static final String FAILED_EAT_MSG = ChatColor.RED
			+ "The sword craves blood. Food can wait!";
	private static final String ATTACK_NONPLAYER_MSG = ChatColor.RED
			+ "The sword demands player blood and cuts your hand instead!";
	private static final String FAILED_PICKUP_MSG = ChatColor.RED
			+ "The sword consumes an item to hold off its hunger";
	private static final String CURSED_ITEM_NAME = "glguy's wrath";
	private static final String LORE1 = ChatColor.DARK_PURPLE + "Cursed";

	private void curseItem(final ItemStack stack) {
		if (stack == null) {
			return;
		}

		final ItemMeta meta = stack.getItemMeta();
		meta.setDisplayName(CURSED_ITEM_NAME);
		meta.setLore(Arrays.asList(LORE1));
		stack.setItemMeta(meta);
	}

	private boolean hasBypass(final Player player) {
		return player.hasPermission("glguyswrath.bypass");
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

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
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

		if (hasBypass(player)) {
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

		if (hasBypass(player)) {
			return;
		}

		player.sendMessage(FAILED_INVENTORY_CLICK_MSG);
		player.damage(1);
		event.setCancelled(true);

		// The close inventory event must not be called while handling inventory
		// click events
		Bukkit.getScheduler().runTask(this, new Runnable() {
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
		Bukkit.getScheduler().runTask(this, new Runnable() {
			@Override
			public void run() {
				victim.getInventory().addItem(transferItem);
				victim.sendMessage(DEATH_CLING_MSG);
			}
		});
	}

	@EventHandler(ignoreCancelled = true)
	public void onEat(final PlayerItemConsumeEvent event) {
		final Player player = event.getPlayer();

		if (hasBypass(player) || !isCursedPlayer(player)) {
			return;
		}

		switch (event.getItem().getType()) {
		case CARROT:
		case POTATO:
		case MELON:
		case PORK:
		case BREAD:
		case APPLE:
		case GOLDEN_APPLE:
		case GOLDEN_CARROT:
		case MUSHROOM_SOUP:
		case COOKIE:
		case GRILLED_PORK:
		case BAKED_POTATO:
		case RAW_CHICKEN:
		case RAW_BEEF:
		case RAW_FISH:
		case COOKED_BEEF:
		case COOKED_CHICKEN:
		case COOKED_FISH:
		case ROTTEN_FLESH:
			event.setCancelled(true);
			player.sendMessage(FAILED_EAT_MSG);
		default:
		}
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
		if (hasBypass(player)) {
			return;
		}

		final ItemStack itemStack = player.getItemInHand();
		if (isCursed(itemStack)) {
			itemStack.setDurability((short) 0);

			if (event.getEntity() instanceof Player) {
				event.setDamage(100);
			} else {
				player.damage(2);
				event.setCancelled(true);
				player.sendMessage(ATTACK_NONPLAYER_MSG);
			}
		}
	}

	private ItemStack findCursedItem(Iterable<ItemStack> xs) {
		for (final ItemStack x : xs) {
			if (isCursed(x)) {
				return x;
			}
		}
		return null;
	}

	private boolean isCursedPlayer(final Player player) {
		return findCursedItem(player.getInventory()) != null;
	}

	@EventHandler(ignoreCancelled = true)
	public void onPickup(final PlayerPickupItemEvent event) {

		// Only check 5% of pickups
		if (Math.random() > 0.05) {
			return;
		}

		final Player player = event.getPlayer();
		if (hasBypass(player) || !isCursedPlayer(player)) {
			return;
		}

		player.sendMessage(FAILED_PICKUP_MSG);
		event.setCancelled(true);
		event.getItem().remove();
	}

	@EventHandler(ignoreCancelled = true)
	public void onItemSwitch(final PlayerItemHeldEvent event) {
		
		final ItemStack heldItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
		if (!isCursed(heldItem)) {
			return;
		}
		
		final Player player = event.getPlayer();
		final List<Entity> nearby = player.getNearbyEntities(10, 10, 10);
		
		Player nearestPlayer = null;
		double distanceSquared = 0;
		
		for (Entity e : nearby) {
			if (e instanceof Player) {
				Player neighbor = (Player) e;
				final double neighborDistance = player.getLocation().distanceSquared(neighbor.getLocation());
				if (nearestPlayer == null || neighborDistance < distanceSquared) {
					nearestPlayer = neighbor;
					distanceSquared = neighborDistance;
				}
			}
		}
		
		if (nearestPlayer == null) {
			return;
		}
		
		player.sendMessage(ChatColor.RED + "You feel an urge kill " + nearestPlayer.getDisplayName());
	}
}
