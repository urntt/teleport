package com.urntt.teleport.teleport;

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/**
 * Errors the mod's commands report. Messages that vanilla already has reuse vanilla's translation keys.
 */
public final class TeleportErrors {
	/** Teleporting is ruled out by the multiplayer mode. Also shown on the action bar by the Click TP key. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message.teleport.blocked");
	public static final SimpleCommandExceptionType BLOCKED = new SimpleCommandExceptionType(BLOCKED_MESSAGE);
	/** Same message as vanilla {@code /tp} outside the world bounds. */
	public static final SimpleCommandExceptionType INVALID_POSITION =
			new SimpleCommandExceptionType(Component.translatable("commands.teleport.invalidPosition"));
	/** Same message as a vanilla selector that matches nothing. */
	public static final SimpleCommandExceptionType NO_ENTITY =
			new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.entity"));
	public static final SimpleCommandExceptionType SELECTOR_UNSUPPORTED =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.selector_unsupported"));
	public static final DynamicCommandExceptionType NOT_LOADED =
			new DynamicCommandExceptionType(name -> Component.translatable("teleport.command.not_loaded", name));
	/** Arguments: the target's name and what the locator bar knows about it. */
	public static final DynamicCommandExceptionType NOT_LOADED_WAYPOINT = new DynamicCommandExceptionType(
			info -> (Component) info);
	public static final DynamicCommandExceptionType OTHER_DIMENSION =
			new DynamicCommandExceptionType(dimension -> Component.translatable("teleport.command.other_dimension", dimension));
	public static final SimpleCommandExceptionType NO_BACK =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.no_back"));
	public static final SimpleCommandExceptionType NO_HOME =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.no_home"));
	public static final SimpleCommandExceptionType NO_TEMP =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.no_temp"));
	public static final SimpleCommandExceptionType NO_SPAWN =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.no_spawn"));
	public static final SimpleCommandExceptionType NO_WORLD_SPAWN =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.no_worldspawn"));
	public static final SimpleCommandExceptionType NOT_IN_WORLD =
			new SimpleCommandExceptionType(Component.translatable("teleport.command.not_in_world"));

	private TeleportErrors() {
	}
}
