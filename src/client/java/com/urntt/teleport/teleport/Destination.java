package com.urntt.teleport.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a client teleport puts the player.
 *
 * <p>The velocity flags follow vanilla {@code /tp}: an axis given as a relative coordinate keeps the player's
 * velocity along it, an absolute one resets it. Vertical velocity is reset anyway unless the player is flying with
 * an elytra.
 *
 * @param target the entity the player teleports to, named in the feedback, or {@code null} for a position
 */
public record Destination(
		ResourceKey<Level> dimension,
		Vec3 position,
		float yRot,
		float xRot,
		boolean keepVelocityX,
		boolean keepVelocityY,
		boolean keepVelocityZ,
		@Nullable Component target) {
	/**
	 * Returns a destination at a fixed position that resets the player's velocity.
	 */
	public static Destination absolute(final ResourceKey<Level> dimension, final Vec3 position, final float yRot,
			final float xRot, final @Nullable Component target) {
		return new Destination(dimension, position, yRot, xRot, false, false, false, target);
	}

	public static Destination of(final SavedLocation location) {
		return absolute(location.dimension(), location.position(), location.yRot(), location.xRot(), null);
	}
}
