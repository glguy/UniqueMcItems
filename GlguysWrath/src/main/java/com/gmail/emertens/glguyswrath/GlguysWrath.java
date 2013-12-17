package com.gmail.emertens.glguyswrath;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public class GlguysWrath extends JavaPlugin {

	boolean hasBypass(final HumanEntity player) {
		return player.hasPermission("glguyswrath.bypass");
	}

	@Override
	public void onEnable() {
        saveDefaultConfig();

        final CursedListener cursedSword = new CursedListener(this);
		Bukkit.getPluginManager().registerEvents(cursedSword, this);
        this.getCommand("curseitem").setExecutor(cursedSword);

        final FlightGem flightGem = new FlightGem(this);
        if (!flightGem.initialize()) {
            getLogger().info("Failed to initialize flight gem");
            this.setEnabled(false);
        }
        Bukkit.getPluginManager().registerEvents(flightGem, this);
        this.getCommand("flightgem").setExecutor(flightGem);
        this.getCommand("setflightgemrespawn").setExecutor(flightGem);
        this.getCommand("findgem").setExecutor(flightGem);
	}
}
