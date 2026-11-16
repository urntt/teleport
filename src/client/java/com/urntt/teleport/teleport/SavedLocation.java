package com.urntt.teleport.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A position with rotation in a dimension, as stored for {@code back}, {@code home}, {@code temp} and {@code spawn}.
 */
public record SavedLocation(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot) {
	/**
	 * Returns where {@code entity} is now.
	 */
	public static SavedLocation of(final Entity entity) {
		return new SavedLocation(entity.level().dimension(), entity.position(), entity.getYRot(), entity.getXRot());
	}
}
