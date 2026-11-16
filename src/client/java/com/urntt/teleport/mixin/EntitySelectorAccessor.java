package com.urntt.teleport.mixin;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes what an {@link EntitySelector} parsed, so the client can evaluate it against the entities it has loaded
 * the same way vanilla's {@code findEntities} does on the server.
 */
@Mixin(EntitySelector.class)
public interface EntitySelectorAccessor {
	@Accessor("playerName")
	@Nullable String teleport$playerName();

	@Accessor("entityUUID")
	@Nullable UUID teleport$entityUuid();

	@Accessor("position")
	Function<Vec3, Vec3> teleport$position();

	@Accessor("currentEntity")
	boolean teleport$currentEntity();

	@Accessor("type")
	EntityTypeTest<Entity, ?> teleport$type();

	@Invoker("getAbsoluteAabb")
	@Nullable AABB teleport$getAbsoluteAabb(Vec3 pos);

	@Invoker("getPredicate")
	Predicate<Entity> teleport$getPredicate(Vec3 pos, @Nullable AABB absoluteAabb, @Nullable FeatureFlagSet enabledFeatures);

	@Invoker("sortAndLimit")
	<T extends Entity> List<T> teleport$sortAndLimit(Vec3 pos, List<T> result);
}
