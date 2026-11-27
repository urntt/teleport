package com.urntt.teleport.gametest;

import static com.urntt.teleport.gametest.GameTestSupport.check;

import com.urntt.teleport.Scene;
import com.urntt.teleport.ServerAddresses;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.TeleportController;
import com.urntt.teleport.TeleportController.Availability;
import com.urntt.teleport.TeleportController.ToggleResult;
import com.urntt.teleport.command.TeleportCommands;
import com.urntt.teleport.compat.OneConfigBridge;
import com.urntt.teleport.config.ConfigMode;
import com.urntt.teleport.config.MultiplayerMode;
import com.urntt.teleport.config.MultiplayerRules;
import com.urntt.teleport.config.SettingValues;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.config.TeleportSettings;
import com.urntt.teleport.teleport.SavedLocation;
import com.urntt.teleport.teleport.SavedLocations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Checks the rules that need no world: configuration defaults, modes and file handling, the upgrade of older files,
 * which settings the controller applies, saved locations, server address matching, which commands the mod keeps from
 * the server, and the icon. It uses temporary files, so it does not touch the configuration the other tests use.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TeleportLogicGameTest implements FabricClientGameTest {
	private static final Scene SERVER_A = Scene.multiplayer("a.example");
	private static final Scene UNKNOWN_SERVER = Scene.multiplayer("unknown.example");

	@Override
	public void runTest(final ClientGameTestContext context) {
		try {
			Path directory = Files.createTempDirectory("teleport-gametest");
			checkDefaults();
			checkConfigFile(directory);
			checkUpgrades(directory);
			checkOneConfigFallback(directory);
			checkController(directory);
			checkSavedLocations(directory);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		checkNoMultiplayerRules();
		checkServerAddresses();
		checkHandledCommands();
		checkIcon();
	}

	/**
	 * Loom deletes the run directory before each run, so the configuration the mod loaded holds the defaults: no mode
	 * is chosen, so OneConfig manages the mod while it is installed.
	 */
	private static void checkDefaults() {
		TeleportConfig config = GameTestSupport.loadSavedConfig();
		check(config.storedMode() == null, "no mode should be chosen by default, was " + config.storedMode());
		check(config.effectiveMode() == defaultMode(), "the default mode should be " + defaultMode() + ", was " + config.effectiveMode());
		check(SettingValues.copyOf(config).equals(SettingValues.DEFAULTS), "the custom settings should start at the defaults, were " + SettingValues.copyOf(config));
		check(!SettingValues.DEFAULTS.clickTp(), "Click TP should be off by default");
		check(SettingValues.DEFAULTS.clickTpReach() == 64, "Click TP reach should default to 64");
		check(SettingValues.DEFAULTS.clickTpPreview(), "the destination preview should be on by default");
		check(Objects.equals(SettingValues.DEFAULTS.multiplayerRules(), new MultiplayerRules(MultiplayerMode.DISABLED, List.of())),
				"multiplayer should be disabled by default with an empty server list");
		check(!SettingValues.ONECONFIG_DEFAULTS.enabled() && SettingValues.ONECONFIG_DEFAULTS.multiplayerRules() == null,
				"OneConfig's settings should be off by default and have no multiplayer rules");
		boolean oneConfigInstalled = FabricLoader.getInstance().isModLoaded(OneConfigBridge.MOD_ID);
		check(OneConfigBridge.isAvailable() == oneConfigInstalled,
				"OneConfig mode should be available exactly when OneConfig is installed (installed: " + oneConfigInstalled + ")");
	}

	private static ConfigMode defaultMode() {
		return OneConfigBridge.isAvailable() ? ConfigMode.ONECONFIG : ConfigMode.CUSTOM;
	}

	private static void checkConfigFile(final Path directory) throws IOException {
		Path path = directory.resolve("config.json");
		Files.writeString(path, "{\"mode\": \"unknown\", \"clickTpReach\": 99999, \"multiplayerMode\": \"unknown\", \"servers\": [\" a.example \", \"\"]}");
		TeleportConfig config = TeleportConfig.load(path);
		check(config.storedMode() == null, "an unknown mode should count as no choice");
		check(config.clickTpReach() == TeleportConfig.MAX_CLICK_TP_REACH, "too large reach should be clamped, got " + config.clickTpReach());
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "an unknown multiplayer mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example")), "server list should be trimmed, got " + config.servers());

		config.setClickTpReach(-5);
		check(TeleportConfig.load(path).clickTpReach() == TeleportConfig.MIN_CLICK_TP_REACH, "negative reach should be clamped and saved");
		config.setClickTpReach(300);
		config.setClickTp(true);
		config.setClickTpPreview(false);
		config.setMode(ConfigMode.DISABLED);
		TeleportConfig reloaded = TeleportConfig.load(path);
		check(reloaded.clickTpReach() == 300 && reloaded.clickTp() && !reloaded.clickTpPreview() && reloaded.storedMode() == ConfigMode.DISABLED,
				"the settings and the mode should be saved");
		check(SettingValues.copyOf(reloaded.effective()).equals(SettingValues.DISABLED), "Disabled mode should apply the disabled settings");

		Path broken = directory.resolve("broken.json");
		Files.writeString(broken, "{ not json");
		TeleportConfig fallback = TeleportConfig.load(broken);
		check(fallback.clickTpReach() == TeleportConfig.DEFAULT_CLICK_TP_REACH, "an unreadable file should fall back to the defaults");
		check(Files.readString(broken).equals("{ not json"), "an unreadable file should be left untouched");
	}

	/**
	 * 1.1.0 turned the mod on and off with {@code enabled} and had defaults and reset rules; 1.0.0 had the defaults and
	 * reset rules for Click TP. Both keep their other settings, and only a 1.1.0 file whose mod was off starts in
	 * Disabled mode.
	 */
	private static void checkUpgrades(final Path directory) throws IOException {
		Path off = directory.resolve("1.1.0-off.json");
		Files.writeString(off, "{\"enabled\": false, \"clickTp\": true, \"clickTpReach\": 20, \"singleplayerDefault\": true,"
				+ " \"multiplayerDefault\": true, \"resetOnWorldExit\": true, \"resetOnGameExit\": true, \"multiplayerMode\": \"whitelist\"}");
		TeleportConfig fromOff = TeleportConfig.load(off);
		check(fromOff.storedMode() == ConfigMode.DISABLED, "a 1.1.0 file with the mod off should start in Disabled mode, was " + fromOff.storedMode());
		check(fromOff.clickTp() && fromOff.clickTpReach() == 20 && fromOff.multiplayerMode() == MultiplayerMode.WHITELIST,
				"a 1.1.0 file should keep its other settings");
		String saved = Files.readString(off);
		for (String key : List.of("enabled", "singleplayerDefault", "multiplayerDefault", "resetOnWorldExit", "resetOnGameExit")) {
			check(!saved.contains("\"" + key + "\""), "the upgraded file should no longer contain " + key + ": " + saved);
		}
		check(TeleportConfig.load(off).storedMode() == ConfigMode.DISABLED, "the upgraded mode should be saved");

		Path on = directory.resolve("1.1.0-on.json");
		Files.writeString(on, "{\"enabled\": true, \"clickTp\": true}");
		TeleportConfig fromOn = TeleportConfig.load(on);
		check(fromOn.storedMode() == null && fromOn.clickTp(), "a 1.1.0 file with the mod on should have no chosen mode");

		Path first = directory.resolve("1.0.0.json");
		Files.writeString(first, "{\"clickTp\": true, \"singleplayerDefault\": false, \"multiplayerDefault\": false, \"resetOnWorldExit\": true}");
		TeleportConfig fromFirst = TeleportConfig.load(first);
		check(fromFirst.storedMode() == null && fromFirst.clickTp(), "a 1.0.0 file should have no chosen mode and keep Click TP");

		// A file with a mode is current; an "enabled" key in it means nothing.
		Path current = directory.resolve("current.json");
		Files.writeString(current, "{\"mode\": \"custom\", \"enabled\": false}");
		check(TeleportConfig.load(current).storedMode() == ConfigMode.CUSTOM, "a current file should keep its mode");
	}

	/**
	 * A file that names OneConfig mode keeps it, but the custom settings apply while OneConfig is not installed.
	 */
	private static void checkOneConfigFallback(final Path directory) throws IOException {
		Path path = directory.resolve("oneconfig.json");
		Files.writeString(path, "{\"mode\": \"oneconfig\", \"clickTpReach\": 10}");
		TeleportConfig config = TeleportConfig.load(path);
		check(config.storedMode() == ConfigMode.ONECONFIG, "the stored mode should stay OneConfig");
		if (OneConfigBridge.isAvailable()) {
			check(config.effectiveMode() == ConfigMode.ONECONFIG, "OneConfig mode should apply while OneConfig is installed");
			check(config.effective().clickTpReach() == SettingValues.ONECONFIG_DEFAULTS.clickTpReach(), "OneConfig's settings should apply in OneConfig mode");
		} else {
			check(config.effectiveMode() == ConfigMode.CUSTOM, "Custom mode should apply while OneConfig is missing");
			check(config.effective().clickTpReach() == 10, "the custom settings should apply while OneConfig is missing");
		}
		check(TeleportConfig.load(path).storedMode() == ConfigMode.ONECONFIG, "loading should not change the stored mode");
	}

	/**
	 * The controller applies the settings of the configuration mode: Disabled mode turns the mod off before the
	 * multiplayer rules apply, and Custom mode applies the multiplayer rules. The Click TP key changes the settings that
	 * apply.
	 */
	private static void checkController(final Path directory) {
		TeleportConfig config = TeleportConfig.load(directory.resolve("controller.json"));
		config.setMode(ConfigMode.CUSTOM);
		TeleportController controller = new TeleportController(config::effective, config::setEffectiveClickTp);
		check(controller.availability() == Availability.BLOCKED, "nothing should be available outside a world");
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.availability() == Availability.AVAILABLE, "Custom mode should allow singleplayer");
		check(controller.toggleClickTp() == ToggleResult.ENABLED && config.clickTp() && controller.isClickTpActive(),
				"the Click TP key should turn the custom Click TP on");

		controller.onJoin(SERVER_A);
		check(controller.availability() == Availability.BLOCKED, "the default multiplayer mode should rule out every server");
		check(!controller.isClickTpActive(), "Click TP should be inactive on a ruled-out server");
		check(controller.toggleClickTp() == ToggleResult.BLOCKED && config.clickTp(), "the Click TP key should only report a ruled-out server");
		config.setServers(List.of("a.example"));
		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		check(controller.availability() == Availability.AVAILABLE, "a whitelisted server should be allowed");
		controller.onJoin(UNKNOWN_SERVER);
		check(controller.availability() == Availability.BLOCKED, "a server missing from the whitelist should be ruled out");
		config.setMultiplayerMode(MultiplayerMode.BLACKLIST);
		check(controller.availability() == Availability.AVAILABLE, "a server missing from the blacklist should be allowed");

		config.setMode(ConfigMode.DISABLED);
		check(controller.availability() == Availability.TURNED_OFF, "Disabled mode should turn the mod off");
		check(!controller.isClickTpActive(), "Click TP should be inactive in Disabled mode");
		check(controller.toggleClickTp() == ToggleResult.TURNED_OFF && config.clickTp(), "the Click TP key should only report Disabled mode");
		config.setMultiplayerMode(MultiplayerMode.DISABLED);
		check(controller.availability() == Availability.TURNED_OFF, "Disabled mode should come before the multiplayer rules");
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.availability() == Availability.TURNED_OFF, "Disabled mode should turn the mod off in singleplayer too");
	}

	/**
	 * Settings without multiplayer rules, like OneConfig's, are off until enabled, and then allow singleplayer and
	 * every server.
	 */
	private static void checkNoMultiplayerRules() {
		AtomicReference<TeleportSettings> settings = new AtomicReference<>(SettingValues.ONECONFIG_DEFAULTS);
		AtomicBoolean clickTp = new AtomicBoolean();
		TeleportController controller = new TeleportController(settings::get, clickTp::set);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.availability() == Availability.TURNED_OFF, "OneConfig's settings should be off by default");
		controller.onJoin(SERVER_A);
		check(controller.availability() == Availability.TURNED_OFF, "OneConfig's settings should be off by default on a server");

		settings.set(new SettingValues(true, false, SettingValues.DEFAULTS.clickTpReach(), true, null));
		check(controller.availability() == Availability.AVAILABLE, "settings without multiplayer rules should allow every server");
		check(controller.toggleClickTp() == ToggleResult.ENABLED && clickTp.get(), "the Click TP key should change the settings that apply");
		controller.onJoin(UNKNOWN_SERVER);
		check(controller.availability() == Availability.AVAILABLE, "settings without multiplayer rules should allow an unknown server");
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.availability() == Availability.AVAILABLE, "settings without multiplayer rules should allow singleplayer");
		controller.onDisconnect();
		check(controller.availability() == Availability.BLOCKED, "nothing should be available outside a world");
	}

	/**
	 * The icon that {@code fabric.mod.json} names, which Mod Menu and OneConfig show, is part of the mod.
	 */
	private static void checkIcon() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(TeleportClient.MOD_ID).orElseThrow();
		String icon = mod.getMetadata().getIconPath(512).orElseThrow(() -> new AssertionError("fabric.mod.json should name an icon"));
		check(mod.findPath(icon).filter(Files::isRegularFile).isPresent(), "the icon " + icon + " should be part of the mod");
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
