package com.urntt.teleport.clicktp;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.teleport.ClientTeleporter;
import com.urntt.teleport.teleport.Destination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Click TP: right-clicking a block within the configured reach teleports the player to it.
 */
public final class ClickTp {
	/**
	 * The block the player is looking at and where Click TP would put the player.
	 */
	public record Target(BlockPos block, Direction face, Vec3 destination) {
	}

	private ClickTp() {
	}

	/**
	 * Returns whether right-clicking teleports the player now: Click TP is on, the scene allows it, and the player is
	 * not sneaking, which turns Click TP off temporarily.
	 */
	public static boolean isActive(final LocalPlayer player) {
		return TeleportClient.controller().isClickTpActive() && !player.isShiftKeyDown();
	}

	/**
	 * Finds the block the player looks at within {@code reach} blocks and the destination for it. The destination
	 * depends only on the face looked at, and whether the player fits there is not checked:
	 * <ul>
	 * <li>top face: on top of the block's collision shape, in the middle of the block (inside the block if it has no
	 * collision shape, such as grass);</li>
	 * <li>side face: in the neighboring block on that side, at the height of the block looked at;</li>
	 * <li>bottom face: right below the block, with the player's head touching it.</li>
	 * </ul>
	 */
	public static @Nullable Target findTarget(final LocalPlayer player, final int reach, final float partialTick) {
		if (reach <= 0) {
			return null;
		}
		Level level = player.level();
		Vec3 from = player.getEyePosition(partialTick);
		Vec3 to = from.add(player.getViewVector(partialTick).scale(reach));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos block = hit.getBlockPos().immutable();
		Direction face = hit.getDirection();
		Vec3 destination = switch (face) {
			case UP -> {
				VoxelShape shape = level.getBlockState(block).getCollisionShape(level, block);
				double top = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
				yield new Vec3(block.getX() + 0.5, block.getY() + top, block.getZ() + 0.5);
			}
			// In double precision: float arithmetic would leave the head slightly inside the block.
			case DOWN -> new Vec3(block.getX() + 0.5, block.getY() - (double) player.getBbHeight(), block.getZ() + 0.5);
			default -> {
				BlockPos side = block.relative(face);
				yield new Vec3(side.getX() + 0.5, block.getY(), side.getZ() + 0.5);
			}
		};
		return new Target(block, face, destination);
	}

	/**
	 * Called whenever vanilla would use the held item or the block looked at. Teleports the player and returns
	 * {@code true} if Click TP applies; otherwise returns {@code false} and vanilla goes on as usual. An entity under
	 * the crosshair, which is within vanilla's interaction range, keeps vanilla's interaction.
	 */
	public static boolean onUse(final Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !isActive(player)) {
			return false;
		}
		if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.ENTITY) {
			return false;
		}
		Target target = findTarget(player, TeleportClient.settings().clickTpReach(), 1.0F);
		if (target == null) {
			return false;
		}
		try {
			ClientTeleporter.move(player, TeleportClient.history(),
					Destination.absolute(player.level().dimension(), target.destination(), player.getYRot(), player.getXRot(), null));
		} catch (CommandSyntaxException e) {
			// The destination is always in the player's dimension, so this cannot happen.
			return false;
		}
		return true;
	}
}
