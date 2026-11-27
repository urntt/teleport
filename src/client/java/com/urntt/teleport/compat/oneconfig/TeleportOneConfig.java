package com.urntt.teleport.compat.oneconfig;

import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.config.MultiplayerRules;
import com.urntt.teleport.config.SettingKeys;
import com.urntt.teleport.config.SettingValues;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.config.TeleportSettings;
import org.jspecify.annotations.Nullable;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.MasterToggle;
import org.polyfrost.oneconfig.api.config.v1.annotations.Number;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

/**
 * The settings OneConfig manages in OneConfig mode: the mod's toggle on its card, off by default, and the Click TP
 * settings, with the same defaults as the mod's own. There are no multiplayer rules; OneConfig's profiles take their
 * place, so once the toggle is on, the mod teleports in singleplayer and on every server. Titles use the mod's
 * translation keys; the keys double as fallback titles.
 */
public final class TeleportOneConfig extends Config implements TeleportSettings {
	/**
	 * OneConfig keeps its files in the same config directory as the mod's own {@code teleport.json} and watches them
	 * for changes, so its file needs a name of its own.
	 */
	private static final String FILE_NAME = TeleportClient.MOD_ID + "-oneconfig.json";

	/** The mod icon, as named in {@code fabric.mod.json}, as a resource path for OneConfig's mod card. */
	private static final String ICON_PATH = "/assets/" + TeleportClient.MOD_ID + "/icon.png";

	/** The toggle on the mod's OneConfig card. While it is off, the mod is turned off. */
	@MasterToggle
	public boolean enabled = SettingValues.ONECONFIG_DEFAULTS.enabled();

	@Switch(
			title = SettingKeys.CLICK_TP,
			titleTranslation = true,
			description = SettingKeys.CLICK_TP + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.SECTION_CLICK_TP,
			subcategoryTranslation = true)
	public boolean clickTp = SettingValues.ONECONFIG_DEFAULTS.clickTp();

	@Number(
			title = SettingKeys.CLICK_TP_REACH,
			titleTranslation = true,
			description = SettingKeys.CLICK_TP_REACH + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.SECTION_CLICK_TP,
			subcategoryTranslation = true,
			min = TeleportConfig.MIN_CLICK_TP_REACH,
			max = TeleportConfig.MAX_CLICK_TP_REACH)
	public int clickTpReach = SettingValues.ONECONFIG_DEFAULTS.clickTpReach();

	@Switch(
			title = SettingKeys.CLICK_TP_PREVIEW,
			titleTranslation = true,
			description = SettingKeys.CLICK_TP_PREVIEW + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.SECTION_CLICK_TP,
			subcategoryTranslation = true)
	public boolean clickTpPreview = SettingValues.ONECONFIG_DEFAULTS.clickTpPreview();

	public TeleportOneConfig() {
		super(FILE_NAME, ICON_PATH, TeleportClient.MOD_ID, Category.UTILITY);
	}

	/** Changes Click TP and saves it, as the Click TP key does in OneConfig mode. */
	void setClickTpAndSave(final boolean clickTp) {
		this.clickTp = clickTp;
		this.save();
	}

	@Override
	public boolean enabled() {
		return this.enabled;
	}

	@Override
	public boolean clickTp() {
		return this.clickTp;
	}

	@Override
	public int clickTpReach() {
		return TeleportConfig.clampReach(this.clickTpReach);
	}

	@Override
	public boolean clickTpPreview() {
		return this.clickTpPreview;
	}

	/** OneConfig has no multiplayer rules; its profiles take their place. */
	@Override
	public @Nullable MultiplayerRules multiplayerRules() {
		return SettingValues.ONECONFIG_DEFAULTS.multiplayerRules();
	}
}
