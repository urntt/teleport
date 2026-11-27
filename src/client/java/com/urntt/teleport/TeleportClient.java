package com.urntt.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.teleport.config.SettingKeys;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.config.TeleportConfigScreen;
import com.urntt.teleport.config.TeleportSettings;
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

	/** Name of Click TP, shown with its new state when the Click TP key switches it. */
	public static final Component CLICK_TP_NAME = Component.translatable(SettingKeys.CLICK_TP);

	private static TeleportConfig config;
	private static TeleportController controller;
	private static BackHistory history;
	private static SavedLocations locations;
	private static LocationTracker tracker;

	@Override
	public void onInitializeClient() {
		config = TeleportConfig.load(TeleportConfig.defaultPath());
		controller = new TeleportController(config::effective, config::setEffectiveClickTp);
		history = new BackHistory();
		locations = SavedLocations.load(SavedLocations.defaultPath());
		tracker = new LocationTracker(history, locations);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		KeyMapping toggleClickTpKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(TOGGLE_CLICK_TP_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tracker.tick();
			while (toggleClickTpKey.consumeClick()) {
				showToggleResult(client, controller.toggleClickTp());
			}
			while (openSettingsKey.consumeClick()) {
				TeleportConfigScreen.open(client);
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

	/** The settings that currently apply, according to the configuration mode. */
	public static TeleportSettings settings() {
		return config.effective();
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

	/**
	 * Shows the outcome of the Click TP key on the action bar: the new state, or why it did not change.
	 */
	private static void showToggleResult(final Minecraft client, final TeleportController.ToggleResult result) {
		if (client.player == null) {
			return;
		}

		Component message = switch (result) {
			case ENABLED, DISABLED -> CommonComponents.optionStatus(CLICK_TP_NAME, result == TeleportController.ToggleResult.ENABLED);
			case TURNED_OFF -> TeleportErrors.TURNED_OFF_MESSAGE;
			case BLOCKED -> TeleportErrors.BLOCKED_MESSAGE;
		};
		client.player.sendOverlayMessage(message);
	}
}
