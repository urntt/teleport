package com.urntt.teleport;

import com.urntt.teleport.config.MultiplayerRules;
import com.urntt.teleport.config.TeleportSettings;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the mod may move the player right now and whether Click TP is active, from the settings that apply
 * in the configuration mode and the current {@link Scene}. It is the only place that combines the configuration mode,
 * the multiplayer rules and the Click TP state.
 */
public final class TeleportController {
	/** Whether the mod may move the player in the current scene. */
	public enum Availability {
		AVAILABLE,
		/** The mod is turned off: Disabled mode, or OneConfig's toggle in OneConfig mode. */
		TURNED_OFF,
		/** The multiplayer rules rule out the current scene. */
		BLOCKED
	}

	/** Outcome of {@link #toggleClickTp()}. */
	public enum ToggleResult {
		ENABLED,
		DISABLED,
		/** The mod is turned off, so Click TP was left unchanged. */
		TURNED_OFF,
		/** The multiplayer rules rule out the current scene, so Click TP was left unchanged. */
		BLOCKED
	}

	private final Supplier<TeleportSettings> settings;
	private final Consumer<Boolean> clickTpSetter;
	private Scene scene = Scene.NONE;

	/**
	 * @param settings      the settings that apply right now, which change with the configuration mode
	 * @param clickTpSetter changes and saves Click TP where those settings come from
	 */
	public TeleportController(final Supplier<TeleportSettings> settings, final Consumer<Boolean> clickTpSetter) {
		this.settings = settings;
		this.clickTpSetter = clickTpSetter;
	}

	public Scene scene() {
		return this.scene;
	}

	public void onJoin(final Scene scene) {
		this.scene = scene;
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether commands and Click TP may move the player now, and if not, why. Being turned off comes first,
	 * because it applies everywhere.
	 */
	public Availability availability() {
		TeleportSettings settings = this.settings.get();
		if (!settings.enabled()) {
			return Availability.TURNED_OFF;
		}
		return this.isAllowed(settings) ? Availability.AVAILABLE : Availability.BLOCKED;
	}

	/**
	 * Returns whether right-clicking a block teleports the player now.
	 */
	public boolean isClickTpActive() {
		return this.availability() == Availability.AVAILABLE && this.settings.get().clickTp();
	}

	/**
	 * Returns whether the multiplayer rules that apply allow moving the player in the current scene.
	 */
	public boolean isAllowed() {
		return this.isAllowed(this.settings.get());
	}

	/**
	 * Flips and saves Click TP where the settings that apply come from, unless the mod cannot teleport now.
	 */
	public ToggleResult toggleClickTp() {
		switch (this.availability()) {
			case TURNED_OFF -> {
				return ToggleResult.TURNED_OFF;
			}
			case BLOCKED -> {
				return ToggleResult.BLOCKED;
			}
			case AVAILABLE -> {
			}
		}
		boolean enabled = !this.settings.get().clickTp();
		this.clickTpSetter.accept(enabled);
		return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
	}

	/**
	 * Settings without multiplayer rules, which is OneConfig's, allow every server.
	 */
	private boolean isAllowed(final TeleportSettings settings) {
		MultiplayerRules rules = settings.multiplayerRules();
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> rules == null || switch (rules.mode()) {
				case DISABLED -> false;
				case WHITELIST -> isListed(rules, multiplayer.address());
				case BLACKLIST -> !isListed(rules, multiplayer.address());
			};
		};
	}

	private static boolean isListed(final MultiplayerRules rules, final @Nullable String address) {
		return address != null && rules.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
