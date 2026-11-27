package com.urntt.teleport.gametest;

import static com.urntt.teleport.gametest.GameTestSupport.LOGGER;
import static com.urntt.teleport.gametest.GameTestSupport.bindKey;
import static com.urntt.teleport.gametest.GameTestSupport.check;
import static com.urntt.teleport.gametest.GameTestSupport.clearMessages;
import static com.urntt.teleport.gametest.GameTestSupport.configure;
import static com.urntt.teleport.gametest.GameTestSupport.listenForMessages;
import static com.urntt.teleport.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.teleport.gametest.GameTestSupport.requireMessageWith;
import static com.urntt.teleport.gametest.GameTestSupport.unbindKey;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.compat.OneConfigBridge;
import com.urntt.teleport.config.ConfigMode;
import com.urntt.teleport.config.MultiplayerMode;
import com.urntt.teleport.config.ServerListScreen;
import com.urntt.teleport.config.SettingKeys;
import com.urntt.teleport.config.SettingValues;
import com.urntt.teleport.config.TeleportConfig;
import com.urntt.teleport.config.TeleportConfigScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Checks the configuration modes and the configuration screen: the mode button cycles through the offered modes and
 * saves each, the settings are editable only in Custom mode and otherwise show the values that apply, OneConfig mode
 * has no multiplayer section and links to OneConfig, and the key bindings work with every mode. The test client runs
 * without OneConfig by default and with it when Gradle is given {@code -PoneConfigRuntime=true}; the checks follow
 * whichever modes are offered. Screenshots show every settings screen and the key bindings in both languages.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ConfigScreenGameTest implements FabricClientGameTest {
	/** A custom reach that differs from the default, to show which settings apply in each mode. */
	private static final int CUSTOM_REACH = 10;
	/** A custom multiplayer mode that differs from the default, to show which settings apply in each mode. */
	private static final MultiplayerMode CUSTOM_MULTIPLAYER_MODE = MultiplayerMode.WHITELIST;
	private static final Pattern ONECONFIG_CLICK_TP_ON = Pattern.compile("\"clickTp\"\\s*:\\s*true");

	@Override
	public void runTest(final ClientGameTestContext context) {
		listenForMessages(context);
		TeleportConfig config = TeleportClient.config();
		boolean oneConfigInstalled = FabricLoader.getInstance().isModLoaded(OneConfigBridge.MOD_ID);
		ConfigMode defaultMode = oneConfigInstalled ? ConfigMode.ONECONFIG : ConfigMode.CUSTOM;
		check(config.storedMode() == null, "no mode should be chosen by default, was " + config.storedMode());
		check(config.effectiveMode() == defaultMode, "the default mode should be " + defaultMode + ", was " + config.effectiveMode());
		LOGGER.info("Checking the configuration screen {} OneConfig", oneConfigInstalled ? "with" : "without");

		checkModeCycle(context, config, defaultMode, oneConfigInstalled);
		configure(context, c -> c.setMode(ConfigMode.CUSTOM));
		checkReachBox(context);
		if (!oneConfigInstalled) {
			checkOneConfigMissing(context, config);
		}

		configure(context, c -> {
			c.setMultiplayerMode(MultiplayerMode.WHITELIST);
			c.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		for (String language : List.of("en_us", "zh_cn")) {
			setLanguage(context, language);
			takeScreenshots(context, language);
		}
		setLanguage(context, "en_us");
		context.setScreen(() -> null);

		checkKeys(context, oneConfigInstalled);
		configure(context, c -> {
			c.setMode(ConfigMode.CUSTOM);
			c.setClickTpReach(SettingValues.DEFAULTS.clickTpReach());
			c.setMultiplayerMode(MultiplayerMode.DISABLED);
			c.setServers(List.of());
		});
		check(SettingValues.copyOf(loadSavedConfig()).equals(SettingValues.DEFAULTS), "the test should leave the default settings behind");
	}

	/**
	 * Clicks the mode button through every offered mode, starting from and ending at the default mode. OneConfig mode
	 * is only offered while OneConfig is installed.
	 */
	private static void checkModeCycle(final ClientGameTestContext context, final TeleportConfig config, final ConfigMode defaultMode,
			final boolean oneConfigInstalled) {
		List<ConfigMode> modes = oneConfigInstalled
				? List.of(ConfigMode.DISABLED, ConfigMode.CUSTOM, ConfigMode.ONECONFIG)
				: List.of(ConfigMode.DISABLED, ConfigMode.CUSTOM);
		configure(context, c -> {
			c.setClickTpReach(CUSTOM_REACH);
			c.setMultiplayerMode(CUSTOM_MULTIPLAYER_MODE);
		});
		check(config.storedMode() == null, "changing settings should not choose a mode");
		context.setScreen(() -> new TeleportConfigScreen(null));
		// The option list positions its widgets while rendering, so let it render before clicking.
		context.waitTicks(2);
		checkScreenFor(context, config, defaultMode);

		int start = modes.indexOf(defaultMode);
		for (int i = 1; i <= modes.size(); i++) {
			ConfigMode expected = modes.get((start + i) % modes.size());
			clickWidget(context, widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof ConfigMode);
			context.waitForScreen(TeleportConfigScreen.class);
			context.waitTicks(2);
			check(config.storedMode() == expected, "the mode button should switch to " + expected + ", was " + config.storedMode());
			check(loadSavedConfig().storedMode() == expected, "the " + expected + " mode should be saved");
			parkCursor(context);
			context.takeScreenshot("teleport-config-" + expected.name().toLowerCase(Locale.ROOT));
			checkScreenFor(context, config, expected);
		}
	}

	/**
	 * Checks that the open screen shows the settings of {@code mode}: editable in Custom mode, greyed out with the
	 * values that apply otherwise, and without the multiplayer section in OneConfig mode.
	 */
	private static void checkScreenFor(final ClientGameTestContext context, final TeleportConfig config, final ConfigMode mode) {
		switch (mode) {
			case CUSTOM -> {
				checkSettings(context, true, true);
				check(SettingValues.copyOf(TeleportClient.settings()).equals(SettingValues.copyOf(config)), "Custom mode should apply the custom settings");
				check(shownReach(context) == CUSTOM_REACH, "Custom mode should show the custom reach");
				check(shownMultiplayerMode(context) == CUSTOM_MULTIPLAYER_MODE, "Custom mode should show the custom multiplayer mode");
			}
			case DISABLED -> {
				checkSettings(context, false, true);
				check(SettingValues.copyOf(TeleportClient.settings()).equals(SettingValues.DISABLED), "Disabled mode should apply the disabled settings");
				check(!TeleportClient.settings().enabled(), "Disabled mode should turn the mod off");
				check(shownReach(context) == SettingValues.DISABLED.clickTpReach(), "Disabled mode should show the reach that applies");
				check(shownMultiplayerMode(context) == MultiplayerMode.DISABLED, "Disabled mode should show the multiplayer mode that applies");
			}
			case ONECONFIG -> checkOneConfigMode(context);
		}
	}

	/**
	 * In OneConfig mode, OneConfig's settings apply: off by default, without multiplayer rules. The screen shows its
	 * Click TP settings greyed out, no multiplayer section, and links to the mod's OneConfig page.
	 */
	private static void checkOneConfigMode(final ClientGameTestContext context) {
		check(SettingValues.copyOf(TeleportClient.settings()).equals(SettingValues.ONECONFIG_DEFAULTS),
				"OneConfig mode should apply OneConfig's settings, which start at their defaults, instead of the custom ones");
		checkSettings(context, false, false);
		check(shownReach(context) == SettingValues.ONECONFIG_DEFAULTS.clickTpReach(), "OneConfig mode should show OneConfig's reach");

		clickWidget(context, widget -> widget instanceof Button && hasLabel(widget, SettingKeys.OPEN_ONECONFIG));
		context.waitFor(client -> client.gui.screen() != null && !(client.gui.screen() instanceof TeleportConfigScreen));
		context.waitTicks(20);
		context.takeScreenshot("teleport-oneconfig-page");
		String screen = context.computeOnClient(client -> client.gui.screen().getClass().getName());
		LOGGER.info("Open in OneConfig shows {}", screen);
		context.setScreen(() -> new TeleportConfigScreen(null));
		// The option list positions its widgets while rendering, so let it render before clicking.
		context.waitTicks(2);
	}

	/** In Custom mode, typing into the reach box saves valid values as they are typed and ignores invalid ones. */
	private static void checkReachBox(final ClientGameTestContext context) {
		context.setScreen(() -> new TeleportConfigScreen(null));
		context.waitTicks(2);
		context.runOnClient(client -> reachBox(client).setValue("300"));
		check(loadSavedConfig().clickTpReach() == 300, "a valid reach should be saved as it is typed");
		context.runOnClient(client -> reachBox(client).setValue("40000"));
		check(loadSavedConfig().clickTpReach() == 300, "a reach out of range should not be saved");
		parkCursor(context);
		context.takeScreenshot("teleport-config-invalid-reach");
		context.runOnClient(client -> reachBox(client).setValue(Integer.toString(SettingValues.DEFAULTS.clickTpReach())));
		check(loadSavedConfig().clickTpReach() == SettingValues.DEFAULTS.clickTpReach(), "the reach should be back at the default");
	}

	/**
	 * A file that names OneConfig mode while OneConfig is not installed keeps that mode, but the custom settings apply
	 * and can be edited, and the mode button says why.
	 */
	private static void checkOneConfigMissing(final ClientGameTestContext context, final TeleportConfig config) {
		configure(context, c -> c.setMode(ConfigMode.ONECONFIG));
		context.setScreen(() -> new TeleportConfigScreen(null));
		context.waitTicks(2);
		check(config.effectiveMode() == ConfigMode.CUSTOM, "Custom mode should apply while OneConfig is missing");
		checkSettings(context, true, true);
		hoverWidget(context, widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof ConfigMode);
		context.takeScreenshot("teleport-config-oneconfig-missing");
		check(loadSavedConfig().storedMode() == ConfigMode.ONECONFIG, "opening the screen should not change the stored mode");
		configure(context, c -> c.setMode(ConfigMode.CUSTOM));
		parkCursor(context);
	}

	/**
	 * Opens the screen with its key binding in a world. With OneConfig, the Click TP key only reports that the mod is
	 * off while OneConfig's toggle is off, and once it is on, changes and saves OneConfig's Click TP and leaves the
	 * mod's own alone.
	 */
	private static void checkKeys(final ClientGameTestContext context, final boolean oneConfigInstalled) {
		KeyMapping openSettingsKey = bindKey(context, TeleportClient.OPEN_SETTINGS_KEY_NAME, "key.keyboard.k");
		KeyMapping clickTpKey = bindKey(context, TeleportClient.TOGGLE_CLICK_TP_KEY_NAME, "key.keyboard.j");
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.getInput().pressKey(openSettingsKey);
			context.waitForScreen(TeleportConfigScreen.class);
			context.setScreen(() -> null);

			if (oneConfigInstalled) {
				configure(context, c -> c.setMode(ConfigMode.ONECONFIG));
				clearMessages(context);
				context.getInput().pressKey(clickTpKey);
				context.waitTick();
				check(!context.computeOnClient(client -> TeleportClient.settings().clickTp()),
						"the Click TP key should not change Click TP while OneConfig's toggle is off");
				requireMessageWith(context, "message.teleport.turned_off");

				setOneConfigToggle(context, true);
				context.getInput().pressKey(clickTpKey);
				context.waitTick();
				check(context.computeOnClient(client -> TeleportClient.settings().clickTp()), "the Click TP key should turn Click TP on in OneConfig");
				check(!loadSavedConfig().clickTp(), "the Click TP key should leave the mod's own Click TP alone in OneConfig mode");
				String saved = readOneConfigFile();
				check(ONECONFIG_CLICK_TP_ON.matcher(saved).find(), "OneConfig should save Click TP, saved " + saved);
				context.getInput().pressKey(clickTpKey);
				context.waitTick();
				check(!context.computeOnClient(client -> TeleportClient.settings().clickTp()), "the Click TP key should turn Click TP off again in OneConfig");
				setOneConfigToggle(context, false);
			}
		}
		unbindKey(context, openSettingsKey);
		unbindKey(context, clickTpKey);
	}

	/**
	 * Flips the toggle on the mod's OneConfig card. The tests cannot reference OneConfig's classes, so this sets the
	 * field of the settings object OneConfig manages.
	 */
	private static void setOneConfigToggle(final ClientGameTestContext context, final boolean enabled) {
		context.runOnClient(client -> {
			Object settings = OneConfigBridge.settings().orElseThrow();
			try {
				settings.getClass().getField("enabled").setBoolean(settings, enabled);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot set OneConfig's toggle", e);
			}
		});
	}

	/** Reads OneConfig's file for this mod, wherever OneConfig keeps it under the game directory. */
	private static String readOneConfigFile() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		String name = TeleportClient.MOD_ID + "-oneconfig.json";
		try (Stream<Path> files = Files.walk(gameDir)) {
			Optional<Path> file = files.filter(path -> path.getFileName().toString().equals(name)).findFirst();
			check(file.isPresent(), "OneConfig should keep " + name + " under " + gameDir);
			LOGGER.info("OneConfig keeps this mod's settings in {}", gameDir.relativize(file.get()));
			return Files.readString(file.get());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Takes screenshots of the configuration screen scrolled to the top and the bottom, the server list and the keys. */
	private static void takeScreenshots(final ClientGameTestContext context, final String language) {
		context.setScreen(() -> new TeleportConfigScreen(null));
		parkCursor(context);
		context.takeScreenshot("teleport-config-screen-" + language);
		scrollDown(context);
		context.takeScreenshot("teleport-config-screen-bottom-" + language);

		context.setScreen(() -> new ServerListScreen(new TeleportConfigScreen(null), TeleportClient.config()));
		parkCursor(context);
		context.takeScreenshot("teleport-server-list-" + language);

		context.setScreen(() -> new KeyBindsScreen(null, Minecraft.getInstance().options));
		scrollDown(context);
		context.takeScreenshot("teleport-key-binds-" + language);
	}

	/** Scrolls the open screen to the bottom with the mouse over its middle, then parks the cursor. */
	private static void scrollDown(final ClientGameTestContext context) {
		double[] center = context.computeOnClient(client -> {
			Window window = client.getWindow();
			return new double[] {window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0};
		});
		context.getInput().setCursorPos(center[0], center[1]);
		context.getInput().scroll(-1000);
		parkCursor(context);
	}

	private static void setLanguage(final ClientGameTestContext context, final String code) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(code);
			client.options.languageCode = code;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
	}

	/** Clicks the first widget of the open screen that matches, with the real mouse. */
	private static void clickWidget(final ClientGameTestContext context, final Predicate<AbstractWidget> matches) {
		hoverWidget(context, matches);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	/** Moves the real mouse over the first widget of the open screen that matches, and waits for its tooltip. */
	private static void hoverWidget(final ClientGameTestContext context, final Predicate<AbstractWidget> matches) {
		double[] position = context.computeOnClient(client -> {
			AbstractWidget widget = widgets(client).stream()
					.filter(matches)
					.findFirst()
					.orElseThrow(() -> new AssertionError("widget not found"));
			Window window = client.getWindow();
			double scale = window.getScreenWidth() / (double) window.getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.waitTicks(2);
	}

	/** Moves the cursor into a corner, so that no tooltip covers the screenshot. */
	private static void parkCursor(final ClientGameTestContext context) {
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
	}

	private static int shownReach(final ClientGameTestContext context) {
		return context.computeOnClient(client -> Integer.parseInt(reachBox(client).getValue()));
	}

	private static MultiplayerMode shownMultiplayerMode(final ClientGameTestContext context) {
		return context.computeOnClient(client -> widgets(client).stream()
				.filter(widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof MultiplayerMode)
				.map(widget -> (MultiplayerMode) ((CycleButton<?>) widget).getValue())
				.findFirst()
				.orElseThrow(() -> new AssertionError("multiplayer mode button not found")));
	}

	/**
	 * Checks that the settings are all editable or all greyed out, and that the multiplayer section is there or not.
	 */
	private static void checkSettings(final ClientGameTestContext context, final boolean active, final boolean multiplayer) {
		List<Boolean> states = context.computeOnClient(client -> settingWidgets(client).stream().map(widget -> widget.active).toList());
		// Click TP, the reach box and the preview; with multiplayer rules, the multiplayer mode and the server list button.
		int expected = multiplayer ? 5 : 3;
		check(states.size() == expected, "expected " + expected + " setting widgets, found " + states.size());
		check(states.stream().allMatch(state -> state == active), "the settings should all be " + (active ? "editable" : "greyed out") + ": " + states);
	}

	/** Every widget that edits a setting. */
	private static List<AbstractWidget> settingWidgets(final Minecraft client) {
		return widgets(client).stream()
				.filter(widget -> widget instanceof CycleButton<?> cycle
								&& (cycle.getValue() instanceof Boolean || cycle.getValue() instanceof MultiplayerMode)
						|| widget instanceof EditBox
						|| widget instanceof Button && hasLabel(widget, SettingKeys.EDIT_SERVERS))
				.toList();
	}

	/**
	 * Returns whether {@code widget}'s label is the translation of {@code key}. Inactive buttons show a greyed copy of
	 * their label, so the label is compared by its key rather than by equality.
	 */
	private static boolean hasLabel(final AbstractWidget widget, final String key) {
		return widget.getMessage().getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key);
	}

	private static EditBox reachBox(final Minecraft client) {
		return widgets(client).stream()
				.filter(EditBox.class::isInstance)
				.map(EditBox.class::cast)
				.findFirst()
				.orElseThrow(() -> new AssertionError("reach box not found"));
	}

	private static List<AbstractWidget> widgets(final Minecraft client) {
		Screen screen = Objects.requireNonNull(client.gui.screen(), "no screen is open");
		List<AbstractWidget> widgets = new ArrayList<>();
		collect(screen.children(), widgets);
		return widgets;
	}

	private static void collect(final List<? extends GuiEventListener> listeners, final List<AbstractWidget> widgets) {
		for (GuiEventListener listener : listeners) {
			if (listener instanceof AbstractWidget widget) {
				widgets.add(widget);
			}
			if (listener instanceof ContainerEventHandler container) {
				collect(container.children(), widgets);
			}
		}
	}
}
