package com.urntt.teleport.gametest;

import static com.urntt.teleport.gametest.GameTestSupport.EPSILON;
import static com.urntt.teleport.gametest.GameTestSupport.LOGGER;
import static com.urntt.teleport.gametest.GameTestSupport.bindKey;
import static com.urntt.teleport.gametest.GameTestSupport.check;
import static com.urntt.teleport.gametest.GameTestSupport.checkClose;
import static com.urntt.teleport.gametest.GameTestSupport.clearMessages;
import static com.urntt.teleport.gametest.GameTestSupport.clickCommand;
import static com.urntt.teleport.gametest.GameTestSupport.clickCommands;
import static com.urntt.teleport.gametest.GameTestSupport.configure;
import static com.urntt.teleport.gametest.GameTestSupport.listenForMessages;
import static com.urntt.teleport.gametest.GameTestSupport.position;
import static com.urntt.teleport.gametest.GameTestSupport.requireMessageWith;
import static com.urntt.teleport.gametest.GameTestSupport.typeCommand;
import static com.urntt.teleport.gametest.GameTestSupport.unbindKey;

import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.teleport.BackHistory;
import com.urntt.teleport.teleport.SavedLocations;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Checks {@code /tp @c} in a singleplayer world: every vanilla form against vanilla's own results, entity
 * destinations, {@code get}, the menus and their click events, {@code back}, the saved places, the respawn point,
 * riding, how the integrated server treats client teleports, and turning the mod off, including the reset on world
 * exit.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TeleportCommandGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		listenForMessages(context);
		KeyMapping toggleKey = bindKey(context, TeleportClient.TOGGLE_KEY_NAME, "key.keyboard.j");
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("time set day");
			context.waitFor(client -> client.player != null && client.player.onGround());

			checkCoordinates(context, server);
			checkVanillaParity(context, server);
			checkEntities(context, server);
			checkGetAndMenus(context, server);
			checkVanillaTeleportReachesServer(context);
			checkBack(context, server);
			checkRejectedTeleport(context, server);
			checkSavedPlaces(context, server);
			checkRespawnPoint(context, server);
			checkRiding(context, server);
			checkFallDistance(context, server);
			checkTurnedOff(context, toggleKey);

			// Leave this world turned off to check the reset on world exit below.
			configure(context, config -> config.setResetOnWorldExit(true));
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "the toggle key should turn the mod off before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(isEnabled(context), "reset on world exit should restore the singleplayer default");
			configure(context, config -> config.setResetOnWorldExit(false));
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "the toggle key should turn the mod off before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(!isEnabled(context), "without reset on world exit the state should carry over");
		}

		configure(context, config -> config.setEnabled(true));
		unbindKey(context, toggleKey);
	}

	/**
	 * The toggle key turns the whole mod off in singleplayer: commands that would move the player only report it,
	 * nothing is sent to the server, and {@code get} still works.
	 */
	private static void checkTurnedOff(final ClientGameTestContext context, final KeyMapping toggleKey) {
		check(isEnabled(context), "the mod should start on");
		// Clear the chat so that it does not cover the action bar in the screenshot.
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		clearMessages(context);
		context.getInput().pressKey(toggleKey);
		context.waitTick();
		check(!isEnabled(context), "the toggle key should turn the mod off");
		requireMessageWith(context, TeleportClient.FEATURE_NAME_KEY);
		check(!GameTestSupport.loadSavedConfig().isEnabled(), "the off state should be saved to the config file");
		context.takeScreenshot("teleport-toggled-off");

		Vec3 before = position(context);
		clearMessages(context);
		typeCommand(context, "tp @c ~3 ~ ~");
		requireMessageWith(context, "message.teleport.turned_off");
		context.waitTicks(10);
		checkClose(position(context), before, EPSILON, "a turned-off mod should not teleport the player");
		check(GameTestSupport.lastMessageWith(context, "command.unknown.command").isEmpty(),
				"the command should not reach the server while the mod is off");
		typeCommand(context, "tp @c ~ ~ ~3 get");
		requireMessageWith(context, "teleport.command.get");

		context.getInput().pressKey(toggleKey);
		context.waitTick();
		check(isEnabled(context), "the toggle key should turn the mod on again");
		check(GameTestSupport.loadSavedConfig().isEnabled(), "the on state should be saved to the config file");
	}

	private static boolean isEnabled(final ClientGameTestContext context) {
		return context.computeOnClient(client -> TeleportClient.config().isEnabled());
	}

	/**
	 * Absolute, relative and rotation arguments move the client at once, and the integrated server accepts the move.
	 */
	private static void checkCoordinates(final ClientGameTestContext context, final TestServerContext server) {
		Vec3 start = position(context);
		Vec3 target = start.add(10.0, 0.0, 0.0);
		Vec3 moved = teleport(context, String.format(Locale.ROOT, "tp @c %.3f %.3f %.3f", target.x, target.y, target.z));
		checkClose(moved, target, EPSILON, "absolute coordinates");
		context.waitTicks(5);
		checkClose(serverPosition(server), target, EPSILON, "the integrated server should accept the client teleport");

		moved = teleport(context, "teleport @c ~-4 ~ ~3");
		checkClose(moved, target.add(-4.0, 0.0, 3.0), EPSILON, "relative coordinates");

		typeCommand(context, "tp @c ~ ~ ~ 30 -20");
		checkRotation(context, 30.0F, -20.0F, "absolute rotation");
		typeCommand(context, "tp @c ~ ~ ~ ~10 ~5");
		checkRotation(context, 40.0F, -15.0F, "relative rotation");

		Vec3 before = position(context);
		clearMessages(context);
		typeCommand(context, "tp @c 30000001 0 0");
		requireMessageWith(context, "commands.teleport.invalidPosition");
		checkClose(position(context), before, EPSILON, "an invalid position should not move the player");
		context.waitTicks(5);
	}

	/**
	 * Local coordinates and {@code facing} match what vanilla computes for the same executor.
	 */
	private static void checkVanillaParity(final ClientGameTestContext context, final TestServerContext server) {
		settle(context, server);
		server.runCommand("execute as @p at @s run summon minecraft:marker ^1 ^2 ^5 {Tags:[\"probe_local\"]}");
		Vec3 expected = marker(server, "probe_local").position();
		Vec3 moved = teleport(context, "tp @c ^1 ^2 ^5");
		LOGGER.info("Local coordinates: client {} vanilla {}", moved, expected);
		checkClose(moved, expected, EPSILON, "local coordinates should match vanilla");

		settle(context, server);
		Vec3 look = position(context).add(7.0, 3.0, -2.0);
		String lookAt = String.format(Locale.ROOT, "%.3f %.3f %.3f", look.x, look.y, look.z);
		server.runCommand("execute as @p at @s run summon minecraft:marker ~ ~ ~ {Tags:[\"probe_facing\"]}");
		server.runCommand("execute as @e[tag=probe_facing] at @s run tp @s ~ ~ ~ facing " + lookAt);
		Entity facingMarker = marker(server, "probe_facing");
		typeCommand(context, "tp @c ~ ~ ~ facing " + lookAt);
		checkRotation(context, facingMarker.getYRot(), facingMarker.getXRot(), "facing a position should match vanilla");
		server.runCommand("kill @e[type=minecraft:marker]");
	}

	private static void checkEntities(final ClientGameTestContext context, final TestServerContext server) {
		Vec3 base = position(context);
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:pig %.3f %.3f %.3f {NoAI:1b,Rotation:[90f,0f]}", base.x + 6, base.y, base.z));
		context.waitFor(client -> pig(client.level) != null);
		UUID pig = context.computeOnClient(client -> pig(client.level).getUUID());
		Vec3 pigPosition = context.computeOnClient(client -> pig(client.level).position());

		checkClose(teleport(context, "tp @c @n[type=minecraft:pig]"), pigPosition, EPSILON, "a nearest-entity selector");
		typeCommand(context, "tp @c " + base.x + " " + base.y + " " + base.z);
		checkClose(teleport(context, "tp @c @e[type=minecraft:pig,limit=1,sort=nearest]"), pigPosition, EPSILON, "a limited selector");
		check(Math.abs(Mth.wrapDegrees(context.computeOnClient(client -> client.player.getYRot()) - 90.0F)) < 0.01F,
				"teleporting to an entity should take its rotation");
		typeCommand(context, "tp @c " + base.x + " " + base.y + " " + base.z);
		checkClose(teleport(context, "tp @c " + pig), pigPosition, EPSILON, "a UUID");

		typeCommand(context, "tp @c " + base.x + " " + base.y + " " + base.z);
		Vec3 before = position(context);
		clearMessages(context);
		typeCommand(context, "tp @c @e[tag=nothing,limit=1]");
		requireMessageWith(context, "argument.entity.notfound.entity");
		checkClose(position(context), before, EPSILON, "a selector without a match should not move the player");

		settle(context, server);
		server.runCommand("execute as @p at @s run summon minecraft:marker ~ ~ ~ {Tags:[\"probe_entity\"]}");
		server.runCommand("execute as @e[tag=probe_entity] at @s run tp @s ~ ~ ~ facing entity @n[type=minecraft:pig] eyes");
		Entity facingMarker = marker(server, "probe_entity");
		typeCommand(context, "tp @c ~ ~ ~ facing entity @n[type=minecraft:pig] eyes");
		checkRotation(context, facingMarker.getYRot(), facingMarker.getXRot(), "facing an entity should match vanilla");
		server.runCommand("kill @e[type=minecraft:marker]");
	}

	/**
	 * {@code get} only prints the destination; the menus offer clickable destinations, and clicking them runs the
	 * command on the client.
	 */
	private static void checkGetAndMenus(final ClientGameTestContext context, final TestServerContext server) {
		Vec3 before = position(context);
		clearMessages(context);
		typeCommand(context, "tp @c ~ ~ ~5 get");
		Component get = requireMessageWith(context, "teleport.command.get");
		LOGGER.info("get: {}", get.getString());
		checkClose(position(context), before, EPSILON, "get should not move the player");
		check(get.getString().contains(String.format(Locale.ROOT, "%.2f", before.z + 5)), "get should print the destination, got " + get.getString());

		typeCommand(context, "tp @c sethome");
		requireMessageWith(context, "teleport.command.sethome");
		Vec3 home = before;
		typeCommand(context, "tp @c ~8 ~ ~");

		clearMessages(context);
		typeCommand(context, "tp @c");
		Component menu = requireMessageWith(context, "teleport.menu.title");
		List<String> commands = clickCommands(menu);
		LOGGER.info("Menu commands: {}", commands);
		check(commands.contains("/tp @c home"), "the menu should offer home, got " + commands);
		check(commands.contains("/tp @c settemp"), "the menu should offer to set temp, got " + commands);
		check(commands.contains("/tp @c entities"), "the menu should offer the entity list, got " + commands);
		check(commands.contains("/tp @c worldspawn"), "the menu should offer the world spawn, got " + commands);
		check(commands.stream().anyMatch(command -> command.endsWith(" 0.00 0.00")), "the menu should offer the block center, got " + commands);
		context.takeScreenshot("teleport-menu");

		clickCommand(context, "/tp @c home");
		checkClose(position(context), home, EPSILON, "clicking a menu entry should teleport the player");

		clearMessages(context);
		typeCommand(context, "tp @c entities");
		Component entities = requireMessageWith(context, "teleport.menu.entities");
		UUID pig = context.computeOnClient(client -> pig(client.level).getUUID());
		check(clickCommands(entities).contains("/tp @c " + pig), "the entity list should offer the pig, got " + clickCommands(entities));
		context.takeScreenshot("teleport-entities");
		clickCommand(context, "/tp @c " + pig);
		checkClose(position(context), context.computeOnClient(client -> pig(client.level).position()), EPSILON, "clicking an entity");
		typeCommand(context, "tp @c " + home.x + " " + home.y + " " + home.z);
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(5);
	}

	/**
	 * Vanilla {@code /tp} with other targets still goes to the server. The test world does not allow cheats, so the
	 * server answers that the player may not use it, which proves that the command reached the server and that
	 * {@code /tp @c} above worked without that permission.
	 */
	private static void checkVanillaTeleportReachesServer(final ClientGameTestContext context) {
		Vec3 before = position(context);
		clearMessages(context);
		typeCommand(context, "tp @s ~ ~ ~12");
		context.waitFor(client -> GameTestSupport.receivedOnClient("command.unknown.command"), 100);
		checkClose(position(context), before, EPSILON, "vanilla /tp without permission should not move the player");
	}

	private static void checkBack(final ClientGameTestContext context, final TestServerContext server) {
		context.waitTicks(5);
		Vec3 origin = position(context);
		Vec3 away = teleport(context, "tp @c ~10 ~ ~");
		checkClose(teleport(context, "tp @c back"), origin, EPSILON, "back should return to the position before the client teleport");
		checkClose(teleport(context, "tp @c back"), away, EPSILON, "back twice should return to the teleport destination");

		context.waitTicks(5);
		Vec3 beforeServer = position(context);
		server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f", beforeServer.x - 15, beforeServer.y, beforeServer.z));
		context.waitFor(client -> client.player.position().distanceTo(beforeServer) > 10.0);
		checkClose(teleport(context, "tp @c back server"), beforeServer, EPSILON, "back server should return to the position before the server teleport");

		context.waitTicks(5);
		Vec3 death = position(context);
		server.runCommand("kill @a");
		context.waitFor(client -> client.player.isDeadOrDying());
		context.runOnClient(client -> client.player.respawn());
		context.waitFor(client -> client.player != null && client.player.isAlive() && client.player.onGround());
		context.waitTicks(5);
		checkClose(teleport(context, "tp @c back death"), death, EPSILON, "back death should return to the death position");
		context.waitTicks(5);
	}

	/**
	 * In survival, the integrated server rejects a client teleport through a wall and moves the player back. That
	 * correction is not a server teleport for {@code back}.
	 */
	private static void checkRejectedTeleport(final ClientGameTestContext context, final TestServerContext server) {
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);
		Vec3 origin = position(context);
		BlockPos feet = BlockPos.containing(origin);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
				feet.getX() + 3, feet.getY(), feet.getZ() - 2, feet.getX() + 3, feet.getY() + 3, feet.getZ() + 2));
		context.waitFor(client -> !client.level.getBlockState(feet.east(3)).isAir());

		Vec3 destination = teleport(context, "tp @c ~6 ~ ~");
		context.waitFor(client -> client.player.position().distanceTo(origin) < 0.1, 100);
		LOGGER.info("The integrated server moved the player back from {} to {}", destination, position(context));
		List<BackHistory.Entry> entries = context.computeOnClient(client -> TeleportClient.history().entries());
		check(entries.stream().noneMatch(entry -> entry.kind() == BackHistory.Kind.SERVER
						&& entry.location().position().distanceTo(destination) < BackHistory.MIN_DISTANCE),
				"a rejected client teleport should not be recorded as a server teleport, got " + entries);

		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
				feet.getX() + 3, feet.getY(), feet.getZ() - 2, feet.getX() + 3, feet.getY() + 3, feet.getZ() + 2));
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	private static void checkSavedPlaces(final ClientGameTestContext context, final TestServerContext server) {
		Vec3 temp = position(context);
		typeCommand(context, "tp @c settemp");
		typeCommand(context, "tp @c ~6 ~ ~");
		checkClose(teleport(context, "tp @c temp"), temp, EPSILON, "temp");

		BlockPos spawn = BlockPos.containing(temp).offset(-9, 0, 4);
		server.runCommand(String.format(Locale.ROOT, "setworldspawn %d %d %d", spawn.getX(), spawn.getY(), spawn.getZ()));
		context.waitFor(client -> client.level.getRespawnData().pos().equals(spawn));
		checkClose(teleport(context, "tp @c worldspawn"), Vec3.atBottomCenterOf(spawn), EPSILON, "worldspawn");
		context.waitTicks(5);
	}

	/**
	 * Using a bed records it as the respawn point; when the bed is gone, the respawn point is where the player
	 * respawns.
	 */
	private static void checkRespawnPoint(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos feet = BlockPos.containing(position(context));
		BlockPos foot = feet.east(2);
		BlockPos head = foot.east();
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:red_bed[facing=east,part=foot]", foot.getX(), foot.getY(), foot.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:red_bed[facing=east,part=head]", head.getX(), head.getY(), head.getZ()));
		context.waitFor(client -> client.level.getBlockState(head).getBlock() instanceof BedBlock);

		clearMessages(context);
		context.getInput().lookAt(foot);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitFor(client -> GameTestSupport.receivedOnClient("block.minecraft.set_spawn"), 100);
		SavedLocations.SpawnPoint spawn = context.computeOnClient(client -> TeleportClient.locations().spawn().orElseThrow());
		check(head.equals(spawn.block()), "the bed head should be the respawn block, got " + spawn);

		typeCommand(context, "tp @c ~ ~ ~-6");
		Vec3 standUp = teleport(context, "tp @c spawn");
		LOGGER.info("Respawn point next to the bed: {}", standUp);
		check(standUp.distanceTo(Vec3.atBottomCenterOf(head)) < 3.0 && BlockPos.containing(standUp).distManhattan(head) > 0,
				"spawn should lead next to the bed, got " + standUp);

		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", foot.getX(), foot.getY(), foot.getZ(), head.getX(), head.getY(), head.getZ()));
		context.waitTicks(5);
		clearMessages(context);
		server.runCommand("kill @a");
		context.waitFor(client -> client.player.isDeadOrDying());
		context.runOnClient(client -> client.player.respawn());
		context.waitFor(client -> client.player != null && client.player.isAlive() && client.player.onGround());
		context.waitTicks(5);
		requireMessageWith(context, "block.minecraft.spawn.not_valid");
		Vec3 respawned = position(context);
		SavedLocations.SpawnPoint afterRespawn = context.computeOnClient(client -> TeleportClient.locations().spawn().orElseThrow());
		check(afterRespawn.block() == null, "a missing bed should no longer be the respawn point, got " + afterRespawn);
		checkClose(afterRespawn.location().position(), respawned, 0.5, "the respawn point should be where the player respawned");
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	/**
	 * The player controls a boat it rides, so the boat moves along.
	 */
	private static void checkRiding(final ClientGameTestContext context, final TestServerContext server) {
		Vec3 base = position(context);
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:oak_boat %.3f %.3f %.3f", base.x, base.y, base.z));
		server.runCommand("ride @p mount @n[type=minecraft:oak_boat]");
		context.waitFor(client -> client.player.getVehicle() != null);
		Vec3 boatBefore = context.computeOnClient(client -> client.player.getVehicle().position());
		Vec3 playerAfter = teleport(context, "tp @c ~5 ~ ~");
		Vec3 boatAfter = context.computeOnClient(client -> client.player.getVehicle().position());
		checkClose(boatAfter, boatBefore.add(5.0, 0.0, 0.0), EPSILON, "the boat the player controls should move along");
		check(Math.abs(playerAfter.x - boatAfter.x) < 0.5 && Math.abs(playerAfter.z - boatAfter.z) < 0.5,
				"the player should stay in the boat, got player " + playerAfter + " boat " + boatAfter);
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[type=minecraft:oak_boat]");
		context.waitFor(client -> client.player.getVehicle() == null);
		context.waitTicks(5);
	}

	/**
	 * Measures what the README warns about: a client teleport downward into the air counts as falling on the server.
	 */
	private static void checkFallDistance(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos feet = BlockPos.containing(position(context));
		int height = 20;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
				feet.getX(), feet.getY(), feet.getZ(), feet.getX(), feet.getY() + height - 1, feet.getZ()));
		server.runCommand("gamemode survival @a");
		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", feet.getX(), feet.getY() + height, feet.getZ()));
		context.waitFor(client -> client.player.onGround() && client.player.getY() >= feet.getY() + height - EPSILON);
		context.waitTicks(10);

		float healthBefore = context.computeOnClient(client -> client.player.getHealth());
		int dropByTeleport = 15;
		typeCommand(context, String.format(Locale.ROOT, "tp @c ~2 ~-%d ~", dropByTeleport));
		context.waitFor(client -> client.player.onGround(), 100);
		context.waitTicks(20);
		float healthAfter = context.computeOnClient(client -> client.player.getHealth());
		int fallAfterTeleport = height - dropByTeleport;
		LOGGER.info("Health before a {}-block teleport down into the air and a {}-block fall: {}, after: {}",
				dropByTeleport, fallAfterTeleport, healthBefore, healthAfter);
		// A plain fall of that height costs fallAfterTeleport - 3 health.
		check(healthBefore - healthAfter > fallAfterTeleport - 3,
				"the server should count the teleport down as falling, lost only " + (healthBefore - healthAfter));
		server.runCommand("gamemode creative @a");
		server.runCommand("effect give @a minecraft:instant_health 1 10");
	}

	/**
	 * Waits until the player stands on the ground and the server knows the same position, so that server commands
	 * executed at the player use the same position as the client.
	 */
	private static void settle(final ClientGameTestContext context, final TestServerContext server) {
		// The ground flag only updates when the player moves, so give a teleported player time to start falling.
		context.waitTicks(2);
		context.waitFor(client -> client.player.onGround());
		context.waitTicks(5);
		checkClose(serverPosition(server), position(context), EPSILON, "the server should know the player's position");
	}

	private static Vec3 teleport(final ClientGameTestContext context, final String command) {
		GameTestSupport.LOGGER.info("Typed command: /{}", command);
		return context.computeOnClient(client -> {
			client.player.connection.sendCommand(command);
			return client.player.position();
		});
	}

	private static void checkRotation(final ClientGameTestContext context, final float yRot, final float xRot, final String what) {
		Vec2 rotation = context.computeOnClient(client -> new Vec2(client.player.getXRot(), client.player.getYRot()));
		check(Math.abs(Mth.wrapDegrees(rotation.y - yRot)) < 0.01F && Math.abs(rotation.x - xRot) < 0.01F,
				what + ": expected yaw " + yRot + " pitch " + xRot + ", got yaw " + rotation.y + " pitch " + rotation.x);
	}

	private static Vec3 serverPosition(final TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
	}

	private static Entity marker(final TestServerContext server, final String tag) {
		return server.computeOnServer(s -> {
			for (Entity entity : s.overworld().getAllEntities()) {
				if (entity.getType() == EntityTypes.MARKER && entity.entityTags().contains(tag)) {
					return entity;
				}
			}
			throw new AssertionError("marker " + tag + " was not summoned");
		});
	}

	private static Entity pig(final ClientLevel level) {
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.getType() == EntityTypes.PIG) {
				return entity;
			}
		}
		return null;
	}
}
