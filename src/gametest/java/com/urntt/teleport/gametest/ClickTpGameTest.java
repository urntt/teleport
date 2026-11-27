package com.urntt.teleport.gametest;

import static com.urntt.teleport.gametest.GameTestSupport.LOGGER;
import static com.urntt.teleport.gametest.GameTestSupport.bindKey;
import static com.urntt.teleport.gametest.GameTestSupport.check;
import static com.urntt.teleport.gametest.GameTestSupport.checkClose;
import static com.urntt.teleport.gametest.GameTestSupport.clearMessages;
import static com.urntt.teleport.gametest.GameTestSupport.configure;
import static com.urntt.teleport.gametest.GameTestSupport.listenForMessages;
import static com.urntt.teleport.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.teleport.gametest.GameTestSupport.position;
import static com.urntt.teleport.gametest.GameTestSupport.requireMessageWith;
import static com.urntt.teleport.gametest.GameTestSupport.unbindKey;

import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.config.ConfigMode;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.teleport.BackHistory;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Checks Click TP in a singleplayer world: the toggle key, that it needs the mod to be on, where each clicked face
 * leads, that it replaces the use action, repeating while the key is held, sneaking, the reach, entities under the
 * crosshair, the destination preview, and how the server treats a teleport down.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickTpGameTest implements FabricClientGameTest {
	/** Closer than this, in blocks, counts as the same position for a player standing still. */
	private static final double STANDING = 0.05;

	@Override
	public void runTest(final ClientGameTestContext context) {
		listenForMessages(context);
		// With OneConfig installed and no mode chosen, OneConfig mode applies, where the mod starts turned off.
		configure(context, config -> config.setMode(ConfigMode.CUSTOM));
		KeyMapping toggleKey = bindKey(context, TeleportClient.TOGGLE_CLICK_TP_KEY_NAME, "key.keyboard.j");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("time set day");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:stone 64");
			context.waitFor(client -> client.player != null && client.player.onGround());
			context.waitTicks(5);
			// The previous test class left another world, and back must not lead into it.
			check(context.computeOnClient(client -> TeleportClient.history().entries().isEmpty()),
					"a new world should start with an empty back history");

			checkToggleKey(context, toggleKey);
			checkTurnedOff(context, toggleKey, server);
			checkFaces(context, server);
			checkRepeat(context);
			checkSneaking(context, server);
			checkReach(context, server);
			checkEntityUnderCrosshair(context, server);
			checkPreview(context);
			checkDrop(context, server);
		}

		configure(context, config -> {
			config.setMode(ConfigMode.CUSTOM);
			config.setClickTp(false);
			config.setClickTpReach(TeleportConfig.DEFAULT_CLICK_TP_REACH);
		});
		unbindKey(context, toggleKey);
	}

	private static void checkToggleKey(final ClientGameTestContext context, final KeyMapping toggleKey) {
		check(!isClickTpEnabled(context), "Click TP should start off");
		context.getInput().pressKey(toggleKey);
		context.waitTick();
		check(isClickTpEnabled(context), "the toggle key should turn Click TP on");
		check(loadSavedConfig().clickTp(), "the new state should be saved");
		context.takeScreenshot("clicktp-toggled-on");
	}

	/**
	 * In Disabled mode, Click TP does nothing even though it is on, and its toggle key only reports why.
	 */
	private static void checkTurnedOff(final ClientGameTestContext context, final KeyMapping toggleKey, final TestServerContext server) {
		configure(context, config -> config.setMode(ConfigMode.DISABLED));
		BlockPos ground = BlockPos.containing(position(context)).offset(0, -1, 3);
		Vec3 before = position(context);
		clearMessages(context);
		clickTop(context, ground);
		context.waitTicks(2);
		check(position(context).distanceTo(before) < STANDING, "Click TP should not teleport in Disabled mode");
		context.waitFor(client -> client.level.getBlockState(ground.above()).is(Blocks.STONE), 40);

		context.getInput().pressKey(toggleKey);
		context.waitTick();
		check(isClickTpEnabled(context), "the Click TP key should not change Click TP in Disabled mode");
		requireMessageWith(context, "message.teleport.turned_off");

		fill(context, server, ground.above(), ground.above(), "minecraft:air");
		configure(context, config -> config.setMode(ConfigMode.CUSTOM));
	}

	/**
	 * The clicked face decides the destination, and clicking replaces placing the held stone.
	 */
	private static void checkFaces(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos feet = BlockPos.containing(position(context));

		BlockPos ground = feet.offset(0, -1, 10);
		clickTop(context, ground);
		checkClose(position(context), Vec3.atBottomCenterOf(ground.above()), STANDING, "clicking the top face should put the player on the block");
		check(context.computeOnClient(client -> client.level.getBlockState(ground.above()).isAir()), "Click TP should not place the held block");
		context.waitTicks(5);
		checkClose(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position()), position(context), STANDING,
				"the integrated server should accept the Click TP");

		feet = BlockPos.containing(position(context));
		BlockPos wall = feet.east(8);
		fill(context, server, wall.offset(0, 0, -1), wall.offset(0, 3, 1), "minecraft:stone");
		click(context, wall);
		checkClose(position(context), Vec3.atBottomCenterOf(wall.west()), STANDING, "clicking a side face should put the player next to the block");

		feet = BlockPos.containing(position(context));
		BlockPos ceiling = feet.offset(-3, 5, 0);
		fill(context, server, ceiling, ceiling, "minecraft:stone");
		click(context, ceiling);
		Vec3 below = position(context);
		double expectedY = ceiling.getY() - (double) context.computeOnClient(client -> client.player.getBbHeight());
		check(Math.abs(below.x - (ceiling.getX() + 0.5)) < STANDING && Math.abs(below.z - (ceiling.getZ() + 0.5)) < STANDING
						&& below.y <= expectedY + STANDING && below.y > expectedY - 1.0,
				"clicking the bottom face should put the player right below the block, got " + below);
		context.waitFor(client -> client.player.onGround());
		context.waitTicks(5);
	}

	/**
	 * Holding the use key keeps teleporting at vanilla's use pace.
	 */
	private static void checkRepeat(final ClientGameTestContext context) {
		int before = clientTeleports(context);
		Vec3 start = position(context);
		context.getInput().lookAt(0.0F, 30.0F);
		context.getInput().holdKeyFor(options -> options.keyUse, 20);
		int teleports = clientTeleports(context) - before;
		Vec3 end = position(context);
		LOGGER.info("Click TP teleports while holding the use key for 20 ticks: {}, moved {} blocks", teleports, start.distanceTo(end));
		check(teleports >= 3, "holding the use key should repeat Click TP, got " + teleports + " teleports");
		check(end.distanceTo(start) > 5.0, "repeated Click TP should keep moving the player forward");
		context.waitFor(client -> client.player.onGround());
		context.waitTicks(5);
	}

	private static void checkSneaking(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos feet = BlockPos.containing(position(context));
		BlockPos ground = feet.offset(0, -1, 3);
		Vec3 before = position(context);
		context.getInput().holdKey(options -> options.keyShift);
		context.waitTicks(2);
		clickTop(context, ground);
		context.getInput().releaseKey(options -> options.keyShift);
		context.waitTicks(2);
		check(position(context).distanceTo(before) < 0.5, "sneaking should turn Click TP off temporarily, moved to " + position(context));
		context.waitFor(client -> client.level.getBlockState(ground.above()).is(Blocks.STONE), 40);
		fill(context, server, ground.above(), ground.above(), "minecraft:air");
	}

	private static void checkReach(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos feet = BlockPos.containing(position(context));
		BlockPos far = feet.east(80);
		fill(context, server, far.offset(0, 0, -1), far.offset(0, 3, 1), "minecraft:stone");

		Vec3 before = position(context);
		click(context, far);
		check(position(context).distanceTo(before) < STANDING, "a block beyond the default reach of 64 should not teleport");

		configure(context, config -> config.setClickTpReach(100));
		click(context, far);
		checkClose(position(context), Vec3.atBottomCenterOf(far.west()), STANDING, "a larger reach should reach the block");

		configure(context, config -> config.setClickTpReach(0));
		before = position(context);
		clickTop(context, feet.offset(0, -1, -3));
		check(position(context).distanceTo(before) < STANDING, "a reach of 0 should never teleport");
		configure(context, config -> config.setClickTpReach(TeleportConfig.DEFAULT_CLICK_TP_REACH));
		context.waitTicks(5);
	}

	private static void checkEntityUnderCrosshair(final ClientGameTestContext context, final TestServerContext server) {
		Vec3 pos = position(context);
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:pig %.3f %.3f %.3f {NoAI:1b}", pos.x - 2.5, pos.y, pos.z));
		context.waitFor(client -> client.level.getEntities(client.player, client.player.getBoundingBox().inflate(4.0)).stream()
				.anyMatch(entity -> entity.getType() == EntityTypes.PIG));
		context.getInput().lookAt(BlockPos.containing(pos.x - 2.5, pos.y + 0.5, pos.z));
		context.waitTick();
		check(context.computeOnClient(client -> client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY),
				"the crosshair should be on the pig");
		Vec3 before = position(context);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(2);
		check(position(context).distanceTo(before) < STANDING, "an entity under the crosshair should keep vanilla's interaction");
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(5);
	}

	private static void checkPreview(final ClientGameTestContext context) {
		BlockPos feet = BlockPos.containing(position(context));
		context.getInput().lookAt(feet.offset(-12, -1, 4));
		context.waitTicks(2);
		context.takeScreenshot("clicktp-preview");
	}

	/**
	 * Measures what the README warns about: even when Click TP puts the player on a block, the server counts the
	 * height teleported down as a fall, because a client teleport is ordinary movement to the server.
	 */
	private static void checkDrop(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos feet = BlockPos.containing(position(context));
		int height = 15;
		fill(context, server, feet, feet.above(height - 1), "minecraft:stone");
		server.runCommand("gamemode survival @a");
		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", feet.getX(), feet.getY() + height, feet.getZ()));
		context.waitFor(client -> client.player.onGround() && client.player.getY() >= feet.getY() + height - 0.01);
		context.waitTicks(10);
		float healthBefore = context.computeOnClient(client -> client.player.getHealth());

		BlockPos landing = feet.offset(6, -1, 0);
		clickTop(context, landing);
		checkClose(position(context), Vec3.atBottomCenterOf(landing.above()), STANDING, "Click TP down should land on the block");
		context.waitTicks(5);
		float healthAfter = context.computeOnClient(client -> client.player.getHealth());
		LOGGER.info("Health before a {}-block Click TP down onto a block: {}, after: {}", height, healthBefore, healthAfter);
		// Vanilla fall damage is the fall height minus 3.
		check(healthBefore - healthAfter >= height - 3 - 1,
				"the server should count the teleport down as a fall, lost only " + (healthBefore - healthAfter));
		server.runCommand("gamemode creative @a");
		server.runCommand("effect give @a minecraft:instant_health 1 10");
	}

	/**
	 * Looks at the middle of {@code block} and presses the use key once.
	 */
	private static void click(final ClientGameTestContext context, final BlockPos block) {
		context.getInput().lookAt(block);
		context.waitTick();
		pressUse(context);
	}

	/**
	 * Looks at the middle of the top face of {@code block}, just below its surface so that the line of sight does not
	 * graze the blocks in front, and presses the use key once.
	 */
	private static void clickTop(final ClientGameTestContext context, final BlockPos block) {
		Vec3 point = new Vec3(block.getX() + 0.5, block.getY() + 1.0 - 0.01, block.getZ() + 0.5);
		context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, point));
		context.waitTick();
		pressUse(context);
	}

	private static void pressUse(final ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTick();
	}

	private static void fill(final ClientGameTestContext context, final TestServerContext server, final BlockPos from, final BlockPos to,
			final String block) {
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", from.getX(), from.getY(), from.getZ(),
				to.getX(), to.getY(), to.getZ(), block));
		boolean air = block.endsWith(":air");
		context.waitFor(client -> client.level.getBlockState(to).isAir() == air);
	}

	private static boolean isClickTpEnabled(final ClientGameTestContext context) {
		return context.computeOnClient(client -> TeleportClient.config().clickTp());
	}

	private static int clientTeleports(final ClientGameTestContext context) {
		return context.computeOnClient(client -> (int) TeleportClient.history().entries().stream()
				.filter(entry -> entry.kind() == BackHistory.Kind.CLIENT).count());
	}
}
