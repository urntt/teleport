package com.urntt.teleport.teleport;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Chat messages of the mod's commands, formatted like vanilla command feedback.
 */
public final class TeleportFeedback {
	/** Prefix of every command this mod handles; also used by click events. */
	public static final String COMMAND_PREFIX = "/tp @c";

	private TeleportFeedback() {
	}

	public static void send(final LocalPlayer player, final Component message) {
		player.sendSystemMessage(message);
	}

	/**
	 * Shows an error the way vanilla shows a failed command: the message in red, followed by the part of the input
	 * that caused it.
	 */
	public static void sendError(final LocalPlayer player, final CommandSyntaxException e, final String command) {
		send(player, ComponentUtils.fromMessage(e.getRawMessage()).copy().withStyle(ChatFormatting.RED));
		if (e.getInput() != null && e.getCursor() >= 0) {
			int cursor = Math.min(e.getInput().length(), e.getCursor());
			MutableComponent context = Component.empty()
					.withStyle(ChatFormatting.GRAY)
					.withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/" + command)));
			if (cursor > 10) {
				context.append(CommonComponents.ELLIPSIS);
			}
			context.append(e.getInput().substring(Math.max(0, cursor - 10), cursor));
			if (cursor < e.getInput().length()) {
				context.append(Component.literal(e.getInput().substring(cursor)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE));
			}
			context.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
			send(player, context);
		}
	}

	/**
	 * Formats a coordinate for vanilla's teleport success messages, which use {@code %f}.
	 */
	public static String formatSuccess(final double value) {
		return String.format(Locale.ROOT, "%f", value);
	}

	/**
	 * Formats a coordinate or angle for display and for commands the player can click.
	 */
	public static String format(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	public static Component dimensionName(final ResourceKey<Level> dimension) {
		return Component.literal(dimension.identifier().toString());
	}

	/**
	 * Returns the command that teleports to {@code destination}'s position and rotation.
	 */
	public static String commandFor(final Destination destination) {
		Vec3 pos = destination.position();
		return COMMAND_PREFIX + " " + format(pos.x) + " " + format(pos.y) + " " + format(pos.z) + " "
				+ format(destination.yRot()) + " " + format(destination.xRot());
	}

	/**
	 * Returns the coordinates of {@code destination} in green brackets, like vanilla's {@code /locate} result:
	 * clicking them puts the matching {@code /tp @c} command into the chat box.
	 */
	public static Component coordinates(final Destination destination) {
		Vec3 pos = destination.position();
		return ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", format(pos.x), format(pos.y), format(pos.z)))
				.withStyle(s -> s.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand(commandFor(destination)))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
	}

	/**
	 * Returns the {@code get} output for {@code destination}.
	 */
	public static Component destination(final Destination destination) {
		return Component.translatable("teleport.command.get", coordinates(destination),
				format(destination.yRot()), format(destination.xRot()), dimensionName(destination.dimension()));
	}
}
