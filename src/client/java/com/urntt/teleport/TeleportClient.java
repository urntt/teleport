package com.urntt.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.config.TeleportConfigScreen;
import com.urntt.teleport.teleport.BackHistory;
import com.urntt.teleport.teleport.LocationTracker;
import com.urntt.teleport.teleport.SavedLocations;
import com.urntt.teleport.teleport.TeleportErrors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public final class TeleportClient implements ClientModInitializer {
	public static final String MOD_ID = "teleport";
	public static final String TOGGLE_CLICK_TP_KEY_NAME = "key.teleport.toggle_click_tp";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.teleport.open_settings";

	/** Translation key of Click TP's name, shared by the toggle message and the configuration screen. */
	public static final String CLICK_TP_NAME_KEY = "options.teleport.click_tp";
	public static final Component CLICK_TP_NAME = Component.translatable(CLICK_TP_NAME_KEY);

	private static TeleportConfig config;
	private static TeleportController controller;
	private static BackHistory history;
	private static SavedLocations locations;
	private static LocationTracker tracker;

	@Override
	public void onInitializeClient() {
		config = TeleportConfig.load(TeleportConfig.defaultPath());
		controller = new TeleportController(config);
		history = new BackHistory();
		locations = SavedLocations.load(SavedLocations.defaultPath());
		tracker = new LocationTracker(history, locations);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(TOGGLE_CLICK_TP_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tracker.tick();
			while (toggleKey.consumeClick()) {
				toggleClickTp(client);
			}
			while (openSettingsKey.consumeClick()) {
				client.gui.setScreen(new TeleportConfigScreen(client.gui.screen()));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			controller.onJoin(Scene.of(client));
			tracker.onJoin(client);
		});
		// The disconnect event may arrive on the network thread; the state is only used on the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(() -> {
			controller.onDisconnect();
			tracker.onDisconnect();
		}));

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				tracker.onBlockUsed(level, hitResult.getBlockPos(), level.getBlockState(hitResult.getBlockPos()), player.getYRot());
			}
			return InteractionResult.PASS;
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> tracker.onGameMessage(message));
	}

	public static TeleportConfig config() {
		return config;
	}

	public static TeleportController controller() {
		return controller;
	}

	public static BackHistory history() {
		return history;
	}

	public static SavedLocations locations() {
		return locations;
	}

	public static LocationTracker tracker() {
		return tracker;
	}

	private static void toggleClickTp(final Minecraft client) {
		TeleportController.ToggleResult result = controller.toggleClickTp();
		if (client.player == null) {
			return;
		}

		Component message = switch (result) {
			case ENABLED -> CommonComponents.optionStatus(CLICK_TP_NAME, true);
			case DISABLED -> CommonComponents.optionStatus(CLICK_TP_NAME, false);
			case BLOCKED -> TeleportErrors.BLOCKED_MESSAGE;
		};
		client.player.sendOverlayMessage(message);
	}
}
