Flight Gem Plugin
=================

This plugin provides a single item which grants its holder the ability to fly.
The player will be able to fly while holding the item in his hand. When the
item is not in use it is automatically returned to a designated dispenser for
other players to use.

Behaviors
---------

When not in use the item is returned to its dispenser. The item may not be
stored in containers, is lost upon logout, and may not be crafted. When the
item is unloaded or destroyed it will respawn.

When a player takes damage while holding the item in his hand the item will be
dropped.

When using Essentials, when a player becomes AFK the gem will be reset.

When using Cenotaph (or other death chest plugin), when a player dies the gem
is dropped directly on the ground.

Commands
--------

* **/findgem**
  * Reports the current location of the flight gem and points a player's
    compass to that location
* **/setflightgemrespawn**
  * Changes the respawn dispenser to the one currently pointed to by the player
* **/flightgem**
  * Creates a new flight item. Typically this command is unnecessary.

Permissions
-----------

* **flightgem.bypass**
  * Allows the player to bypass the inventory transfer restrictions of the flight item.
* **flightgem.creategem**
  * Allows the player to use the **/flightgem** command
* **flightgem.setflightrespawn**
  * Allows the player to use the **/setflightgemrespawn** command
* **flightgem.findgem**
  * Allows the player to use the **/findgem** command

Configuration
-------------

* **flightgem.respawn.message**
  * The message sent to all players when the flight item is returned to the dispenser
* **flightgem.object.name**
  * The display name of the flight item (Default: *Flight gem*)
* **flightgem.object.material**
  * The material of the flight item. (Default: *DIAMOND*)
* **flightgem.object.lore**
  * A list of the lore lines. This should never be empty to avoid easy cloning.
    (Default: *[Flight]*)
