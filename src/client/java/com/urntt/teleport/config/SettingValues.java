package com.urntt.teleport.config;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An immutable set of settings.
 */
public record SettingValues(
		boolean enabled,
		boolean clickTp,
		int clickTpReach,
		boolean clickTpPreview,
		@Nullable MultiplayerRules multiplayerRules) implements TeleportSettings {
	/** The defaults of the mod's own settings: the mod may teleport in singleplayer, but on no server. */
	public static final SettingValues DEFAULTS = new SettingValues(
			true, false, TeleportConfig.DEFAULT_CLICK_TP_REACH, true, new MultiplayerRules(MultiplayerMode.DISABLED, List.of()));

	/**
	 * The defaults of the OneConfig settings: the mod is turned off until the player turns it on in OneConfig, and then
	 * applies everywhere.
	 */
	public static final SettingValues ONECONFIG_DEFAULTS = new SettingValues(
			false, DEFAULTS.clickTp(), DEFAULTS.clickTpReach(), DEFAULTS.clickTpPreview(), null);

	/** The settings of Disabled mode, where the mod is turned off everywhere. */
	public static final SettingValues DISABLED = new SettingValues(
			false, DEFAULTS.clickTp(), DEFAULTS.clickTpReach(), DEFAULTS.clickTpPreview(), DEFAULTS.multiplayerRules());

	/** Copies the current values of {@code settings}. */
	public static SettingValues copyOf(final TeleportSettings settings) {
		return new SettingValues(settings.enabled(), settings.clickTp(), settings.clickTpReach(), settings.clickTpPreview(),
				settings.multiplayerRules());
	}
}
