# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

## [1.1.0+26.3] - 2026-11-16

### Added

- Add a state for the whole mod, on by default, with a **Toggle teleport** key binding (unbound by default) and a setting on the configuration screen. While it is off, `/tp @c` and Click TP do not move the player; `get`, the menu, `sethome` and `settemp` keep working.

### Changed

- The singleplayer and server defaults and the reset rules now apply to the whole mod instead of Click TP, and both defaults are on. A configuration file from 1.0.0 starts with the new defaults, because its values belonged to Click TP.
- The Click TP key no longer changes Click TP where teleporting is disabled or turned off; it only shows why.

### Fixed

- Fix that `/tp @c` could not be turned off in singleplayer.

## [1.0.0+26.3] - 2026-11-16

### Added

- Add `/tp @c` and `/teleport @c`, which teleport the local player on the client only, whatever the player's permissions are. They accept vanilla `/tp` arguments: absolute, relative and local coordinates, rotations, `facing`, and entity destinations such as player names, UUIDs and target selectors evaluated on the client.
- Reach players and entities the client has not loaded through the spectator teleport while spectating, or through the exact position the locator bar shows. Otherwise, report what the locator bar knows.
- Add `/tp @c back [client|server|death]`, which returns to the position before the last client teleport, the last server teleport or the last death.
- Add `/tp @c spawn`, `/tp @c worldspawn`, `/tp @c home` and `/tp @c temp`, and `/tp @c sethome` and `/tp @c settemp` to save home and temp. Respawn points, home and temp are saved per world and server.
- Add `get` at the end of every destination to show it without teleporting.
- Add the `/tp @c` menu of clickable destinations and the `/tp @c entities` list.
- Add Click TP, which teleports the player to the right-clicked block within a configurable reach, off by default, with a translucent preview of the player at the destination. Sneaking turns it off temporarily.
- Add key bindings, unbound by default, to toggle Click TP and to open the configuration screen.
- Add a configuration screen built from vanilla widgets, also available through Mod Menu, with Click TP defaults, reset rules, and multiplayer modes (disabled by default, whitelist, blacklist) with a server list.
- Add English and Simplified Chinese translations.
