package com.urntt.teleport.compat;

import com.urntt.teleport.config.TeleportSettings;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Connects the mod to its optional OneConfig integration without referencing OneConfig classes. The integration in
 * {@code compat.oneconfig} registers itself here when OneConfig calls its entrypoint, so while OneConfig is not
 * installed nothing is registered and OneConfig mode is unavailable.
 */
public final class OneConfigBridge {
	/** OneConfig's mod id. */
	public static final String MOD_ID = "oneconfig";

	private static @Nullable Supplier<TeleportSettings> settings;
	private static @Nullable Consumer<Boolean> clickTpSetter;
	private static @Nullable Runnable openOneConfig;

	private OneConfigBridge() {
	}

	/**
	 * Called by the OneConfig integration once its settings are registered with OneConfig.
	 *
	 * @param settings      the settings OneConfig currently holds
	 * @param clickTpSetter changes and saves Click TP in OneConfig, for the Click TP key
	 * @param openOneConfig opens OneConfig's screen on this mod's page
	 */
	public static void register(final Supplier<TeleportSettings> settings, final Consumer<Boolean> clickTpSetter,
			final Runnable openOneConfig) {
		OneConfigBridge.settings = settings;
		OneConfigBridge.clickTpSetter = clickTpSetter;
		OneConfigBridge.openOneConfig = openOneConfig;
	}

	/** Whether OneConfig is installed and holds this mod's settings. */
	public static boolean isAvailable() {
		return settings != null;
	}

	/** The settings OneConfig currently holds, if it is available. */
	public static Optional<TeleportSettings> settings() {
		return settings != null ? Optional.of(settings.get()) : Optional.empty();
	}

	/** Changes and saves Click TP in OneConfig, if it is available. */
	public static void setClickTp(final boolean clickTp) {
		if (clickTpSetter != null) {
			clickTpSetter.accept(clickTp);
		}
	}

	/** Opens OneConfig's screen on this mod's page, if OneConfig is available. */
	public static void openOneConfig() {
		if (openOneConfig != null) {
			openOneConfig.run();
		}
	}
}
