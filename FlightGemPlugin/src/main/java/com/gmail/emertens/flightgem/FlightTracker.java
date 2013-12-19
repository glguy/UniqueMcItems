package com.gmail.emertens.flightgem;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Copyright 2013 Eric Mertens
 *
 * This class implements all the event handlers needed for flight gem
 * behavior and safety.
 */
final class FlightTracker {

    private final Map<String, Boolean> oldAllowFlightSettings = new HashMap<>();

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
}