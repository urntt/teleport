package com.urntt.teleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.compat.OneConfigBridge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The configuration mode and the mod's own settings, persisted as JSON. Every change is written to disk immediately.
 */
public final class TeleportConfig implements TeleportSettings {
	/** Shortest Click TP reach, in blocks. */
	public static final int MIN_CLICK_TP_REACH = 0;
	/** Longest Click TP reach, in blocks. */
	public static final int MAX_CLICK_TP_REACH = 32767;
	/** Click TP reach on a fresh installation, in blocks. */
	public static final int DEFAULT_CLICK_TP_REACH = 64;

	private static final Logger LOGGER = LoggerFactory.getLogger(TeleportClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Settings settings;

	private TeleportConfig(final Path path, final Settings settings) {
		this.path = path;
		this.settings = settings;
	}

	/**
	 * Returns the location of the config file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(TeleportClient.MOD_ID + ".json");
	}

	/**
	 * Loads the configuration from {@code path}. A missing file is created with the defaults. A readable file is
	 * rewritten with any settings it lacks and without those it no longer uses. An unreadable file falls back to the
	 * defaults and is left untouched until the next change is saved.
	 */
	public static TeleportConfig load(final Path path) {
		if (Files.notExists(path)) {
			TeleportConfig config = new TeleportConfig(path, new Settings().normalized());
			config.save();
			return config;
		}

		Settings settings;
		try {
			settings = GSON.fromJson(Files.readString(path), Settings.class);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read config file {}, using defaults", path, e);
			return new TeleportConfig(path, new Settings().normalized());
		}

		TeleportConfig config = new TeleportConfig(path, (settings != null ? settings : new Settings()).normalized());
		config.save();
		return config;
	}

	/**
	 * The mode the player chose, which may name OneConfig even while OneConfig is not installed, or {@code null} until
	 * the player chooses one.
	 */
	public @Nullable ConfigMode storedMode() {
		return this.settings.mode;
	}

	/**
	 * The mode that applies. Until the player chooses one, OneConfig manages the mod while it is available. OneConfig
	 * mode falls back to the custom settings while OneConfig is not available.
	 */
	public ConfigMode effectiveMode() {
		boolean oneConfig = OneConfigBridge.isAvailable();
		ConfigMode mode = this.settings.mode;
		if (mode == null) {
			return oneConfig ? ConfigMode.ONECONFIG : ConfigMode.CUSTOM;
		}
		return mode == ConfigMode.ONECONFIG && !oneConfig ? ConfigMode.CUSTOM : mode;
	}

	/** The settings that apply in the {@linkplain #effectiveMode() effective mode}. */
	public TeleportSettings effective() {
		return switch (this.effectiveMode()) {
			case DISABLED -> SettingValues.DISABLED;
			case CUSTOM -> this;
			case ONECONFIG -> OneConfigBridge.settings().orElse(this);
		};
	}

	public void setMode(final ConfigMode mode) {
		this.settings.mode = mode;
		this.save();
	}

	/**
	 * Changes Click TP where the settings that apply come from, as the Click TP key does: in OneConfig in OneConfig
	 * mode, and in the mod's own settings otherwise.
	 */
	public void setEffectiveClickTp(final boolean clickTp) {
		if (this.effectiveMode() == ConfigMode.ONECONFIG) {
			OneConfigBridge.setClickTp(clickTp);
		} else {
			this.setClickTp(clickTp);
		}
	}

	/** The mod's own settings are always enabled; Disabled mode turns the mod off. */
	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public boolean clickTp() {
		return this.settings.clickTp;
	}

	public void setClickTp(final boolean clickTp) {
		this.settings.clickTp = clickTp;
		this.save();
	}

	@Override
	public int clickTpReach() {
		return this.settings.clickTpReach;
	}

	/**
	 * Sets the Click TP reach, clamped to the allowed range.
	 */
	public void setClickTpReach(final int reach) {
		this.settings.clickTpReach = clampReach(reach);
		this.save();
	}

	@Override
	public boolean clickTpPreview() {
		return this.settings.clickTpPreview;
	}

	public void setClickTpPreview(final boolean preview) {
		this.settings.clickTpPreview = preview;
		this.save();
	}

	@Override
	public MultiplayerRules multiplayerRules() {
		return new MultiplayerRules(this.settings.multiplayerMode, this.settings.servers);
	}

	public MultiplayerMode multiplayerMode() {
		return this.settings.multiplayerMode;
	}

	public void setMultiplayerMode(final MultiplayerMode multiplayerMode) {
		this.settings.multiplayerMode = multiplayerMode;
		this.save();
	}

	/** The server list used by the whitelist and blacklist modes. */
	public List<String> servers() {
		return List.copyOf(this.settings.servers);
	}

	public void setServers(final List<String> servers) {
		this.settings.servers = normalizedServers(servers);
		this.save();
	}

	/** Clamps a Click TP reach to the allowed range. */
	public static int clampReach(final int reach) {
		return Mth.clamp(reach, MIN_CLICK_TP_REACH, MAX_CLICK_TP_REACH);
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.settings));
		} catch (IOException e) {
			LOGGER.error("Failed to write config file {}", this.path, e);
		}
	}

	/**
	 * Trims the entries of a server list and drops blank ones.
	 */
	private static List<String> normalizedServers(final @Nullable List<String> servers) {
		List<String> entries = new ArrayList<>();
		if (servers != null) {
			for (String entry : servers) {
				if (entry != null && !entry.isBlank()) {
					entries.add(entry.trim());
				}
			}
		}
		return entries;
	}

	/**
	 * The serialized form. Field initializers are the defaults, which also apply to keys missing from the file. Gson
	 * ignores keys that no field matches, such as the defaults and reset rules of 1.1.0, so saving drops them.
	 */
	private static final class Settings {
		/** The mode the player chose, or {@code null} until then; Gson leaves it out of the file while it is. */
		private @Nullable ConfigMode mode;
		/**
		 * The on/off state of 1.1.0, which had no modes. Only read, to start such a file in Disabled mode if the mod
		 * was off; Gson leaves it out of the file once it is {@code null}.
		 */
		private @Nullable Boolean enabled;
		private boolean clickTp = SettingValues.DEFAULTS.clickTp();
		private int clickTpReach = SettingValues.DEFAULTS.clickTpReach();
		private boolean clickTpPreview = SettingValues.DEFAULTS.clickTpPreview();
		private MultiplayerMode multiplayerMode = defaultRules().mode();
		private List<String> servers = new ArrayList<>(defaultRules().servers());

		private static MultiplayerRules defaultRules() {
			return Objects.requireNonNull(SettingValues.DEFAULTS.multiplayerRules());
		}

		/**
		 * Starts a 1.1.0 file whose mod was off in Disabled mode, replaces values Gson could not map (unknown mode
		 * names, which count as no choice, a missing list), clamps the reach and tidies the server list.
		 */
		private Settings normalized() {
			if (this.mode == null && Boolean.FALSE.equals(this.enabled)) {
				this.mode = ConfigMode.DISABLED;
			}
			this.enabled = null;
			this.clickTpReach = clampReach(this.clickTpReach);
			if (this.multiplayerMode == null) {
				this.multiplayerMode = defaultRules().mode();
			}
			this.servers = normalizedServers(this.servers);
			return this;
		}
	}
}
