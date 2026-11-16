package com.urntt.teleport.config;

import com.urntt.teleport.TeleportClient;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Configuration screen built from vanilla widgets. Every change is saved immediately.
 */
public final class TeleportConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.teleport.title");
	private static final Component REACH = Component.translatable("options.teleport.click_tp_reach");
	private static final Component REACH_TOOLTIP = Component.translatable("options.teleport.click_tp_reach.tooltip",
			TeleportConfig.MIN_CLICK_TP_REACH, TeleportConfig.MAX_CLICK_TP_REACH);
	private static final int INVALID_TEXT_COLOR = 0xFFFF0000;
	/** Width of each half of a two-widget row, as used by vanilla's option lists. */
	private static final int HALF_WIDTH = 150;
	private static final int ROW_HEIGHT = 20;

	public TeleportConfigScreen(final @Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		TeleportConfig config = TeleportClient.config();

		this.list.addHeader(Component.translatable("options.teleport.section.click_tp"));
		this.list.addBig(toggle(TeleportClient.CLICK_TP_NAME_KEY, config.isClickTpEnabled(), config::setClickTpEnabled));
		StringWidget reachLabel = new StringWidget(HALF_WIDTH, ROW_HEIGHT, REACH, this.font);
		reachLabel.setTooltip(Tooltip.create(REACH_TOOLTIP));
		this.list.addSmall(reachLabel, this.reachBox(config));
		this.list.addBig(toggle("options.teleport.click_tp_preview", config.clickTpPreview(), config::setClickTpPreview));

		this.list.addHeader(Component.translatable("options.teleport.section.defaults"));
		this.list.addBig(toggle("options.teleport.singleplayer_default", config.singleplayerDefault(), config::setSingleplayerDefault));
		this.list.addBig(toggle("options.teleport.multiplayer_default", config.multiplayerDefault(), config::setMultiplayerDefault));

		this.list.addHeader(Component.translatable("options.teleport.section.reset"));
		this.list.addBig(toggle("options.teleport.reset_on_world_exit", config.resetOnWorldExit(), config::setResetOnWorldExit));
		this.list.addBig(toggle("options.teleport.reset_on_game_exit", config.resetOnGameExit(), config::setResetOnGameExit));

		this.list.addHeader(Component.translatable("options.teleport.section.multiplayer"));
		this.list.addBig(new OptionInstance<>(
				"options.teleport.multiplayer_mode",
				mode -> Tooltip.create(Component.translatable("options.teleport.multiplayer_mode.tooltip", mode.description())),
				// The button itself prepends the caption, so this only names the value.
				(caption, mode) -> mode.label(),
				new OptionInstance.Enum<>(List.of(MultiplayerMode.values()), MultiplayerMode.CODEC),
				config.multiplayerMode(),
				config::setMultiplayerMode));
		this.list.addBig(Button.builder(Component.translatable("options.teleport.edit_servers"),
						button -> this.minecraft.gui.setScreen(new ServerListScreen(this, config)))
				.tooltip(Tooltip.create(Component.translatable("options.teleport.edit_servers.tooltip")))
				.build());
	}

	/**
	 * Creates the text field for the Click TP reach. Valid values are saved as they are typed; invalid ones turn red
	 * and are not saved.
	 */
	private EditBox reachBox(final TeleportConfig config) {
		EditBox box = new EditBox(this.font, HALF_WIDTH, ROW_HEIGHT, REACH);
		box.setMaxLength(Integer.toString(TeleportConfig.MAX_CLICK_TP_REACH).length());
		box.setValue(Integer.toString(config.clickTpReach()));
		box.setTooltip(Tooltip.create(REACH_TOOLTIP));
		box.setResponder(text -> {
			Integer reach = parseReach(text);
			box.setTextColor(reach != null ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);
			if (reach != null) {
				config.setClickTpReach(reach);
			}
		});
		return box;
	}

	/**
	 * Returns the reach typed into the text field, or {@code null} if it is not a whole number in the allowed range.
	 */
	static @Nullable Integer parseReach(final String text) {
		try {
			int reach = Integer.parseInt(text.trim());
			return reach >= TeleportConfig.MIN_CLICK_TP_REACH && reach <= TeleportConfig.MAX_CLICK_TP_REACH ? reach : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Creates an on/off option whose tooltip is the translation of {@code captionKey + ".tooltip"}.
	 */
	private static OptionInstance<Boolean> toggle(final String captionKey, final boolean value, final Consumer<Boolean> onChange) {
		return OptionInstance.createBoolean(
				captionKey,
				OptionInstance.cachedConstantTooltip(Component.translatable(captionKey + ".tooltip")),
				value,
				onChange::accept);
	}
}
