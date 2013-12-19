package com.gmail.emertens.flightgem;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public class FlightGemPlugin extends JavaPlugin {

	boolean hasBypass(final HumanEntity player) {
		return player.hasPermission("flightgem.bypass");
	}

	@Override
	public void onEnable() {
        saveDefaultConfig();

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
