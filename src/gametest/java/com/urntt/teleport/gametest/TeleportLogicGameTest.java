package com.urntt.teleport.gametest;

import static com.urntt.teleport.gametest.GameTestSupport.check;

import com.urntt.teleport.ServerAddresses;
import com.urntt.teleport.command.TeleportCommands;
import com.urntt.teleport.config.MultiplayerMode;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.teleport.SavedLocation;
import com.urntt.teleport.teleport.SavedLocations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Checks the rules that need no world: configuration defaults and file handling, saved locations, server address
 * matching, and which commands the mod keeps from the server. It uses temporary files, so it does not touch the
 * configuration the other tests use.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TeleportLogicGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		try {
			Path directory = Files.createTempDirectory("teleport-gametest");
			checkDefaults();
			checkConfigFile(directory);
			checkSavedLocations(directory);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		checkServerAddresses();
		checkHandledCommands();
	}

	/**
	 * The installation is fresh, so the configuration the mod loaded holds the defaults.
	 */
	private static void checkDefaults() {
		TeleportConfig config = GameTestSupport.loadSavedConfig();
		check(!config.isClickTpEnabled(), "Click TP should be off by default");
		check(config.clickTpReach() == TeleportConfig.DEFAULT_CLICK_TP_REACH && TeleportConfig.DEFAULT_CLICK_TP_REACH == 64,
				"Click TP reach should default to 64, got " + config.clickTpReach());
		check(config.clickTpPreview(), "the destination preview should be on by default");
		check(config.isEnabled(), "the mod should be on by default");
		check(config.singleplayerDefault() && config.multiplayerDefault(), "the mod should be on by default after a reset");
		check(!config.resetOnWorldExit() && !config.resetOnGameExit(), "reset rules should be off by default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "multiplayer should be disabled by default");
		check(config.servers().isEmpty(), "the server list should be empty by default");
	}

	private static void checkConfigFile(final Path directory) throws IOException {
		Path path = directory.resolve("config.json");
		Files.writeString(path, "{\"clickTpReach\": 99999, \"multiplayerMode\": \"unknown\", \"servers\": [\" a.example \", \"\"]}");
		TeleportConfig config = TeleportConfig.load(path);
		check(config.clickTpReach() == TeleportConfig.MAX_CLICK_TP_REACH, "too large reach should be clamped, got " + config.clickTpReach());
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "unknown mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example")), "server list should be trimmed, got " + config.servers());

		config.setClickTpReach(-5);
		check(TeleportConfig.load(path).clickTpReach() == TeleportConfig.MIN_CLICK_TP_REACH, "negative reach should be clamped and saved");
		config.setClickTpReach(300);
		check(TeleportConfig.load(path).clickTpReach() == 300, "reach should be saved");

		// 1.0.0 had no state for the whole mod; its defaults belonged to Click TP and were written as off.
		Path oldVersion = directory.resolve("old.json");
		Files.writeString(oldVersion, "{\"clickTp\": true, \"singleplayerDefault\": false, \"multiplayerDefault\": false, \"resetOnWorldExit\": true}");
		TeleportConfig migrated = TeleportConfig.load(oldVersion);
		check(migrated.isEnabled() && migrated.singleplayerDefault() && migrated.multiplayerDefault(),
				"a 1.0.0 config should start with the mod on and its defaults on");
		check(migrated.isClickTpEnabled() && migrated.resetOnWorldExit(), "a 1.0.0 config should keep its other settings");
		Path current = directory.resolve("current.json");
		Files.writeString(current, "{\"enabled\": false, \"singleplayerDefault\": false, \"multiplayerDefault\": false}");
		TeleportConfig kept = TeleportConfig.load(current);
		check(!kept.isEnabled() && !kept.singleplayerDefault() && !kept.multiplayerDefault(),
				"a current config should keep the mod's state and defaults");

		Path broken = directory.resolve("broken.json");
		Files.writeString(broken, "{ not json");
		TeleportConfig fallback = TeleportConfig.load(broken);
		check(fallback.clickTpReach() == TeleportConfig.DEFAULT_CLICK_TP_REACH, "an unreadable file should fall back to the defaults");
		check(Files.readString(broken).equals("{ not json"), "an unreadable file should be left untouched");
	}

	private static void checkSavedLocations(final Path directory) {
		Path path = directory.resolve("locations.json");
		SavedLocations locations = SavedLocations.load(path);
		SavedLocation home = new SavedLocation(Level.NETHER, new Vec3(1.5, 64, -3.25), 90.0F, 10.0F);

		locations.select(null);
		check(locations.home().isEmpty(), "no world selected should have no home");
		locations.select("singleplayer/World A");
		locations.setHome(home);
		locations.setSpawn(SavedLocations.SpawnPoint.atBlock(Level.OVERWORLD, new BlockPos(5, 70, 5), 45.0F));
		locations.select("server/mc.example.com");
		check(locations.home().isEmpty(), "locations should be separate per world");

		SavedLocations reloaded = SavedLocations.load(path);
		reloaded.select("singleplayer/World A");
		check(reloaded.home().equals(Optional.of(home)), "home should be saved, got " + reloaded.home());
		check(reloaded.spawn().map(SavedLocations.SpawnPoint::block).equals(Optional.of(new BlockPos(5, 70, 5))),
				"a bed respawn point should keep its block, got " + reloaded.spawn());
		reloaded.clearSpawnBlock();
		check(reloaded.spawn().isEmpty(), "a broken bed should clear the respawn point");

		SavedLocation respawned = new SavedLocation(Level.OVERWORLD, new Vec3(0.5, 64, 0.5), 0.0F, 0.0F);
		reloaded.setSpawn(new SavedLocations.SpawnPoint(respawned, null));
		reloaded.clearSpawnBlock();
		check(reloaded.spawn().map(SavedLocations.SpawnPoint::location).equals(Optional.of(respawned)),
				"a respawn point without a block should survive a broken bed, got " + reloaded.spawn());
	}

	private static void checkServerAddresses() {
		check(ServerAddresses.matches("localhost", "localhost:25565"), "an entry without a port should match any port");
		check(!ServerAddresses.matches("localhost:25566", "localhost"), "an entry with a port should match only that port");
		check(ServerAddresses.matches("MC.Example.com", "mc.example.com"), "host names should match ignoring case");
		check(!ServerAddresses.isValid("not a host"), "invalid addresses should be rejected");
	}

	private static void checkHandledCommands() {
		check(TeleportCommands.handles("tp @c 0 64 0"), "tp @c should run on the client");
		check(TeleportCommands.handles("teleport @c"), "teleport @c should run on the client");
		check(!TeleportCommands.handles("tp @a 0 64 0"), "tp with other targets should go to the server");
		check(!TeleportCommands.handles("tp @cat 0 64 0"), "a different selector should go to the server");
		check(!TeleportCommands.handles("msg @c hello"), "other commands should go to the server");
	}
}
