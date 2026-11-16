package com.urntt.teleport.mixin;

import com.urntt.teleport.clicktp.ClickTpPreview;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Click TP preview, a translucent copy of the local player at the destination, to the entities rendered
 * this frame.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "extractVisibleEntities", at = @At("RETURN"))
	private void teleport$appendClickTpPreview(final Camera camera, final Frustum frustum, final DeltaTracker deltaTracker,
			final LevelRenderState output, final CallbackInfo ci) {
		ClickTpPreview.append((LevelExtractor) (Object) this, camera, deltaTracker, output);
	}
}
