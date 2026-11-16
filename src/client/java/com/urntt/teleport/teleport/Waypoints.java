package com.urntt.teleport.teleport;

import com.urntt.teleport.mixin.AzimuthWaypointAccessor;
import com.urntt.teleport.mixin.ChunkWaypointAccessor;
import com.urntt.teleport.mixin.Vec3iWaypointAccessor;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.Nullable;

/**
 * Reads what the locator bar knows about an entity. Depending on the distance, the server sends the block the
 * entity is in, only its chunk, or only the direction it is in.
 */
public final class Waypoints {
	private Waypoints() {
	}

	/**
	 * Returns the locator bar waypoint of the entity with {@code uuid}, if the server sends one.
	 */
	public static Optional<TrackedWaypoint> find(final LocalPlayer player, final UUID uuid) {
		TrackedWaypoint[] found = new TrackedWaypoint[1];
		player.connection.getWaypointManager().forEachWaypoint(player, waypoint -> {
			if (waypoint.id().left().filter(uuid::equals).isPresent()) {
				found[0] = waypoint;
			}
		});
		return Optional.ofNullable(found[0]);
	}

	/**
	 * Returns the block the waypoint's source is in, or {@code null} if the waypoint only knows less.
	 */
	public static @Nullable Vec3i blockPosition(final TrackedWaypoint waypoint) {
		return waypoint instanceof Vec3iWaypointAccessor block ? block.teleport$vector() : null;
	}

	/**
	 * Describes what an inexact waypoint knows about {@code name}, or returns {@code null} if it knows nothing.
	 */
	public static @Nullable Component describeInexact(final TrackedWaypoint waypoint, final Component name) {
		if (waypoint instanceof ChunkWaypointAccessor chunk) {
			ChunkPos pos = chunk.teleport$chunkPos();
			return Component.translatable("teleport.command.not_loaded.chunk", name, pos.x(), pos.z(),
					pos.getMinBlockX(), pos.getMaxBlockX(), pos.getMinBlockZ(), pos.getMaxBlockZ());
		}
		if (waypoint instanceof AzimuthWaypointAccessor azimuth) {
			float yaw = Mth.wrapDegrees(azimuth.teleport$angle() * Mth.RAD_TO_DEG);
			Direction direction = Direction.fromYRot(yaw);
			return Component.translatable("teleport.command.not_loaded.direction", name,
					String.format(Locale.ROOT, "%.0f", yaw),
					Component.translatable("teleport.direction." + direction.getSerializedName()));
		}
		return null;
	}
}
