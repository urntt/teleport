package com.urntt.teleport.mixin;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the chunk of a locator bar waypoint the server sends for a source in a chunk the player does not see.
 */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$ChunkWaypoint")
public interface ChunkWaypointAccessor {
	@Accessor("chunkPos")
	ChunkPos teleport$chunkPos();
}
