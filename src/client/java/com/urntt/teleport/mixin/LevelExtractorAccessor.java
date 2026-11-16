package com.urntt.teleport.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes how the level extractor turns an entity into its render state, used for the Click TP preview.
 */
@Mixin(LevelExtractor.class)
public interface LevelExtractorAccessor {
	@Invoker("extractEntity")
	EntityRenderState teleport$extractEntity(Entity entity, float partialTickTime);
}
