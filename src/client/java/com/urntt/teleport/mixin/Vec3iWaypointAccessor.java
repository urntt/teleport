package com.urntt.teleport.mixin;

import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the block position of a locator bar waypoint the server sends for a source in a chunk the player sees.
 */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$Vec3iWaypoint")
public interface Vec3iWaypointAccessor {
	@Accessor("vector")
	Vec3i teleport$vector();
}
