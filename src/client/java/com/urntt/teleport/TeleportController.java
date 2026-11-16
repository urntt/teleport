package com.urntt.teleport;

import com.urntt.teleport.config.TeleportConfig;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the mod may move the player right now and whether Click TP is active, from the configuration and
 * the current {@link Scene}. It is the only place that combines the Click TP state, the defaults, the reset rules,
 * and the multiplayer rules.
 */
public final class TeleportController {
	/** Outcome of {@link #toggleClickTp()}. */
	public enum ToggleResult {
		ENABLED,
		DISABLED,
		/** The current server is not allowed, so the Click TP state was left unchanged. */
		BLOCKED
	}

	private final TeleportConfig config;
	private Scene scene = Scene.NONE;
	private boolean allowedWorldJoinedSinceStart = false;

	public TeleportController(final TeleportConfig config) {
		this.config = config;
	}

	public Scene scene() {
		return this.scene;
	}

	/**
	 * Called when the player joins a world. On an allowed scene, restores the scene's default Click TP state if a
	 * reset rule applies: always with "reset on world exit", and for the first allowed world since the game started
	 * with "reset on game exit". Applying the reset on join instead of on exit lets it pick the next scene's default
	 * and also works after a crash.
	 */
	public void onJoin(final Scene scene) {
		this.scene = scene;
		if (!this.isAllowed()) {
			return;
		}

		boolean firstWorld = !this.allowedWorldJoinedSinceStart;
		this.allowedWorldJoinedSinceStart = true;
		if (this.config.resetOnWorldExit() || (this.config.resetOnGameExit() && firstWorld)) {
			this.config.setClickTpEnabled(this.defaultFor(scene));
		}
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether right-clicking a block teleports the player now.
	 */
	public boolean isClickTpActive() {
		return this.config.isClickTpEnabled() && this.isAllowed();
	}

	/**
	 * Returns whether the multiplayer rules allow moving the player in the current scene.
	 */
	public boolean isAllowed() {
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> switch (this.config.multiplayerMode()) {
				case DISABLED -> false;
				case WHITELIST -> this.isListed(multiplayer.address());
				case BLACKLIST -> !this.isListed(multiplayer.address());
			};
		};
	}

	/**
	 * Flips and saves the Click TP state, unless the current server is not allowed.
	 */
	public ToggleResult toggleClickTp() {
		if (this.scene instanceof Scene.Multiplayer && !this.isAllowed()) {
			return ToggleResult.BLOCKED;
		}
		boolean enabled = !this.config.isClickTpEnabled();
		this.config.setClickTpEnabled(enabled);
		return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
	}

	private boolean defaultFor(final Scene scene) {
		return scene instanceof Scene.Singleplayer ? this.config.singleplayerDefault() : this.config.multiplayerDefault();
	}

	private boolean isListed(final @Nullable String address) {
		return address != null && this.config.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
