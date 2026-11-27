package com.urntt.teleport.config;

import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.compat.OneConfigBridge;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The configuration screen, built from vanilla widgets. The mode button comes first. The settings below it can only
 * be edited in Custom mode; otherwise they are greyed out and show the values that currently apply. OneConfig has no
 * multiplayer rules, so OneConfig mode has no multiplayer section. Every change is saved immediately.
 */
public final class TeleportConfigScreen extends OptionsSubScreen {
	private static final Component REACH = Component.translatable(SettingKeys.CLICK_TP_REACH);
	private static final Component REACH_DESCRIPTION = Component.empty()
			.append(Component.translatable(SettingKeys.CLICK_TP_REACH + SettingKeys.DESCRIPTION))
			.append(CommonComponents.SPACE)
			.append(Component.translatable(SettingKeys.CLICK_TP_REACH_RANGE, TeleportConfig.MIN_CLICK_TP_REACH, TeleportConfig.MAX_CLICK_TP_REACH));
	private static final int INVALID_TEXT_COLOR = 0xFFFF0000;
	/** Width of each half of a two-widget row, as used by vanilla's option lists. */
	private static final int HALF_WIDTH = 150;
	private static final int ROW_HEIGHT = 20;

	private final TeleportConfig config = TeleportClient.config();

	public TeleportConfigScreen(final @Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(SettingKeys.TITLE));
	}

	/** Shows the screen on top of whatever screen is open. */
	public static void open(final Minecraft minecraft) {
		minecraft.gui.setScreen(new TeleportConfigScreen(minecraft.gui.screen()));
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		ConfigMode mode = this.config.effectiveMode();
		boolean editable = mode == ConfigMode.CUSTOM;
		TeleportSettings shown = this.config.effective();

		this.list.addBig(this.createModeButton(mode));
		if (mode == ConfigMode.ONECONFIG) {
			this.list.addBig(Button.builder(Component.translatable(SettingKeys.OPEN_ONECONFIG), button -> OneConfigBridge.openOneConfig()).build());
		}

		this.list.addHeader(Component.translatable(SettingKeys.SECTION_CLICK_TP));
		this.list.addBig(toggle(SettingKeys.CLICK_TP, shown.clickTp(), editable, this.config::setClickTp));
		StringWidget reachLabel = new StringWidget(HALF_WIDTH, ROW_HEIGHT, REACH, this.font);
		reachLabel.setTooltip(Tooltip.create(REACH_DESCRIPTION));
		this.list.addSmall(reachLabel, this.reachBox(shown.clickTpReach(), editable));
		this.list.addBig(toggle(SettingKeys.CLICK_TP_PREVIEW, shown.clickTpPreview(), editable, this.config::setClickTpPreview));

		MultiplayerRules rules = shown.multiplayerRules();
		if (rules == null) {
			// OneConfig has no multiplayer rules; its profiles take their place.
			return;
		}
		this.list.addHeader(Component.translatable(SettingKeys.SECTION_MULTIPLAYER));
		CycleButton<MultiplayerMode> multiplayerMode = CycleButton.builder(MultiplayerMode::label, rules.mode())
				.withValues(List.of(MultiplayerMode.values()))
				.withTooltip(value -> Tooltip.create(Component.empty()
						.append(value.description())
						.append("\n\n")
						.append(Component.translatable(SettingKeys.MULTIPLAYER_MODE + SettingKeys.DESCRIPTION))))
				.create(Component.translatable(SettingKeys.MULTIPLAYER_MODE), (button, value) -> this.config.setMultiplayerMode(value));
		multiplayerMode.active = editable;
		this.list.addBig(multiplayerMode);
		Button servers = Button.builder(Component.translatable(SettingKeys.EDIT_SERVERS),
						button -> this.minecraft.gui.setScreen(new ServerListScreen(this, this.config)))
				.tooltip(Tooltip.create(Component.translatable(SettingKeys.EDIT_SERVERS + SettingKeys.DESCRIPTION)))
				.build();
		servers.active = editable;
		this.list.addBig(servers);
	}

	/**
	 * Creates the mode button. OneConfig mode is only offered while OneConfig is installed. Changing the mode changes
	 * the values shown below and whether they can be edited, so the screen starts over.
	 */
	private CycleButton<ConfigMode> createModeButton(final ConfigMode mode) {
		List<ConfigMode> modes = OneConfigBridge.isAvailable()
				? List.of(ConfigMode.values())
				: List.of(ConfigMode.DISABLED, ConfigMode.CUSTOM);
		boolean oneConfigMissing = this.config.storedMode() == ConfigMode.ONECONFIG && !OneConfigBridge.isAvailable();
		return CycleButton.builder(ConfigMode::label, mode)
				.withValues(modes)
				.withTooltip(value -> Tooltip.create(oneConfigMissing && value == mode
						? Component.translatable(SettingKeys.ONECONFIG_MISSING)
						: value.description()))
				.create(Component.translatable(SettingKeys.MODE), (button, value) -> {
					this.config.setMode(value);
					this.minecraft.gui.setScreen(new TeleportConfigScreen(this.lastScreen));
				});
	}

	/**
	 * Creates the text field for the Click TP reach. Valid values are saved as they are typed; invalid ones turn red
	 * and are not saved.
	 */
	private EditBox reachBox(final int reach, final boolean editable) {
		EditBox box = new EditBox(this.font, HALF_WIDTH, ROW_HEIGHT, REACH);
		box.setMaxLength(Integer.toString(TeleportConfig.MAX_CLICK_TP_REACH).length());
		box.setValue(Integer.toString(reach));
		box.setTooltip(Tooltip.create(REACH_DESCRIPTION));
		box.setResponder(text -> {
			Integer parsed = parseReach(text);
			box.setTextColor(parsed != null ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);
			if (parsed != null) {
				this.config.setClickTpReach(parsed);
			}
		});
		box.active = editable;
		box.setEditable(editable);
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
	 * Creates an on/off button whose tooltip is the translation of {@code key + ".description"}.
	 */
	private static CycleButton<Boolean> toggle(final String key, final boolean value, final boolean editable, final Consumer<Boolean> setter) {
		Tooltip tooltip = Tooltip.create(Component.translatable(key + SettingKeys.DESCRIPTION));
		CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
				.withTooltip(ignored -> tooltip)
				.create(Component.translatable(key), (cycleButton, newValue) -> setter.accept(newValue));
		button.active = editable;
		return button;
	}
}
