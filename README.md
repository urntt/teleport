# teleport

A client-side Fabric mod for Minecraft: Java Edition that lets the player teleport without asking the server.

`/tp @c …` and `/teleport @c …` take the same arguments as vanilla `/tp`, but they only change your own position on your client, whatever your permissions are. Click TP teleports you to the block you right-click. Quick destinations take you back to where you were, to your home, to your respawn point and more.

## Warning

**This mod is disabled in multiplayer by default. To use it in multiplayer, change the settings on its configuration screen. In OneConfig mode, the mod is off everywhere until you turn it on, and then works on every server.**

A client teleport is not a server teleport. The mod moves your player on your client, and the movement packets your client sends anyway tell the server about the new position. The server, including the integrated server of a singleplayer world, checks that movement like any other. This mod does not try to bypass any check or anti-cheat.

### Multiplayer

- Vanilla servers set you back when a single move is longer than about 10 blocks, or when you pass through blocks in survival or adventure mode.
- Servers that run anti-cheat systems may detect client teleports, which can get you set back, kicked, or banned. Using the mod may also break a server's rules. Check each server's rules before you enable it there. Use it in multiplayer at your own risk.
- If you run the server yourself, [nomovementcheck](https://github.com/urntt/nomovementcheck) is a server-side mod that makes the server accept every position the client reports for the players you trust, so client teleports always work for them.

### Singleplayer

- The integrated server sets you back when a teleport passes through blocks in survival or adventure mode.
- The server counts every block a client teleport takes you down as falling, even when you land on a block. In survival, teleporting 15 blocks down costs as much health as falling 15 blocks.
- Nothing checks the destination. A teleport can put you inside blocks, into lava, or above the void. In a Hardcore world, a careless teleport can end the world.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/teleport/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

Optional:

- [Mod Menu](https://modrinth.com/mod/modmenu) opens the mod's configuration screen from its mod list.
- [OneConfig](https://modrinth.com/mod/oneconfig) can manage the mod instead (see [Settings](#settings)).

## Usage

Without OneConfig, the mod is on by default in singleplayer and disabled on multiplayer servers. To turn it off, pick the **Disabled** mode on the configuration screen. While it is off, commands that would move you only report that teleporting is turned off, and Click TP does nothing. `get`, the menu, `sethome` and `settemp` do not move you and keep working.

### Commands

`@c` stands for "client": the command runs on your client and never reaches the server. Every other `/tp` and `/teleport` command, such as `/tp @s ~ ~10 ~`, is sent to the server as usual.

| Command | Effect |
| --- | --- |
| `/tp @c <x> <y> <z>` | Teleports you to the position. Absolute (`10`), relative (`~5`) and local (`^ ^ ^3`) coordinates work like in vanilla `/tp`. |
| `/tp @c <x> <y> <z> <yaw> <pitch>` | Also sets your rotation, absolute or relative. |
| `/tp @c <x> <y> <z> facing <x> <y> <z>` | Also turns you toward a position. |
| `/tp @c <x> <y> <z> facing entity <entity> [eyes\|feet]` | Also turns you toward an entity. |
| `/tp @c <entity>` | Teleports you to an entity: a player name, a UUID, or a target selector such as `@n[type=minecraft:pig]`. You take its rotation, like in vanilla. |
| `/tp @c back [client\|server\|death]` | Takes you back to a previous position. See below. |
| `/tp @c spawn` | Takes you to your respawn point, once the mod knows it. |
| `/tp @c worldspawn` | Takes you to the world spawn. |
| `/tp @c home` / `/tp @c temp` | Takes you to your saved home or temp location. |
| `/tp @c sethome` / `/tp @c settemp` | Saves your current position as home or temp. |
| `/tp @c entities` | Lists every entity you can teleport to, nearest first (at most 100). |
| `/tp @c` | Shows a menu of destinations. |

Add `get` to the end of any command that leads somewhere, for example `/tp @c home get` or `/tp @c ^ ^ ^10 get`, to see the destination without teleporting. Clicking the coordinates puts a matching `/tp @c` command into the chat box.

Client teleports never change dimension. A destination in another dimension, such as a home saved in the Nether, is shown by `get` but cannot be reached.

The words `back`, `spawn`, `worldspawn`, `home`, `temp`, `sethome`, `settemp` and `entities` take precedence over player names. To teleport to a player with one of those names, use their UUID or a selector such as `@a[name=home,limit=1]`.

### The menu

`/tp @c` shows clickable destinations. Hover over an entry to see where it leads and how far away it is, and click it to teleport:

- **Players**: players and other player-like entities, such as mannequins, that you can teleport to. **All entities** lists every entity instead.
- **Places**: the middle of the block you stand in (facing south), your previous position, your respawn point, the world spawn, home and temp. Home and temp offer to save your position when they are not set.

### Entities the client has not loaded

Your client only knows the entities near you. Selectors only match those. For a player name or UUID it has not loaded, the mod tries, in this order:

1. If you are a spectator, it asks the server to teleport you there with vanilla's spectator teleport, which works at any distance and in any dimension.
2. If the locator bar knows the exact block of that player or entity, the mod teleports you there.
3. Otherwise you get an error with whatever the locator bar knows: the chunk, or only the direction.

Selector arguments that need data only the server has, such as `tag`, `scores`, `advancements`, `predicate`, `gamemode` and `level`, do not match anything on the client.

### Back

The mod remembers where you were before each of these, up to 32 positions in this world:

- **client**: a client teleport, including Click TP and `back` itself. Using `back` twice returns you to where you started.
- **server**: a teleport by the server, such as vanilla `/tp`, an ender pearl or a spectator teleport. When the server moves you back right after a client teleport because it rejected it, that is not recorded.
- **death**: where you died. Before you die in this session, the mod uses the death position vanilla keeps for the recovery compass.

`/tp @c back` goes to the most recent of these in your current dimension that is at least one block away. `/tp @c back <kind>` only considers that kind. The list is cleared when you leave the world.

### Respawn point, home and temp

The respawn point is known after you set it with a bed or respawn anchor ("Respawn point set") or after you respawn. For a bed or respawn anchor in a loaded chunk, `spawn` takes you to where vanilla would respawn you next to it. If the server reports that your bed is missing or obstructed, the mod forgets the bed.

Respawn points, home and temp are saved separately for each singleplayer world, server and Realm in `config/teleport/locations.json`.

### Click TP

Click TP is off by default and only works while the mod is on. When it is on, right-clicking a block within the Click TP reach (64 blocks by default) teleports you there instead of using the block or the item in your hand:

- On the top face of a block, you land on the block.
- On a side face, you land next to the block, at its height.
- On the bottom face, you land right below the block.

The destination is not checked, so you can end up inside blocks. Holding the right mouse button keeps teleporting at vanilla's use pace. While you sneak, Click TP is off and right-clicking works as usual. An entity under your crosshair, such as a villager, is used as usual too. A translucent copy of you shows where you will land.

### Key bindings

The key bindings are under **teleport** in **Options → Controls → Key Binds** and unbound by default:

- **Toggle Click TP** turns Click TP on or off and shows the new state on the action bar. In OneConfig mode, it changes the setting in OneConfig. Where teleporting is turned off or disabled, it only shows why.
- **Open teleport Settings** opens the configuration screen. With Mod Menu installed, you can also open it from the mod list, and with OneConfig, from the mod's OneConfig page.

### Settings

Open the settings screen from Mod Menu, or bind the **Open teleport Settings** key. The button at the top of the screen selects the mode:

| Mode | Effect |
| --- | --- |
| Disabled | The mod is turned off everywhere: `/tp @c` and Click TP do not move you. |
| Custom (default without OneConfig) | The settings on this screen apply. |
| OneConfig (default with OneConfig) | OneConfig manages the mod. Only offered when OneConfig is installed. |

Until you pick a mode, OneConfig mode applies while OneConfig is installed, and Custom mode otherwise. If you pick OneConfig mode and later remove OneConfig, the custom settings apply until you pick another mode.

In Custom mode, these settings apply:

| Setting | Default | Meaning |
| --- | --- | --- |
| Click TP | Off | Whether right-clicking a block teleports you, the same state the **Toggle Click TP** key switches. |
| Click TP Reach | 64 | How far away, in blocks, Click TP finds the block you look at (0 to 32767). |
| Destination Preview | On | Shows a translucent copy of you where Click TP would put you. |
| Multiplayer Mode | Disabled | **Disabled**: never teleports on servers. **Whitelist**: teleports only on servers in the server list. **Blacklist**: teleports on all servers except those in the server list. |
| Server List | Empty | The addresses the whitelist and blacklist modes use. |

In OneConfig mode, the mod's card in OneConfig turns the mod on and off; it is **off by default**. The mod's page in OneConfig holds **Click TP**, **Click TP Reach** and **Destination Preview**, with the same defaults as above. There is no multiplayer mode or server list: once the card is on, the mod teleports in singleplayer and on **every server**. To use different settings on some servers, keep them in different OneConfig profiles and switch profiles. This screen shows OneConfig's values greyed out and links to them with **Open in OneConfig**.

The custom settings are saved to `config/teleport.json` as soon as you change them; OneConfig keeps its own (`teleport-oneconfig.json`) with each profile. A configuration file from version 1.1.0, which had an on/off state, defaults and reset rules instead of modes, keeps its other settings and starts in Disabled mode if the mod was off.

The multiplayer mode is a hard limit: on a server it rules out, the mod stays off, and every command that would move you only reports that teleporting is disabled there. `get`, the menu, `sethome` and `settemp` do not move you and work everywhere. Joining another player's LAN world or a Realm counts as multiplayer.

Server list entries are compared with the address you connect to, ignoring upper and lower case. An entry without a port, such as `mc.example.com`, matches the server on any port, while an entry with a port, such as `mc.example.com:25566`, matches only that port. The server list screen marks invalid addresses in red and does not save until they are fixed or removed.

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests, which start Minecraft and check the commands against vanilla's own results, the destinations, the menus, Click TP, the key bindings, the configuration screen and modes, and the multiplayer rules and destination sources on a local dedicated server:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

By default, the development client and the tests run without OneConfig. Add `-PoneConfigRuntime=true` to run them with OneConfig installed, which offers the OneConfig mode. In that run, the tests also check that OneConfig mode is the default and starts turned off, that its settings apply on a server no list names once it is on, that the screen opens OneConfig, and that the **Toggle Click TP** key changes and saves OneConfig's setting:

```bash
./gradlew runClientGameTest -PoneConfigRuntime=true
```

The multiplayer tests start a local dedicated server, so the test setup in `build.gradle` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that test server.

## License

[MIT](LICENSE)
