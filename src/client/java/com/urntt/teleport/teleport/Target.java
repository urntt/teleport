package com.urntt.teleport.teleport;

import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * The resolved destination of a teleport command.
 */
public sealed interface Target {
	/** A position the client teleports the player to. */
	record Position(Destination destination) implements Target {
	}

	/**
	 * An entity the client does not know the position of, reached through vanilla's spectator teleport, which the
	 * server performs.
	 */
	record Spectator(UUID uuid, Component name) implements Target {
	}
}
