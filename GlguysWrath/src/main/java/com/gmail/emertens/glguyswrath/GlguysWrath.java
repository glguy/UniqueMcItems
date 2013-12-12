package com.gmail.emertens.glguyswrath;

import java.util.Arrays;
import java.util.List;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public class GlguysWrath extends JavaPlugin {


	boolean hasBypass(final Player player) {
		return player.hasPermission("glguyswrath.bypass");
	}

	@Override
	public void onEnable() {
        final CursedListener cursedSword = new CursedListener(this);
		Bukkit.getPluginManager().registerEvents(cursedSword, this);
        this.getCommand("curseitem").setExecutor(cursedSword);

        final FlightGem flightGem = new FlightGem(this);
        Bukkit.getPluginManager().registerEvents(flightGem, this);
        this.getCommand("flightgem").setExecutor(flightGem);
	}


}
