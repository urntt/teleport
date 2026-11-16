package com.urntt.teleport.teleport;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.urntt.teleport.mixin.EntitySelectorAccessor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.Nullable;

/**
 * Turns the arguments of {@code /tp @c} into a {@link Target}, using only what the client knows. Coordinates and
 * rotations are evaluated like vanilla {@code /tp} with the local player as the executor, anchored at its feet.
 */
public final class DestinationResolver {
	/** Where a {@code facing} argument makes the player look. */
	public sealed interface Facing {
		record AtPosition(Coordinates position) implements Facing {
		}

		record AtEntity(EntitySelector entity, EntityAnchorArgument.Anchor anchor) implements Facing {
		}
	}

	private DestinationResolver() {
	}

	/**
	 * Resolves {@code /tp @c <location> [<rotation> | facing …]}.
	 */
	public static Destination coordinates(final LocalPlayer player, final Coordinates location,
			final @Nullable Coordinates rotation, final @Nullable Facing facing) throws CommandSyntaxException {
		Vec3 origin = player.position();
		Vec2 originRotation = player.getRotationVector();
		Vec3 position = position(location, origin, originRotation);
		if (!Level.isInSpawnableBounds(BlockPos.containing(position))) {
			throw TeleportErrors.INVALID_POSITION.create();
		}

		float yRot = player.getYRot();
		float xRot = player.getXRot();
		if (rotation != null) {
			Vec2 resolved = rotation(rotation, originRotation);
			yRot = Mth.wrapDegrees(resolved.y);
			xRot = Mth.wrapDegrees(resolved.x);
		}
		if (facing != null) {
			Vec3 lookTarget = switch (facing) {
				case Facing.AtPosition at -> position(at.position(), origin, originRotation);
				case Facing.AtEntity at -> at.anchor().apply(singleLoadedEntity(player, at.entity()));
			};
			Vec2 look = lookRotation(position, lookTarget);
			yRot = look.y;
			xRot = look.x;
		}

		return new Destination(player.level().dimension(), position, yRot, xRot,
				location.isXRelative(), location.isYRelative(), location.isZRelative(), null);
	}

	/**
	 * Resolves {@code /tp @c <destination>}. A loaded entity is the destination itself. A player name or UUID the
	 * client has not loaded falls back to the spectator teleport while the player is a spectator, and then to an exact
	 * locator bar waypoint.
	 */
	public static Target entity(final LocalPlayer player, final EntitySelector selector) throws CommandSyntaxException {
		List<? extends Entity> found = ClientSelectors.find(selector, player);
		if (!found.isEmpty()) {
			Entity entity = found.getFirst();
			return new Target.Position(Destination.absolute(entity.level().dimension(), entity.position(),
					entity.getYRot(), entity.getXRot(), entity.getDisplayName()));
		}

		EntitySelectorAccessor accessor = (EntitySelectorAccessor) (Object) selector;
		UUID uuid;
		Component name;
		if (accessor.teleport$playerName() != null) {
			PlayerInfo info = playerInfo(player.connection, accessor.teleport$playerName());
			if (info == null) {
				throw TeleportErrors.NO_ENTITY.create();
			}
			uuid = info.getProfile().id();
			name = Component.literal(info.getProfile().name());
		} else if (accessor.teleport$entityUuid() != null) {
			uuid = accessor.teleport$entityUuid();
			PlayerInfo info = player.connection.getPlayerInfo(uuid);
			name = Component.literal(info != null ? info.getProfile().name() : uuid.toString());
		} else {
			throw TeleportErrors.NO_ENTITY.create();
		}
		return unloaded(player, uuid, name);
	}

	/**
	 * Resolves an entity the client has not loaded, identified by {@code uuid}.
	 */
	public static Target unloaded(final LocalPlayer player, final UUID uuid, final Component name) throws CommandSyntaxException {
		if (player.isSpectator()) {
			return new Target.Spectator(uuid, name);
		}

		Optional<TrackedWaypoint> waypoint = Waypoints.find(player, uuid);
		if (waypoint.isPresent()) {
			Vec3i block = Waypoints.blockPosition(waypoint.get());
			if (block != null) {
				return new Target.Position(Destination.absolute(player.level().dimension(), Vec3.atBottomCenterOf(block),
						player.getYRot(), player.getXRot(), name));
			}
			Component known = Waypoints.describeInexact(waypoint.get(), name);
			if (known != null) {
				throw TeleportErrors.NOT_LOADED_WAYPOINT.create(known);
			}
		}
		throw TeleportErrors.NOT_LOADED.create(name);
	}

	/**
	 * Resolves {@code /tp @c back [kind]}: the newest recorded position of that kind in the current dimension. When
	 * this session recorded no death, the death position vanilla keeps for the recovery compass counts as the oldest
	 * death entry.
	 */
	public static Destination back(final LocalPlayer player, final BackHistory history, final BackHistory.@Nullable Kind kind)
			throws CommandSyntaxException {
		Optional<SavedLocation> found = history.latest(kind, player.level().dimension(), player.position());
		if (found.isEmpty() && (kind == null || kind == BackHistory.Kind.DEATH)) {
			found = vanillaDeathLocation(player)
					.filter(location -> location.dimension() == player.level().dimension()
							&& location.position().distanceTo(player.position()) >= BackHistory.MIN_DISTANCE);
		}
		return Destination.of(found.orElseThrow(TeleportErrors.NO_BACK::create));
	}

	/**
	 * Resolves {@code /tp @c spawn}. For a bed or respawn anchor in a loaded chunk, the destination is where vanilla
	 * would respawn the player next to it, facing it.
	 */
	public static Destination spawn(final LocalPlayer player, final SavedLocations.SpawnPoint spawn) {
		BlockPos block = spawn.block();
		ClientLevel level = (ClientLevel) player.level();
		if (block != null && spawn.location().dimension() == level.dimension() && level.isLoaded(block)) {
			BlockState state = level.getBlockState(block);
			Optional<Vec3> standUp = Optional.empty();
			if (state.getBlock() instanceof AbstractBedBlock) {
				standUp = AbstractBedBlock.findStandUpPosition(EntityTypes.PLAYER, level, block,
						state.getValue(AbstractBedBlock.FACING), spawn.location().yRot());
			} else if (state.getBlock() instanceof RespawnAnchorBlock) {
				standUp = RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, level, block);
			}
			if (standUp.isPresent()) {
				return Destination.absolute(level.dimension(), standUp.get(), lookAtBlockYaw(standUp.get(), block), 0.0F, null);
			}
		}
		return Destination.of(spawn.location());
	}

	/**
	 * Resolves {@code /tp @c worldspawn} from the world spawn the server sends to every client.
	 */
	public static Destination worldSpawn(final LocalPlayer player) {
		LevelData.RespawnData data = ((ClientLevel) player.level()).getRespawnData();
		return Destination.absolute(data.dimension(), Vec3.atBottomCenterOf(data.pos()), data.yaw(), data.pitch(), null);
	}

	/**
	 * Returns the death position vanilla syncs to the client for the recovery compass.
	 */
	public static Optional<SavedLocation> vanillaDeathLocation(final LocalPlayer player) {
		Optional<GlobalPos> death = player.getLastDeathLocation();
		return death.map(pos -> new SavedLocation(pos.dimension(), Vec3.atBottomCenterOf(pos.pos()), player.getYRot(), player.getXRot()));
	}

	/**
	 * Evaluates position coordinates relative to {@code origin} and {@code rotation} (pitch, yaw), like vanilla's
	 * {@link Coordinates#getPosition} does for a command source.
	 */
	public static Vec3 position(final Coordinates coordinates, final Vec3 origin, final Vec2 rotation) {
		return switch (coordinates) {
			case WorldCoordinates world -> new Vec3(world.x().get(origin.x), world.y().get(origin.y), world.z().get(origin.z));
			case LocalCoordinates local -> local.apply(origin, rotation);
			default -> throw new IllegalArgumentException("Unsupported coordinates " + coordinates);
		};
	}

	/**
	 * Evaluates rotation coordinates relative to {@code rotation} (pitch, yaw), like vanilla's
	 * {@link Coordinates#getRotation} does for a command source.
	 */
	public static Vec2 rotation(final Coordinates coordinates, final Vec2 rotation) {
		if (coordinates instanceof WorldCoordinates world) {
			return new Vec2((float) world.x().get(rotation.x), (float) world.y().get(rotation.y));
		}
		return Vec2.ZERO;
	}

	/**
	 * Returns the rotation (pitch, yaw) that looks from {@code from} at {@code target}, with the same formula as
	 * vanilla's {@code Entity.lookAt}.
	 */
	public static Vec2 lookRotation(final Vec3 from, final Vec3 target) {
		double xd = target.x - from.x;
		double yd = target.y - from.y;
		double zd = target.z - from.z;
		double sd = Math.sqrt(xd * xd + zd * zd);
		float xRot = Mth.wrapDegrees((float) (-(Mth.atan2(yd, sd) * 180.0F / (float) Math.PI)));
		float yRot = Mth.wrapDegrees((float) (Mth.atan2(zd, xd) * 180.0F / (float) Math.PI) - 90.0F);
		return new Vec2(xRot, yRot);
	}

	/**
	 * Returns the yaw that looks at {@code block}, with the same formula vanilla uses when respawning a player next to
	 * a bed or respawn anchor.
	 */
	private static float lookAtBlockYaw(final Vec3 position, final BlockPos block) {
		Vec3 direction = Vec3.atBottomCenterOf(block).subtract(position).normalize();
		return (float) Mth.wrapDegrees(Mth.atan2(direction.z, direction.x) * 180.0F / (float) Math.PI - 90.0);
	}

	private static Entity singleLoadedEntity(final LocalPlayer player, final EntitySelector selector) throws CommandSyntaxException {
		List<? extends Entity> found = ClientSelectors.find(selector, player);
		if (found.isEmpty()) {
			throw TeleportErrors.NO_ENTITY.create();
		}
		return found.getFirst();
	}

	/**
	 * Finds a player in the tab list by name, ignoring case like vanilla's player lookup.
	 */
	public static @Nullable PlayerInfo playerInfo(final ClientPacketListener connection, final String name) {
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(name)) {
				return info;
			}
		}
		return null;
	}
}
