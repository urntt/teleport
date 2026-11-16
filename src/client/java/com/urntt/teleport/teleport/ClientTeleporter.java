package com.urntt.teleport.teleport;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Performs client teleports: it moves the local player on the client only and leaves reporting the new position to
 * the regular movement packets, which the server validates as usual.
 */
public final class ClientTeleporter {
	private ClientTeleporter() {
	}

	/**
	 * Teleports the local player to {@code destination}, records the old position for {@code back}, and reports the
	 * teleport in the chat.
	 *
	 * <p>Nothing about the player's state is checked: the player moves while riding, sleeping or dead as well. If the
	 * player controls the vehicle it rides, the vehicle moves along, because the client simulates such vehicles;
	 * any other vehicle keeps its passengers where it is.
	 */
	public static void teleport(final LocalPlayer player, final BackHistory history, final Destination destination)
			throws CommandSyntaxException {
		move(player, history, destination);
		Vec3 pos = destination.position();
		Component message = destination.target() != null
				? Component.translatable("commands.teleport.success.entity.single", player.getDisplayName(), destination.target())
				: Component.translatable("commands.teleport.success.location.single", player.getDisplayName(),
						TeleportFeedback.formatSuccess(pos.x), TeleportFeedback.formatSuccess(pos.y), TeleportFeedback.formatSuccess(pos.z));
		TeleportFeedback.send(player, message);
	}

	/**
	 * Moves the local player to {@code destination} without any feedback, as Click TP does.
	 */
	public static void move(final LocalPlayer player, final BackHistory history, final Destination destination)
			throws CommandSyntaxException {
		if (destination.dimension() != player.level().dimension()) {
			throw TeleportErrors.OTHER_DIMENSION.create(TeleportFeedback.dimensionName(destination.dimension()));
		}

		history.recordClientTeleport(SavedLocation.of(player));

		Entity root = player.getRootVehicle();
		Entity mover = root != player && root.isLocalInstanceAuthoritative() ? root : player;
		Vec3 velocity = mover.getDeltaMovement();
		// Like vanilla /tp: absolute axes reset the velocity, relative ones keep it, and the vertical velocity is reset
		// unless the player is flying with an elytra.
		boolean keepVertical = destination.keepVelocityY() && player.isFallFlying();
		Vec3 newVelocity = new Vec3(
				destination.keepVelocityX() ? velocity.x : 0.0,
				keepVertical ? velocity.y : 0.0,
				destination.keepVelocityZ() ? velocity.z : 0.0);
		Vec3 pos = destination.position();

		if (mover == player) {
			player.snapTo(pos.x, pos.y, pos.z, destination.yRot(), destination.xRot());
		} else {
			// Move the vehicle by the player's displacement, so the player ends up at the destination.
			Vec3 vehicleDestination = mover.position().add(pos.subtract(player.position()));
			mover.snapTo(vehicleDestination.x, vehicleDestination.y, vehicleDestination.z, destination.yRot(), mover.getXRot());
			for (Entity passenger : mover.getIndirectPassengers()) {
				Entity vehicle = passenger.getVehicle();
				if (vehicle != null) {
					vehicle.positionRider(passenger);
					passenger.setOldPosAndRot();
				}
			}
			player.setYRot(destination.yRot());
			player.setXRot(destination.xRot());
			player.setOldRot();
		}
		mover.setDeltaMovement(newVelocity);
		player.setYHeadRot(destination.yRot());
		player.resetFallDistance();
	}

	/**
	 * Asks the server to teleport the spectating player to an entity the client has not loaded, exactly like
	 * vanilla's spectator menu does. The server's teleport is recorded for {@code back} like any other server teleport.
	 */
	public static void spectatorTeleport(final LocalPlayer player, final Target.Spectator target) {
		player.connection.send(new ServerboundTeleportToEntityPacket(target.uuid()));
		TeleportFeedback.send(player, Component.translatable("teleport.command.spectator", target.name()));
	}
}
