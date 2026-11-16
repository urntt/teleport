package com.urntt.teleport.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the direction of a locator bar waypoint the server sends for a source that is really far away.
 */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$AzimuthWaypoint")
public interface AzimuthWaypointAccessor {
	/** The yaw, in radians, at which the player faces the source. */
	@Accessor("angle")
	float teleport$angle();
}
