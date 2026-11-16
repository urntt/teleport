package com.urntt.teleport.clicktp;

import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.mixin.LevelExtractorAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Shows where Click TP would put the player: a translucent copy of the local player at the destination, rendered
 * the way vanilla renders an invisible entity that the viewer can still see, for example as a spectator.
 */
public final class ClickTpPreview {
	private ClickTpPreview() {
	}

	public static void append(final LevelExtractor extractor, final Camera camera, final DeltaTracker deltaTracker,
			final LevelRenderState output) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || !TeleportClient.config().clickTpPreview() || !ClickTp.isActive(player)) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(player));
		ClickTp.Target target = ClickTp.findTarget(player, TeleportClient.config().clickTpReach(), partialTick);
		if (target == null) {
			return;
		}

		EntityRenderState state = ((LevelExtractorAccessor) extractor).teleport$extractEntity(player, partialTick);
		Vec3 destination = target.destination();
		state.x = destination.x;
		state.y = destination.y;
		state.z = destination.z;
		state.distanceToCameraSq = camera.position().distanceToSqr(destination);
		BlockPos lightProbe = BlockPos.containing(destination.add(0.0, player.getEyeHeight(), 0.0));
		state.lightCoords = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, lightProbe), level.getBrightness(LightLayer.SKY, lightProbe));
		makeTranslucent(state);
		output.entityRenderStates.add(state);
		output.lastEntityRenderStateCount = output.entityRenderStates.size();
	}

	/**
	 * Selects vanilla's translucent rendering for an invisible entity the viewer can see, and drops everything that
	 * belongs to the real player rather than to a preview: name tag, shadow, outline, fire and hurt overlay.
	 */
	private static void makeTranslucent(final EntityRenderState state) {
		state.isInvisible = true;
		state.displayFireAnimation = false;
		state.nameTag = null;
		state.scoreText = null;
		state.nameTagAttachment = null;
		state.shadowRadius = 0.0F;
		state.outlineColor = EntityRenderState.NO_OUTLINE;
		if (state instanceof LivingEntityRenderState living) {
			living.isInvisibleToPlayer = false;
			living.hasRedOverlay = false;
		}
		if (state instanceof AvatarRenderState avatar) {
			// A spectator avatar renders only its head; the preview shows the whole player.
			avatar.isSpectator = false;
		}
	}
}
