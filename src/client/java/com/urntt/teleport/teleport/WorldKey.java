package com.urntt.teleport.teleport;

import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Names the world or server that saved locations belong to.
 */
public final class WorldKey {
	private WorldKey() {
	}

	/**
	 * Returns the key of the world {@code minecraft} is in: {@code singleplayer/<save folder>} for a world this client
	 * hosts, {@code realms/<name>} for a Realm, {@code server/<address>} for any other server, or {@code null} if it
	 * cannot tell.
	 */
	public static @Nullable String of(final Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (minecraft.isLocalServer() && server != null) {
			Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			return folder != null ? "singleplayer/" + folder : null;
		}

		ServerData data = minecraft.getCurrentServer();
		if (data == null) {
			return null;
		}
		return data.isRealm() ? "realms/" + data.name : "server/" + data.ip.trim().toLowerCase(Locale.ROOT);
	}
}
