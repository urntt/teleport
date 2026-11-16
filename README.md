# teleport

A simple client-side Fabric mod for Minecraft: Java Edition that lets the player teleport without asking the server.

`/tp @c …` and `/teleport @c …` take the same arguments as vanilla `/tp`, but they only change your own position on your client, whatever your permissions are. Click TP teleports you to the block you right-click.

## Warning

**This mod is disabled in multiplayer by default. To use it in multiplayer, change the settings on its configuration screen.**

A client teleport is not a server teleport. The server, including the integrated server of a singleplayer world, still checks every move and may put you back. Servers that run anti-cheat systems may detect client teleports, which can get you set back, kicked, or banned, and using the mod may break a server's rules. This mod does not try to bypass any anti-cheat. Use it at your own risk.

## License

[MIT](LICENSE)
