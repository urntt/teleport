package com.urntt.teleport.gametest;

import static com.urntt.teleport.gametest.GameTestSupport.EPSILON;
import static com.urntt.teleport.gametest.GameTestSupport.LOGGER;
import static com.urntt.teleport.gametest.GameTestSupport.bindKey;
import static com.urntt.teleport.gametest.GameTestSupport.check;
import static com.urntt.teleport.gametest.GameTestSupport.checkClose;
import static com.urntt.teleport.gametest.GameTestSupport.clearMessages;
import static com.urntt.teleport.gametest.GameTestSupport.configure;
import static com.urntt.teleport.gametest.GameTestSupport.listenForMessages;
import static com.urntt.teleport.gametest.GameTestSupport.position;
import static com.urntt.teleport.gametest.GameTestSupport.requireMessageWith;
import static com.urntt.teleport.gametest.GameTestSupport.typeCommand;
import static com.urntt.teleport.gametest.GameTestSupport.unbindKey;

import com.urntt.teleport.Scene;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.config.MultiplayerMode;
import com.urntt.teleport.teleport.Waypoints;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Checks the mod on a local dedicated server, which the client reaches as {@code localhost} without operator
 * permissions: the multiplayer modes, vanilla's movement checks, and the destination sources for entities the client
 * has not loaded (the spectator teleport and the locator bar).
 */
@SuppressWarnings("UnstableApiUsage")
public final class TeleportMultiplayerGameTest implements FabricClientGameTest {
	/**
	 * Pigs are tracked by clients within 10 chunks; at 10% the client loads them only within 16 blocks, so pigs
	 * further away but in sight are known only through the locator bar.
	 */
	private static final String ENTITY_BROADCAST_RANGE_PERCENTAGE = "10";

	@Override
	public void runTest(final ClientGameTestContext context) {
		listenForMessages(context);
		configure(context, config -> {
			config.setClickTpEnabled(false);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		KeyMapping toggleKey = bindKey(context, TeleportClient.TOGGLE_CLICK_TP_KEY_NAME, "key.keyboard.j");

		Properties properties = new Properties();
		properties.setProperty("entity-broadcast-range-percentage", ENTITY_BROADCAST_RANGE_PERCENTAGE);
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode creative @a");
			server.runCommand("time set day");
			context.waitFor(client -> client.player.onGround());
			context.waitTicks(10);
			Scene scene = context.computeOnClient(client -> TeleportClient.controller().scene());
			check(scene instanceof Scene.Multiplayer, "a dedicated server should count as multiplayer, got " + scene);

			checkDisabledByDefault(context, toggleKey);
			checkWhitelisted(context, server, connection);
			checkSpectatorTeleport(context, server);
			checkLocatorBar(context, server);
		}

		configure(context, config -> {
			config.setClickTpEnabled(false);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		unbindKey(context, toggleKey);
	}

	/**
	 * With the default multiplayer mode, nothing that moves the player works and nothing reaches the server, while
	 * {@code get}, the menu and {@code sethome} still do.
	 */
	private static void checkDisabledByDefault(final ClientGameTestContext context, final KeyMapping toggleKey) {
		Vec3 before = position(context);
		clearMessages(context);
		typeCommand(context, "tp @c ~3 ~ ~");
		requireMessageWith(context, "message.teleport.blocked");
		context.waitTicks(20);
		checkClose(position(context), before, EPSILON, "a disabled server should not teleport the player");
		check(GameTestSupport.lastMessageWith(context, "command.unknown.command").isEmpty()
						&& GameTestSupport.lastMessageWith(context, "argument.entity.selector.unknown").isEmpty(),
				"the command should not reach the server, got " + GameTestSupport.messages(context).stream().map(Component::getString).toList());

		context.getInput().pressKey(toggleKey);
		context.waitTick();
		check(!context.computeOnClient(client -> TeleportClient.config().isClickTpEnabled()), "the toggle key should not turn Click TP on here");
		context.takeScreenshot("teleport-blocked-on-server");

		typeCommand(context, "tp @c ~ ~ ~3 get");
		requireMessageWith(context, "teleport.command.get");
		typeCommand(context, "tp @c");
		requireMessageWith(context, "teleport.menu.title");
		typeCommand(context, "tp @c sethome");
		requireMessageWith(context, "teleport.command.sethome");
	}

	/**
	 * On a whitelisted server, a short client teleport passes vanilla's checks, and a long one is set back by the
	 * vanilla speed check, which this mod does not try to avoid.
	 */
	private static void checkWhitelisted(final ClientGameTestContext context, final TestDedicatedServerContext server,
			final TestDedicatedServerConnection connection) {
		configure(context, config -> {
			config.setServers(List.of("localhost"));
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		});

		Vec3 start = position(context);
		Vec3 target = start.add(3.0, 0.0, 0.0);
		typeCommand(context, String.format(Locale.ROOT, "tp @c %.3f %.3f %.3f", target.x, target.y, target.z));
		context.waitTicks(10);
		Vec3 onServer = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
		checkClose(onServer, target, EPSILON, "a short client teleport should be accepted");

		Vec3 beforeLong = position(context);
		typeCommand(context, "tp @c ~50 ~ ~");
		context.waitFor(client -> client.player.position().distanceTo(beforeLong) < 0.5, 100);
		LOGGER.info("The dedicated server set back a 50-block client teleport to {}", position(context));
	}

	/**
	 * A spectator reaches an entity the client has not loaded through vanilla's spectator teleport.
	 */
	private static void checkSpectatorTeleport(final ClientGameTestContext context, final TestDedicatedServerContext server) {
		BlockPos far = BlockPos.containing(position(context)).east(2000);
		UUID pig = summonPig(server, far, "spectator_target");
		server.runCommand("gamemode spectator @a");
		context.waitFor(client -> client.player.isSpectator());
		check(context.computeOnClient(client -> client.level.getEntity(pig) == null), "the far pig should not be loaded");

		clearMessages(context);
		typeCommand(context, "tp @c " + pig);
		requireMessageWith(context, "teleport.command.spectator");
		context.waitFor(client -> client.player.position().distanceTo(Vec3.atBottomCenterOf(far)) < 2.0, 200);
		LOGGER.info("The spectator teleport moved the player to {}", position(context));

		server.runCommand("gamemode creative @a");
		Vec3 back = Vec3.atBottomCenterOf(far.west(2000));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f", back.x, back.y, back.z));
		context.waitFor(client -> !client.player.isSpectator() && client.player.position().distanceTo(back) < 1.0);
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(20);
	}

	/**
	 * The locator bar gives the exact block of a pig in sight that the client has not loaded, only the chunk of one
	 * out of sight, and only the direction of one really far away.
	 */
	private static void checkLocatorBar(final ClientGameTestContext context, final TestDedicatedServerContext server) {
		BlockPos origin = BlockPos.containing(position(context));
		BlockPos nearPos = origin.east(24);
		BlockPos midPos = origin.east(220);
		BlockPos farPos = origin.east(700);
		UUID near = summonPig(server, nearPos, "near");
		UUID mid = summonPig(server, midPos, "mid");
		UUID far = summonPig(server, farPos, "far");
		for (String tag : List.of("near", "mid", "far")) {
			server.runCommand("attribute @e[tag=" + tag + ",limit=1] minecraft:waypoint_transmit_range base set 1000");
		}
		context.waitFor(client -> Waypoints.find(client.player, near).isPresent() && Waypoints.find(client.player, mid).isPresent()
				&& Waypoints.find(client.player, far).isPresent(), 200);
		check(context.computeOnClient(client -> client.level.getEntity(near) == null), "the near pig should not be loaded");

		clearMessages(context);
		typeCommand(context, "tp @c " + near + " get");
		Component get = requireMessageWith(context, "teleport.command.get");
		LOGGER.info("Locator bar destination of the near pig: {}", get.getString());
		Vec3 nearDestination = Vec3.atBottomCenterOf(nearPos);
		check(get.getString().contains(String.format(Locale.ROOT, "%.2f", nearDestination.x)), "get should show the pig's block, got " + get.getString());
		Vec3 moved = context.computeOnClient(client -> {
			client.player.connection.sendCommand("tp @c " + near);
			return client.player.position();
		});
		checkClose(moved, nearDestination, EPSILON, "a client teleport to the locator bar position");

		context.waitTicks(20);
		typeCommand(context, "tp @c " + mid);
		LOGGER.info("Chunk waypoint: {}", requireMessageWith(context, "teleport.command.not_loaded.chunk").getString());
		typeCommand(context, "tp @c " + far);
		LOGGER.info("Direction waypoint: {}", requireMessageWith(context, "teleport.command.not_loaded.direction").getString());
		context.takeScreenshot("teleport-locator-bar");
		server.runCommand("kill @e[type=minecraft:pig]");
	}

	/**
	 * Summons a pig without AI at {@code pos}, keeps its chunk loaded, and returns its UUID.
	 */
	private static UUID summonPig(final TestDedicatedServerContext server, final BlockPos pos, final String tag) {
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", pos.getX(), pos.getZ()));
		server.waitFor(s -> s.overworld().isLoaded(pos));
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:pig %d %d %d {NoAI:1b,Tags:[\"%s\"]}", pos.getX(), pos.getY(), pos.getZ(), tag));
		// Entities in a chunk that is still loading become visible a little later.
		server.waitFor(s -> tagged(s, tag) != null);
		return server.computeOnServer(s -> tagged(s, tag).getUUID());
	}

	private static @Nullable Entity tagged(final MinecraftServer server, final String tag) {
		for (Entity entity : server.overworld().getAllEntities()) {
			if (entity.entityTags().contains(tag)) {
				return entity;
			}
		}
		return null;
	}
}
