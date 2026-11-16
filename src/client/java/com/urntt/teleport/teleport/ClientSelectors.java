package com.urntt.teleport.teleport;

import com.urntt.teleport.mixin.EntitySelectorAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Evaluates entity selectors on the client, against the entities the client has loaded. It follows vanilla's
 * {@code EntitySelector.findEntities} and {@code findPlayers}, with the client level standing in for the server's
 * levels and the local player as the executor. Selector arguments that need server data (tags, scores,
 * advancements, predicates, game modes, levels) do not match anything on the client.
 */
public final class ClientSelectors {
	private ClientSelectors() {
	}

	public static List<? extends Entity> find(final EntitySelector selector, final LocalPlayer player) throws CommandSyntaxException {
		try {
			return findUnchecked(selector, player);
		} catch (RuntimeException e) {
			// Predicates that read server state, such as scores, fail on the client.
			throw TeleportErrors.SELECTOR_UNSUPPORTED.create();
		}
	}

	private static List<? extends Entity> findUnchecked(final EntitySelector selector, final LocalPlayer player) {
		EntitySelectorAccessor accessor = (EntitySelectorAccessor) (Object) selector;
		ClientLevel level = (ClientLevel) player.level();

		String playerName = accessor.teleport$playerName();
		if (playerName != null) {
			for (AbstractClientPlayer candidate : level.players()) {
				if (candidate.getGameProfile().name().equalsIgnoreCase(playerName)) {
					return List.of(candidate);
				}
			}
			return List.of();
		}

		UUID uuid = accessor.teleport$entityUuid();
		if (uuid != null) {
			Entity entity = level.getEntity(uuid);
			if (entity == null || !(selector.includesEntities() || entity instanceof Player)
					|| !entity.getType().isEnabled(level.enabledFeatures())) {
				return List.of();
			}
			return List.of(entity);
		}

		Vec3 pos = accessor.teleport$position().apply(player.position());
		AABB absoluteAabb = accessor.teleport$getAbsoluteAabb(pos);
		if (accessor.teleport$currentEntity()) {
			Predicate<Entity> predicate = accessor.teleport$getPredicate(pos, absoluteAabb, null);
			return predicate.test(player) ? List.of(player) : List.of();
		}

		List<Entity> result = new ArrayList<>();
		if (selector.includesEntities()) {
			Predicate<Entity> predicate = accessor.teleport$getPredicate(pos, absoluteAabb, level.enabledFeatures());
			for (Entity entity : level.entitiesForRendering()) {
				if (accessor.teleport$type().tryCast(entity) != null && predicate.test(entity)) {
					result.add(entity);
				}
			}
		} else {
			Predicate<Entity> predicate = accessor.teleport$getPredicate(pos, absoluteAabb, null);
			for (AbstractClientPlayer candidate : level.players()) {
				if (predicate.test(candidate)) {
					result.add(candidate);
				}
			}
		}
		return accessor.teleport$sortAndLimit(pos, result);
	}
}
