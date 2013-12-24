package com.gmail.emertens.flightgem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Copyright 2013 Eric Mertens
 *
 * This class implements the tracking of granting and restoring flight settings.
 */
final class FlightTracker {

    private FlightGemPlugin plugin;
    private Set<String> fliers = new HashSet<>();

    FlightTracker(final FlightGemPlugin plugin) {
        this.plugin = plugin;
    }

    void restoreFlightSetting(final Player player) {

        if (GameMode.CREATIVE.equals(player.getGameMode())) return;

        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.sendMessage(ChatColor.RED + "You feel drawn to the earth.");
            player.playSound(player.getLocation(), Sound.FIZZ, 1, 1);
            fliers.remove(player.getName());
        }
    }

    void allowFlight(final Player player) {

        if (GameMode.CREATIVE.equals(player.getGameMode())) return;

        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            player.playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1, 1);
            player.sendMessage(ChatColor.GREEN + "You feel as light as air!");
            fliers.add(player.getName());
        }
    }

    void verifyStatus() {
        final List<Player> players = new ArrayList<>();
        for (final String name : fliers) {
            final Player player = Bukkit.getPlayer(name);
            if (player != null) {
                if (player.getAllowFlight() && !plugin.isFlightGem(player.getItemInHand())) {
                    players.add(player);
                }
            }
        }
        for (final Player player : players) {
            plugin.info("Watchdog caught", player);
            restoreFlightSetting(player);
        }
    }
}
