package com.urntt.teleport.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.config.TeleportConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("teleport-gametest");
	/** Positions closer than this, in blocks, count as equal. */
	static final double EPSILON = 1.0E-3;

	/** Game messages the client received since the last {@link #clearMessages}, including the mod's own feedback. */
	private static final List<Component> MESSAGES = new ArrayList<>();
	private static boolean listening;

	private GameTestSupport() {
	}

	/**
	 * Starts collecting game messages. Call it once per test class before relying on {@link #messages}.
	 */
	static void listenForMessages(final ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (!listening) {
				listening = true;
				ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add(message));
			}
			MESSAGES.clear();
		});
	}

	static void clearMessages(final ClientGameTestContext context) {
		context.runOnClient(client -> MESSAGES.clear());
	}

	static List<Component> messages(final ClientGameTestContext context) {
		return context.computeOnClient(client -> List.copyOf(MESSAGES));
	}

	/**
	 * Returns the newest received message that uses the translation key {@code key} anywhere inside it.
	 */
	static Optional<Component> lastMessageWith(final ClientGameTestContext context, final String key) {
		List<Component> messages = messages(context);
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (usesKey(messages.get(i), key)) {
				return Optional.of(messages.get(i));
			}
		}
		return Optional.empty();
	}

	/**
	 * Returns whether a message with {@code key} arrived; call it on the client thread, for example in
	 * {@code context.waitFor}.
	 */
	static boolean receivedOnClient(final String key) {
		return MESSAGES.stream().anyMatch(message -> usesKey(message, key));
	}

	static Component requireMessageWith(final ClientGameTestContext context, final String key) {
		return lastMessageWith(context, key).orElseThrow(() -> new AssertionError(
				"expected a message with " + key + ", got " + messages(context).stream().map(Component::getString).toList()));
	}

	static boolean usesKey(final Component component, final String key) {
		if (component.getContents() instanceof TranslatableContents translatable) {
			if (translatable.getKey().equals(key)) {
				return true;
			}
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component argument && usesKey(argument, key)) {
					return true;
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			if (usesKey(sibling, key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the commands that clicking parts of {@code component} would run.
	 */
	static List<String> clickCommands(final Component component) {
		List<String> commands = new ArrayList<>();
		component.visit((Style style, String text) -> {
			if (style.getClickEvent() instanceof ClickEvent.RunCommand run && !commands.contains(run.command())) {
				commands.add(run.command());
			}
			return Optional.empty();
		}, Style.EMPTY);
		return commands;
	}

	/**
	 * Sends {@code command} the way the chat screen does when the player types it.
	 */
	static void typeCommand(final ClientGameTestContext context, final String command) {
		LOGGER.info("Typed command: /{}", command);
		context.runOnClient(client -> client.player.connection.sendCommand(command));
	}

	/**
	 * Sends {@code command} the way a click on a chat component that runs it does.
	 */
	static void clickCommand(final ClientGameTestContext context, final String command) {
		LOGGER.info("Clicked command: {}", command);
		context.runOnClient(client -> client.player.connection.sendUnattendedCommand(Commands.trimOptionalPrefix(command), null));
	}

	static Vec3 position(final ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.position());
	}

	static void checkClose(final Vec3 actual, final Vec3 expected, final double tolerance, final String what) {
		check(actual.distanceTo(expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@code key} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name, final String key) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(key));
			KeyMapping.resetMapping();
		});
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<TeleportConfig> change) {
		context.runOnClient(client -> change.accept(TeleportClient.config()));
	}

	static TeleportConfig loadSavedConfig() {
		return TeleportConfig.load(TeleportConfig.defaultPath());
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
