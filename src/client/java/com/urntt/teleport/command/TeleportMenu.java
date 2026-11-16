package com.urntt.teleport.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.teleport.Destination;
import com.urntt.teleport.teleport.DestinationResolver;
import com.urntt.teleport.teleport.SavedLocation;
import com.urntt.teleport.teleport.Target;
import com.urntt.teleport.teleport.TeleportErrors;
import com.urntt.teleport.teleport.TeleportFeedback;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The chat menus of {@code /tp @c} and {@code /tp @c entities}: every entry is a clickable name that runs the
 * matching {@code /tp @c} command, and its tooltip shows where it leads.
 */
public final class TeleportMenu {
	/** At most this many entities are listed by {@code /tp @c entities}, nearest first. */
	public static final int ENTITY_LIST_LIMIT = 100;

	private TeleportMenu() {
	}

	/**
	 * Shows the destinations the player can teleport to now.
	 */
	public static int showMenu(final LocalPlayer player) {
		MutableComponent menu = Component.translatable("teleport.menu.title").withStyle(ChatFormatting.GOLD);

		MutableComponent players = Component.empty();
		List<Component> playerEntries = playerEntries(player);
		if (playerEntries.isEmpty()) {
			players.append(Component.translatable("teleport.menu.none").withStyle(ChatFormatting.GRAY));
		} else {
			players.append(ComponentUtils.formatList(playerEntries, CommonComponents.SPACE));
		}
		players.append(CommonComponents.SPACE).append(button(Component.translatable("teleport.menu.all_entities"),
				TeleportFeedback.COMMAND_PREFIX + " entities", Component.translatable("teleport.menu.all_entities.hint"), ChatFormatting.AQUA));
		menu.append("\n").append(Component.translatable("teleport.menu.players", players));

		menu.append("\n").append(Component.translatable("teleport.menu.places", ComponentUtils.formatList(placeEntries(player), CommonComponents.SPACE)));

		if (!TeleportClient.controller().isAllowed()) {
			menu.append("\n").append(TeleportErrors.BLOCKED_MESSAGE.copy().withStyle(ChatFormatting.GRAY));
		}
		TeleportFeedback.send(player, menu);
		return 1;
	}

	/**
	 * Lists every entity the client has loaded, nearest first, up to {@link #ENTITY_LIST_LIMIT}.
	 */
	public static int showEntities(final LocalPlayer player) {
		List<Entity> entities = new ArrayList<>();
		for (Entity entity : ((ClientLevel) player.level()).entitiesForRendering()) {
			if (entity != player) {
				entities.add(entity);
			}
		}
		entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));

		List<Component> entries = new ArrayList<>();
		for (Entity entity : entities.subList(0, Math.min(ENTITY_LIST_LIMIT, entities.size()))) {
			entries.add(entityEntry(player, entity));
		}
		MutableComponent message = Component.translatable("teleport.menu.entities", entities.size()).withStyle(ChatFormatting.GOLD);
		message.append("\n");
		if (entries.isEmpty()) {
			message.append(Component.translatable("teleport.menu.none").withStyle(ChatFormatting.GRAY));
		} else {
			message.append(ComponentUtils.formatList(entries, CommonComponents.SPACE));
		}
		if (entities.size() > ENTITY_LIST_LIMIT) {
			message.append(CommonComponents.SPACE)
					.append(Component.translatable("teleport.menu.more", entities.size() - ENTITY_LIST_LIMIT).withStyle(ChatFormatting.GRAY));
		}
		TeleportFeedback.send(player, message);
		return 1;
	}

	/**
	 * Players and other player-like entities (such as mannequins) the player can teleport to: the loaded ones, and
	 * players in the tab list the client has not loaded but can still reach.
	 */
	private static List<Component> playerEntries(final LocalPlayer player) {
		List<Component> entries = new ArrayList<>();
		ClientLevel level = (ClientLevel) player.level();
		List<Entity> avatars = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof Avatar && entity != player) {
				avatars.add(entity);
			}
		}
		avatars.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
		for (Entity avatar : avatars) {
			entries.add(entityEntry(player, avatar));
		}

		for (PlayerInfo info : player.connection.getOnlinePlayers()) {
			if (info.getProfile().id().equals(player.getUUID()) || level.getEntity(info.getProfile().id()) != null) {
				continue;
			}
			Component name = Component.literal(info.getProfile().name());
			String command = TeleportFeedback.COMMAND_PREFIX + " " + info.getProfile().id();
			try {
				Target target = DestinationResolver.unloaded(player, info.getProfile().id(), name);
				Component hover = switch (target) {
					case Target.Position position -> locationHover(player, name, position.destination(), Component.translatable("teleport.menu.locator_bar"));
					case Target.Spectator spectator -> lines(name.copy().withStyle(ChatFormatting.YELLOW),
							Component.translatable("teleport.menu.spectator").withStyle(ChatFormatting.GRAY),
							Component.translatable("teleport.menu.click"));
				};
				entries.add(button(name, command, hover, ChatFormatting.GREEN));
			} catch (CommandSyntaxException e) {
				// Not reachable from the client: only the locator bar's rough direction or nothing is known.
			}
		}
		return entries;
	}

	private static List<Component> placeEntries(final LocalPlayer player) {
		List<Component> entries = new ArrayList<>();
		Vec3 pos = player.position();
		Destination center = Destination.absolute(player.level().dimension(),
				new Vec3(Mth.floor(pos.x) + 0.5, pos.y, Mth.floor(pos.z) + 0.5), 0.0F, 0.0F, null);
		entries.add(destinationButton(player, Component.translatable("teleport.menu.center"), TeleportFeedback.commandFor(center), center));

		destination(() -> DestinationResolver.back(player, TeleportClient.history(), null)).ifPresent(back ->
				entries.add(destinationButton(player, Component.translatable("teleport.menu.back"), TeleportFeedback.COMMAND_PREFIX + " back", back)));
		TeleportClient.locations().spawn().ifPresent(spawn -> entries.add(destinationButton(player,
				Component.translatable("teleport.menu.spawn"), TeleportFeedback.COMMAND_PREFIX + " spawn", DestinationResolver.spawn(player, spawn))));
		entries.add(destinationButton(player, Component.translatable("teleport.menu.worldspawn"),
				TeleportFeedback.COMMAND_PREFIX + " worldspawn", DestinationResolver.worldSpawn(player)));
		entries.add(savedEntry(player, "home", TeleportClient.locations().home()));
		entries.add(savedEntry(player, "temp", TeleportClient.locations().temp()));
		return entries;
	}

	/**
	 * Returns the {@code home} or {@code temp} entry: a teleport button if it is set, otherwise a button that sets it.
	 */
	private static Component savedEntry(final LocalPlayer player, final String name, final Optional<SavedLocation> location) {
		Component label = Component.translatable("teleport.menu." + name);
		if (location.isPresent()) {
			return destinationButton(player, label, TeleportFeedback.COMMAND_PREFIX + " " + name, Destination.of(location.get()));
		}
		return button(Component.translatable("teleport.menu.set", label), TeleportFeedback.COMMAND_PREFIX + " set" + name,
				Component.translatable("teleport.menu.set." + name + ".hint"), ChatFormatting.GRAY);
	}

	private static Component entityEntry(final LocalPlayer player, final Entity entity) {
		Destination destination = Destination.absolute(entity.level().dimension(), entity.position(), entity.getYRot(), entity.getXRot(), null);
		Component hover = locationHover(player, entity.getDisplayName(), destination, entity.getType().getDescription());
		ChatFormatting color = entity instanceof Avatar ? ChatFormatting.GREEN : ChatFormatting.WHITE;
		return button(entity.getDisplayName(), TeleportFeedback.COMMAND_PREFIX + " " + entity.getStringUUID(), hover, color);
	}

	private static Component destinationButton(final LocalPlayer player, final Component label, final String command, final Destination destination) {
		return button(label, command, locationHover(player, label, destination, null), ChatFormatting.GREEN);
	}

	/**
	 * Returns the tooltip of an entry: its name, position, dimension, distance, an optional note, and a click hint.
	 */
	private static Component locationHover(final LocalPlayer player, final Component name, final Destination destination,
			final @Nullable Component note) {
		Vec3 pos = destination.position();
		List<Component> lines = new ArrayList<>();
		lines.add(name.copy().withStyle(ChatFormatting.YELLOW));
		lines.add(Component.translatable("chat.coordinates", TeleportFeedback.format(pos.x), TeleportFeedback.format(pos.y), TeleportFeedback.format(pos.z)));
		lines.add(TeleportFeedback.dimensionName(destination.dimension()).copy().withStyle(ChatFormatting.GRAY));
		if (destination.dimension() == player.level().dimension()) {
			lines.add(Component.translatable("teleport.menu.distance", TeleportFeedback.format(player.position().distanceTo(pos))).withStyle(ChatFormatting.GRAY));
		}
		if (note != null) {
			lines.add(note.copy().withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable("teleport.menu.click"));
		return lines(lines.toArray(Component[]::new));
	}

	private static Component lines(final Component... lines) {
		MutableComponent result = Component.empty();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				result.append("\n");
			}
			result.append(lines[i]);
		}
		return result;
	}

	private static Component button(final Component label, final String command, final Component hover, final ChatFormatting color) {
		return ComponentUtils.wrapInSquareBrackets(label).withStyle(s -> s.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	@FunctionalInterface
	private interface DestinationSupplier {
		Destination get() throws CommandSyntaxException;
	}

	private static Optional<Destination> destination(final DestinationSupplier supplier) {
		try {
			return Optional.of(supplier.get());
		} catch (CommandSyntaxException e) {
			return Optional.empty();
		}
	}
}
