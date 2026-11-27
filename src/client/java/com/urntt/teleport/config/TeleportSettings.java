package com.urntt.teleport.config;

import org.jspecify.annotations.Nullable;

/**
 * The values the mod reads. Implemented by the mod's own configuration, by the OneConfig integration and by the
 * constant {@link SettingValues} sets.
 */
public interface TeleportSettings {
	/** Whether the mod may move the player at all. Off in Disabled mode and while OneConfig's toggle is off. */
	boolean enabled();

	/** Whether right-clicking a block teleports the player, as toggled by the Click TP key. */
	boolean clickTp();

	/** How far Click TP looks for a block, in blocks. */
	int clickTpReach();

	/** Whether Click TP shows a translucent copy of the player at the destination. */
	boolean clickTpPreview();

	/**
	 * On which multiplayer servers the mod may teleport, or {@code null} if on every server: OneConfig has no
	 * multiplayer rules, because its profiles take their place.
	 */
	@Nullable MultiplayerRules multiplayerRules();
}
