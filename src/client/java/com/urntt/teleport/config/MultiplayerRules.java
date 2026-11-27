package com.urntt.teleport.config;

import java.util.List;

/**
 * On which multiplayer servers the mod may teleport the player. Only the mod's own settings have them; OneConfig mode
 * has none, because OneConfig's profiles take their place.
 *
 * @param mode    whether the server list is a whitelist or a blacklist, or whether every server is ruled out
 * @param servers the server list used by the whitelist and blacklist modes
 */
public record MultiplayerRules(MultiplayerMode mode, List<String> servers) {
	public MultiplayerRules {
		servers = List.copyOf(servers);
	}
}
