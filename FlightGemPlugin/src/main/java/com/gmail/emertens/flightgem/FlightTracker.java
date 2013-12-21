package com.gmail.emertens.flightgem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright 2013 Eric Mertens
 *
 * This class implements the tracking of granting and restoring flight settings.
 */
final class FlightTracker {

    private final Map<String, Boolean> oldAllowFlightSettings = new HashMap<>();
    private FlightGemPlugin plugin;

    public FlightTracker(final FlightGemPlugin plugin) {
        this.plugin = plugin;
    }

    void restoreFlightSetting(final Player player) {
        final String name = player.getName();
        final Boolean wasFlying = oldAllowFlightSettings.get(name);
        if ((wasFlying == null || !wasFlying) && player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.sendMessage(ChatColor.RED + "You feel drawn to the earth.");
            player.playSound(player.getLocation(), Sound.FIZZ, 1, 1);
        }
        oldAllowFlightSettings.remove(name);
    }

    void allowFlight(final Player player) {

        final boolean wasFlying = player.getAllowFlight();
        final String name = player.getName();

        if (!oldAllowFlightSettings.containsKey(name)) {
            oldAllowFlightSettings.put(name, wasFlying);
        }

        if (!wasFlying) {
            player.setAllowFlight(true);
            player.playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1, 1);
            player.sendMessage(ChatColor.GREEN + "You feel as light as air!");
        }
    }

    void verifyStatus() {
        final List<Player> players = new ArrayList<>();
        for (final String name : oldAllowFlightSettings.keySet()) {
            final Player player = Bukkit.getPlayer(name);
            if (player != null) {
                if (!plugin.isFlightGem(player.getItemInHand())) {
                    players.add(player);
                }
            }
        }
        for (final Player player : players) {
            plugin.info("Watchdog caught " + player.getName(), player.getLocation());
            restoreFlightSetting(player);
        }
    }
}
