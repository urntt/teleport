package com.urntt.teleport.teleport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The positions {@code /tp @c back} returns to, following Essential Commands' {@code /back}: each teleport records
 * where the player was before it, so going back is itself recorded and repeating it alternates between two places.
 * The history lives in memory and is cleared when the player leaves the world.
 */
public final class BackHistory {
	/** What moved the player away from a recorded position. */
	public enum Kind implements StringRepresentable {
		/** A client teleport by this mod. */
		CLIENT("client"),
		/** A teleport the server sent, such as vanilla {@code /tp} or an ender pearl. */
		SERVER("server"),
		/** The player died there. */
		DEATH("death");

		private final String name;

		Kind(final String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public record Entry(Kind kind, SavedLocation location) {
	}

	/** Number of entries kept; older ones are dropped. */
	public static final int CAPACITY = 32;
	/**
	 * Entries closer than this to the current position are skipped by {@code back}, and server moves shorter than
	 * this are position corrections rather than teleports. In blocks.
	 */
	public static final double MIN_DISTANCE = 1.0;
	/**
	 * A server move within this many ticks of a client teleport that puts the player back at the teleport's origin
	 * means that the server rejected the teleport.
	 */
	public static final int REJECTION_WINDOW_TICKS = 40;

	/** Newest first. */
	private final Deque<Entry> entries = new ArrayDeque<>();
	private @Nullable Vec3 lastClientTeleportOrigin;
	private long lastClientTeleportTick;
	private long ticks;

	public void tick() {
		this.ticks++;
	}

	public void clear() {
		this.entries.clear();
		this.lastClientTeleportOrigin = null;
	}

	/**
	 * Records the position the player leaves with a client teleport.
	 */
	public void recordClientTeleport(final SavedLocation origin) {
		this.add(Kind.CLIENT, origin);
		this.lastClientTeleportOrigin = origin.position();
		this.lastClientTeleportTick = this.ticks;
	}

	/**
	 * Records the position a server teleport moved the player away from, unless the move was only a correction or
	 * the server rejecting a recent client teleport.
	 */
	public void recordServerTeleport(final SavedLocation before, final Vec3 after) {
		if (before.position().distanceTo(after) < MIN_DISTANCE) {
			return;
		}
		if (this.lastClientTeleportOrigin != null
				&& this.ticks - this.lastClientTeleportTick <= REJECTION_WINDOW_TICKS
				&& after.distanceTo(this.lastClientTeleportOrigin) < MIN_DISTANCE) {
			return;
		}
		this.add(Kind.SERVER, before);
	}

	public void recordDeath(final SavedLocation location) {
		this.add(Kind.DEATH, location);
	}

	/**
	 * Returns the newest entry of {@code kind} (any kind if {@code null}) in {@code dimension} that is at least
	 * {@link #MIN_DISTANCE} away from {@code current}.
	 */
	public Optional<SavedLocation> latest(final @Nullable Kind kind, final ResourceKey<Level> dimension, final Vec3 current) {
		return this.entries.stream()
				.filter(entry -> kind == null || entry.kind() == kind)
				.map(Entry::location)
				.filter(location -> location.dimension() == dimension && location.position().distanceTo(current) >= MIN_DISTANCE)
				.findFirst();
	}

	/** All entries, newest first. */
	public List<Entry> entries() {
		return List.copyOf(this.entries);
	}

	private void add(final Kind kind, final SavedLocation location) {
		this.entries.addFirst(new Entry(kind, location));
		while (this.entries.size() > CAPACITY) {
			this.entries.removeLast();
		}
	}
}
