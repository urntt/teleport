package com.urntt.teleport.teleport;

import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turns what the client observes into {@code back} entries and respawn points: server teleports, deaths, respawns,
 * and the beds and respawn anchors the player uses.
 */
public final class LocationTracker {
	/** Vanilla's message when a bed or respawn anchor becomes the player's respawn point. */
	public static final String SPAWN_SET_KEY = "block.minecraft.set_spawn";
	/** How long after using a bed or respawn anchor its "respawn point set" message is accepted, in milliseconds. */
	private static final long SPAWN_BLOCK_CONFIRM_MILLIS = 10_000L;

	private record UsedSpawnBlock(ResourceKey<Level> dimension, BlockPos block, float yRot, long millis) {
	}

	private final BackHistory history;
	private final SavedLocations locations;
	private WeakReference<LocalPlayer> lastPositionedPlayer = new WeakReference<>(null);
	private boolean respawnPending;
	private @Nullable UsedSpawnBlock usedSpawnBlock;

	public LocationTracker(final BackHistory history, final SavedLocations locations) {
		this.history = history;
		this.locations = locations;
	}

	/**
	 * Starts tracking a new world. Everything recorded in the previous one is dropped here as well as on disconnect,
	 * because leaving a singleplayer world does not always report a disconnect.
	 */
	public void onJoin(final Minecraft minecraft) {
		this.reset();
		this.locations.select(WorldKey.of(minecraft));
	}

	public void onDisconnect() {
		this.reset();
		this.locations.select(null);
	}

	private void reset() {
		this.history.clear();
		this.lastPositionedPlayer = new WeakReference<>(null);
		this.respawnPending = false;
		this.usedSpawnBlock = null;
	}

	public void tick() {
		this.history.tick();
	}

	/**
	 * Called when the server sets the local player's position. The first position of a new player entity (after
	 * joining, respawning or changing dimension) is not a teleport; after a death it is the respawn point.
	 */
	public void onServerPosition(final LocalPlayer player, final SavedLocation before, final Vec3 after) {
		boolean newPlayer = this.lastPositionedPlayer.get() != player;
		this.lastPositionedPlayer = new WeakReference<>(player);
		if (newPlayer) {
			if (this.respawnPending) {
				this.respawnPending = false;
				this.locations.setSpawn(new SavedLocations.SpawnPoint(SavedLocation.of(player), null));
			}
			return;
		}
		this.history.recordServerTeleport(before, after);
	}

	public void onDeath(final LocalPlayer player) {
		this.history.recordDeath(SavedLocation.of(player));
		this.respawnPending = true;
	}

	/**
	 * Called when the server reports that the player's bed or respawn anchor no longer works.
	 */
	public void onNoRespawnBlock() {
		this.locations.clearSpawnBlock();
	}

	/**
	 * Called when the local player uses a block, to remember a bed or respawn anchor until the server confirms that
	 * it became the respawn point.
	 */
	public void onBlockUsed(final Level level, final BlockPos pos, final BlockState state, final float yRot) {
		BlockPos block = pos;
		if (state.getBlock() instanceof AbstractBedBlock) {
			if (state.getValue(AbstractBedBlock.PART) != BedPart.HEAD) {
				// Like vanilla, the respawn point of a bed is its head.
				block = pos.relative(state.getValue(AbstractBedBlock.FACING));
			}
		} else if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
			return;
		}
		this.usedSpawnBlock = new UsedSpawnBlock(level.dimension(), block.immutable(), yRot, Util.getMillis());
	}

	/**
	 * Called for every game message; the "respawn point set" message confirms the last used bed or anchor.
	 */
	public void onGameMessage(final Component message) {
		if (!(message.getContents() instanceof TranslatableContents translatable) || !SPAWN_SET_KEY.equals(translatable.getKey())) {
			return;
		}
		UsedSpawnBlock used = this.usedSpawnBlock;
		if (used != null && Util.getMillis() - used.millis() <= SPAWN_BLOCK_CONFIRM_MILLIS) {
			this.locations.setSpawn(SavedLocations.SpawnPoint.atBlock(used.dimension(), used.block(), used.yRot()));
		}
	}
}
