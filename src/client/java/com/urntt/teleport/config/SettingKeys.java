package com.urntt.teleport.config;

/**
 * Translation keys of the settings, shared by the configuration screen and the OneConfig integration. They are
 * compile-time constants so that OneConfig's option annotations can use them.
 */
public final class SettingKeys {
	private static final String PREFIX = "options.teleport.";

	public static final String TITLE = PREFIX + "title";
	public static final String MODE = PREFIX + "mode";
	public static final String ONECONFIG_MISSING = PREFIX + "mode.oneconfig.missing";
	public static final String OPEN_ONECONFIG = PREFIX + "open_oneconfig";

	public static final String SECTION_CLICK_TP = PREFIX + "section.click_tp";
	public static final String CLICK_TP = PREFIX + "click_tp";
	public static final String CLICK_TP_REACH = PREFIX + "click_tp_reach";
	/** The allowed range of the Click TP reach, added to its description on the mod's own screen. */
	public static final String CLICK_TP_REACH_RANGE = CLICK_TP_REACH + ".range";
	public static final String CLICK_TP_PREVIEW = PREFIX + "click_tp_preview";

	public static final String SECTION_MULTIPLAYER = PREFIX + "section.multiplayer";
	/** Prefix of the multiplayer mode keys; the rest of each value's key is its id in the config file. */
	public static final String MULTIPLAYER_MODE = PREFIX + "multiplayer_mode";
	public static final String SERVERS = PREFIX + "servers";
	public static final String EDIT_SERVERS = PREFIX + "edit_servers";

	/** Suffix of the key that describes a setting, shown as its tooltip. */
	public static final String DESCRIPTION = ".description";

	private SettingKeys() {
	}
}
