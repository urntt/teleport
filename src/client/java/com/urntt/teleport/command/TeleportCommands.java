package com.urntt.teleport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.teleport.BackHistory;
import com.urntt.teleport.teleport.ClientTeleporter;
import com.urntt.teleport.teleport.Destination;
import com.urntt.teleport.teleport.DestinationResolver;
import com.urntt.teleport.teleport.SavedLocation;
import com.urntt.teleport.teleport.Target;
import com.urntt.teleport.teleport.TeleportErrors;
import com.urntt.teleport.teleport.TeleportFeedback;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code /tp @c} and {@code /teleport @c} commands. They run on the client: {@link #handles} tells the packet
 * listener which commands to keep from the server, {@link #execute} runs them, and {@link #addTo} adds their nodes to
 * the command tree the server sends, so the chat suggests and highlights them like server commands.
 */
public final class TeleportCommands {
	/** The pseudo selector that makes a vanilla teleport command a client teleport. */
	public static final String CLIENT_TARGET = "@c";

	private static final Logger LOGGER = LoggerFactory.getLogger(TeleportClient.MOD_ID);
	private static final Pattern COMMAND = Pattern.compile("^(?:tp|teleport) " + Pattern.quote(CLIENT_TARGET) + "(?:\\s|$)");
	private static final String TELEPORT = "teleport";
	private static final String TP = "tp";
	private static final String GET = "get";
	private static final CommandDispatcher<ClientSuggestionProvider> DISPATCHER = createDispatcher();

	/** Resolves the destination of one form of the command. */
	@FunctionalInterface
	private interface Resolver {
		Target resolve(CommandContext<ClientSuggestionProvider> context, LocalPlayer player) throws CommandSyntaxException;
	}

	private TeleportCommands() {
	}

	/**
	 * Returns whether {@code command} (without the leading slash) is one of this mod's commands.
	 */
	public static boolean handles(final String command) {
		return COMMAND.matcher(command).find();
	}

	/**
	 * Runs {@code command} on the client and reports errors in the chat like vanilla does.
	 */
	public static void execute(final String command) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		LocalPlayer player = minecraft.player;
		if (connection == null || player == null) {
			return;
		}
		try {
			DISPATCHER.execute(command, connection.getSuggestionsProvider());
		} catch (CommandSyntaxException e) {
			TeleportFeedback.sendError(player, e, command);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to run client command '{}'", command, e);
			TeleportFeedback.send(player, Component.translatable("command.failed").withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * Adds the {@code @c} branch under {@code teleport} and {@code tp} in {@code commands}, the command tree the server
	 * sent. Brigadier merges nodes with the same name, so the server's own {@code /tp} keeps working. Without
	 * permission for {@code /tp}, the server sends neither node, and this adds both.
	 */
	public static void addTo(final CommandDispatcher<ClientSuggestionProvider> commands) {
		RootCommandNode<ClientSuggestionProvider> root = commands.getRoot();
		LiteralCommandNode<ClientSuggestionProvider> client = clientBranch().build();

		CommandNode<ClientSuggestionProvider> teleport = root.getChild(TELEPORT);
		if (teleport == null) {
			root.addChild(LiteralArgumentBuilder.<ClientSuggestionProvider>literal(TELEPORT).build());
			teleport = root.getChild(TELEPORT);
		}
		redirectTarget(teleport).addChild(client);

		CommandNode<ClientSuggestionProvider> tp = root.getChild(TP);
		if (tp == null) {
			root.addChild(LiteralArgumentBuilder.<ClientSuggestionProvider>literal(TP).redirect(teleport).build());
		} else {
			redirectTarget(tp).addChild(client);
		}
	}

	private static CommandNode<ClientSuggestionProvider> redirectTarget(final CommandNode<ClientSuggestionProvider> node) {
		return node.getRedirect() != null ? node.getRedirect() : node;
	}

	private static CommandDispatcher<ClientSuggestionProvider> createDispatcher() {
		CommandDispatcher<ClientSuggestionProvider> dispatcher = new CommandDispatcher<>();
		LiteralCommandNode<ClientSuggestionProvider> teleport = dispatcher.register(
				LiteralArgumentBuilder.<ClientSuggestionProvider>literal(TELEPORT).then(clientBranch()));
		dispatcher.register(LiteralArgumentBuilder.<ClientSuggestionProvider>literal(TP).redirect(teleport));
		return dispatcher;
	}

	/**
	 * Builds everything after {@code /tp @c}. The vanilla forms come last, so the literals take precedence over a
	 * player with the same name.
	 */
	private static LiteralArgumentBuilder<ClientSuggestionProvider> clientBranch() {
		return literal(CLIENT_TARGET)
				.executes(c -> TeleportMenu.showMenu(player()))
				.then(literal("entities").executes(c -> TeleportMenu.showEntities(player())))
				.then(destination(literal("back"), (c, p) -> position(back(p, null)))
						.then(destination(literal(BackHistory.Kind.CLIENT.getSerializedName()), (c, p) -> position(back(p, BackHistory.Kind.CLIENT))))
						.then(destination(literal(BackHistory.Kind.SERVER.getSerializedName()), (c, p) -> position(back(p, BackHistory.Kind.SERVER))))
						.then(destination(literal(BackHistory.Kind.DEATH.getSerializedName()), (c, p) -> position(back(p, BackHistory.Kind.DEATH)))))
				.then(destination(literal("spawn"), (c, p) -> position(DestinationResolver.spawn(p,
						TeleportClient.locations().spawn().orElseThrow(TeleportErrors.NO_SPAWN::create)))))
				.then(destination(literal("worldspawn"), (c, p) -> position(DestinationResolver.worldSpawn(p))))
				.then(destination(literal("home"), (c, p) -> position(Destination.of(
						TeleportClient.locations().home().orElseThrow(TeleportErrors.NO_HOME::create)))))
				.then(destination(literal("temp"), (c, p) -> position(Destination.of(
						TeleportClient.locations().temp().orElseThrow(TeleportErrors.NO_TEMP::create)))))
				.then(literal("sethome").executes(c -> setHome(player())))
				.then(literal("settemp").executes(c -> setTemp(player())))
				.then(destination(argument("location", Vec3Argument.vec3()), (c, p) -> coordinates(c, p, null, null))
						.then(destination(argument("rotation", RotationArgument.rotation()),
								(c, p) -> coordinates(c, p, c.getArgument("rotation", Coordinates.class), null)))
						.then(literal("facing")
								.then(literal("entity")
										.then(destination(argument("facingEntity", ClientEntityArgument.entity()),
												(c, p) -> facingEntity(c, p, EntityAnchorArgument.Anchor.FEET))
												.then(destination(argument("facingAnchor", EntityAnchorArgument.anchor()),
														(c, p) -> facingEntity(c, p, c.getArgument("facingAnchor", EntityAnchorArgument.Anchor.class))))))
								.then(destination(argument("facingLocation", Vec3Argument.vec3()),
										(c, p) -> coordinates(c, p, null, new DestinationResolver.Facing.AtPosition(
												c.getArgument("facingLocation", Coordinates.class)))))))
				.then(destination(argument("destination", ClientEntityArgument.entity()),
						(c, p) -> DestinationResolver.entity(p, ClientEntityArgument.getSelector(c, "destination"))));
	}

	/**
	 * Makes {@code builder} teleport to the destination {@code resolver} finds, and adds a {@code get} child that only
	 * prints it. Teleporting first checks the multiplayer rules and whether the mod is on; {@code get} works everywhere.
	 */
	private static <T extends ArgumentBuilder<ClientSuggestionProvider, T>> T destination(final T builder, final Resolver resolver) {
		return builder
				.executes(c -> {
					LocalPlayer player = player();
					switch (TeleportClient.controller().availability()) {
						case BLOCKED -> throw TeleportErrors.BLOCKED.create();
						case TURNED_OFF -> throw TeleportErrors.TURNED_OFF.create();
						case AVAILABLE -> {
						}
					}
					return teleport(player, resolver.resolve(c, player));
				})
				.then(literal(GET).executes(c -> {
					LocalPlayer player = player();
					return get(player, resolver.resolve(c, player));
				}));
	}

	private static int teleport(final LocalPlayer player, final Target target) throws CommandSyntaxException {
		switch (target) {
			case Target.Position position -> ClientTeleporter.teleport(player, TeleportClient.history(), position.destination());
			case Target.Spectator spectator -> ClientTeleporter.spectatorTeleport(player, spectator);
		}
		return 1;
	}

	private static int get(final LocalPlayer player, final Target target) {
		Component message = switch (target) {
			case Target.Position position -> TeleportFeedback.destination(position.destination());
			case Target.Spectator spectator -> Component.translatable("teleport.command.get.spectator", spectator.name());
		};
		TeleportFeedback.send(player, message);
		return 1;
	}

	private static Target position(final Destination destination) {
		return new Target.Position(destination);
	}

	private static Destination back(final LocalPlayer player, final BackHistory.@Nullable Kind kind) throws CommandSyntaxException {
		return DestinationResolver.back(player, TeleportClient.history(), kind);
	}

	private static Target coordinates(final CommandContext<ClientSuggestionProvider> context, final LocalPlayer player,
			final @Nullable Coordinates rotation, final DestinationResolver.@Nullable Facing facing) throws CommandSyntaxException {
		return position(DestinationResolver.coordinates(player, context.getArgument("location", Coordinates.class), rotation, facing));
	}

	private static Target facingEntity(final CommandContext<ClientSuggestionProvider> context, final LocalPlayer player,
			final EntityAnchorArgument.Anchor anchor) throws CommandSyntaxException {
		return coordinates(context, player, null,
				new DestinationResolver.Facing.AtEntity(ClientEntityArgument.getSelector(context, "facingEntity"), anchor));
	}

	private static int setHome(final LocalPlayer player) throws CommandSyntaxException {
		SavedLocation location = SavedLocation.of(player);
		requireWorld();
		TeleportClient.locations().setHome(location);
		TeleportFeedback.send(player, Component.translatable("teleport.command.sethome", TeleportFeedback.coordinates(Destination.of(location))));
		return 1;
	}

	private static int setTemp(final LocalPlayer player) throws CommandSyntaxException {
		SavedLocation location = SavedLocation.of(player);
		requireWorld();
		TeleportClient.locations().setTemp(location);
		TeleportFeedback.send(player, Component.translatable("teleport.command.settemp", TeleportFeedback.coordinates(Destination.of(location))));
		return 1;
	}

	private static void requireWorld() throws CommandSyntaxException {
		if (TeleportClient.locations().selected() == null) {
			throw TeleportErrors.NOT_IN_WORLD.create();
		}
	}

	private static LocalPlayer player() throws CommandSyntaxException {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw TeleportErrors.NOT_IN_WORLD.create();
		}
		return player;
	}

	private static LiteralArgumentBuilder<ClientSuggestionProvider> literal(final String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static <T> RequiredArgumentBuilder<ClientSuggestionProvider, T> argument(final String name,
			final ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}
}
