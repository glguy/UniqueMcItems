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
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class GlguysWrath extends JavaPlugin implements Listener {

	private final static String CURSED_ITEM_NAME = "glguy's wrath";
	private final static String LORE1 = ChatColor.DARK_PURPLE + "Cursed";
	
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
				sender.sendMessage(ChatColor.RED + "Only players can curse items");
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
			player.sendMessage(ChatColor.RED
					+ "You'd rather kill with the sword than drop it!");
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

		player.sendMessage(ChatColor.RED
				+ "The sword is terrified of being dropped and cuts your hand.");
		player.damage(1);
		event.setCancelled(true);

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
		final Player player = event.getEntity();

		ItemStack wrath = null;
		for (ItemStack x : drops) {
			if (isCursed(x)) {
				wrath = x;
				break;
			}
		}

		final ItemStack fwrath;

		// Couldn't find wrath, maybe the killer was holding it
		if (wrath == null) {
			final Player killer = player.getKiller();
			if (killer == null)
				return; // No killer, no wrath found, all done

			ItemStack x = killer.getItemInHand();
			if (isCursed(x)) {
				fwrath = x;
				killer.getInventory().remove(x);
			} else {
				return; // Killer didn't have it either, all done
			}

			event.setDeathMessage(killer.getDisplayName() + " left the cursed sword in " + player.getDisplayName() + "'s chest");
		} else {
			drops.remove(wrath);
			fwrath = wrath;
		}

		Bukkit.getScheduler().runTask(this, new Runnable() {
			@Override
			public void run() {
				player.getInventory().addItem(fwrath);
				player.sendMessage(ChatColor.RED + "The sword clings to you.");
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
			player.sendMessage(ChatColor.RED
					+ "The sword craves blood. Food can wait!");
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
				player.sendMessage(ChatColor.RED + "The sword demands player blood and cuts your hand instead!");
			}
		}
	}

	private boolean isCursedPlayer(final Player player) {
		for (ItemStack x : player.getInventory()) {
			if (isCursed(x)) {
				return true;
			}
		}
		return false;
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onPickup(final PlayerPickupItemEvent event) {
		
		// Only check 5% of pickups
		if (Math.random() > 0.05) { return; }

		final Player player = event.getPlayer();
		if (hasBypass(player) || !isCursedPlayer(player)) {
			return;
		}

		player.sendMessage(ChatColor.RED + "The sword consumes an item to hold off its hunger");
		event.setCancelled(true);
		event.getItem().remove();
	}

}
