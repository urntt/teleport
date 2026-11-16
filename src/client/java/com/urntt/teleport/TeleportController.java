package com.urntt.teleport;

import com.urntt.teleport.config.TeleportConfig;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the mod may move the player right now and whether Click TP is active, from the configuration and
 * the current {@link Scene}. It is the only place that combines the mod's state, the defaults, the reset rules, the
 * multiplayer rules, and the Click TP state.
 */
public final class TeleportController {
	/** Whether the mod may move the player in the current scene. */
	public enum Availability {
		AVAILABLE,
		/** The multiplayer rules rule out the current scene. */
		BLOCKED,
		/** The player turned the mod off. */
		TURNED_OFF
	}

	/** Outcome of {@link #toggle()} and {@link #toggleClickTp()}. */
	public enum ToggleResult {
		ENABLED,
		DISABLED,
		/** The multiplayer rules rule out the current scene, so the state was left unchanged. */
		BLOCKED,
		/** The mod is turned off, so the Click TP state was left unchanged. */
		TURNED_OFF
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
	 * Called when the player joins a world. On an allowed scene, restores the scene's default state of the mod if a
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
			this.config.setEnabled(this.defaultFor(scene));
		}
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether commands and Click TP may move the player now, and if not, why. The multiplayer rules come
	 * first, because they apply whatever the mod's state is.
	 */
	public Availability availability() {
		if (!this.isAllowed()) {
			return Availability.BLOCKED;
		}
		return this.config.isEnabled() ? Availability.AVAILABLE : Availability.TURNED_OFF;
	}

	/**
	 * Returns whether right-clicking a block teleports the player now.
	 */
	public boolean isClickTpActive() {
		return this.availability() == Availability.AVAILABLE && this.config.isClickTpEnabled();
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
	 * Flips and saves the state of the whole mod, unless the current server is not allowed.
	 */
	public ToggleResult toggle() {
		if (this.scene instanceof Scene.Multiplayer && !this.isAllowed()) {
			return ToggleResult.BLOCKED;
		}
		boolean enabled = !this.config.isEnabled();
		this.config.setEnabled(enabled);
		return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
	}

	/**
	 * Flips and saves the Click TP state, unless the mod cannot teleport in the current scene.
	 */
	public ToggleResult toggleClickTp() {
		switch (this.availability()) {
			case BLOCKED -> {
				return ToggleResult.BLOCKED;
			}
			case TURNED_OFF -> {
				return ToggleResult.TURNED_OFF;
			}
			case AVAILABLE -> {
			}
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
