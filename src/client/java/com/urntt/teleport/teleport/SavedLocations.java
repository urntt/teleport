package com.urntt.teleport.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.teleport.TeleportClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code home}, {@code temp} and {@code spawn} locations of every world and server, persisted as JSON. Every
 * change is written to disk immediately. Only the locations of the selected world are visible.
 */
public final class SavedLocations {
	/**
	 * The player's respawn point.
	 *
	 * @param location where to go if the respawn block is not loaded or no longer offers a place to stand
	 * @param block the bed or respawn anchor the respawn point belongs to, or {@code null} for a plain position
	 */
	public record SpawnPoint(SavedLocation location, @Nullable BlockPos block) {
		/**
		 * Returns a respawn point at the bed or respawn anchor at {@code block}, set while looking at {@code yRot}.
		 */
		public static SpawnPoint atBlock(final ResourceKey<Level> dimension, final BlockPos block, final float yRot) {
			return new SpawnPoint(new SavedLocation(dimension, Vec3.atBottomCenterOf(block.above()), yRot, 0.0F), block);
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(TeleportClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final StoredFile file;
	private @Nullable String selected;

	private SavedLocations(final Path path, final StoredFile file) {
		this.path = path;
		this.file = file;
	}

	/**
	 * Returns the location of the file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(TeleportClient.MOD_ID).resolve("locations.json");
	}

	/**
	 * Loads the locations from {@code path}. A missing file starts empty. An unreadable file starts empty and is left
	 * untouched until the next change is saved.
	 */
	public static SavedLocations load(final Path path) {
		if (Files.notExists(path)) {
			return new SavedLocations(path, new StoredFile());
		}
		try {
			StoredFile file = GSON.fromJson(Files.readString(path), StoredFile.class);
			if (file == null) {
				file = new StoredFile();
			}
			if (file.worlds == null) {
				file.worlds = new LinkedHashMap<>();
			}
			return new SavedLocations(path, file);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read saved locations {}, starting empty", path, e);
			return new SavedLocations(path, new StoredFile());
		}
	}

	/**
	 * Selects the world whose locations are read and written, or none if {@code worldKey} is {@code null}.
	 */
	public void select(final @Nullable String worldKey) {
		this.selected = worldKey;
	}

	public @Nullable String selected() {
		return this.selected;
	}

	public Optional<SavedLocation> home() {
		return this.world(false).map(world -> world.home).map(StoredLocation::toLocation);
	}

	public void setHome(final SavedLocation location) {
		this.world(true).ifPresent(world -> world.home = StoredLocation.of(location));
		this.save();
	}

	public Optional<SavedLocation> temp() {
		return this.world(false).map(world -> world.temp).map(StoredLocation::toLocation);
	}

	public void setTemp(final SavedLocation location) {
		this.world(true).ifPresent(world -> world.temp = StoredLocation.of(location));
		this.save();
	}

	public Optional<SpawnPoint> spawn() {
		return this.world(false).map(world -> world.spawn).map(StoredSpawn::toSpawnPoint);
	}

	public void setSpawn(final SpawnPoint spawn) {
		this.world(true).ifPresent(world -> world.spawn = StoredSpawn.of(spawn));
		this.save();
	}

	/**
	 * Forgets the respawn point if it belongs to a bed or respawn anchor, because the server reported that the block
	 * no longer works. A respawn point at a plain position is kept.
	 */
	public void clearSpawnBlock() {
		Optional<StoredWorld> world = this.world(false);
		if (world.isPresent() && world.get().spawn != null && world.get().spawn.block != null) {
			world.get().spawn = null;
			this.save();
		}
	}

	private Optional<StoredWorld> world(final boolean create) {
		if (this.selected == null) {
			return Optional.empty();
		}
		StoredWorld world = this.file.worlds.get(this.selected);
		if (world == null && create) {
			world = new StoredWorld();
			this.file.worlds.put(this.selected, world);
		}
		return Optional.ofNullable(world);
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.file));
		} catch (IOException e) {
			LOGGER.error("Failed to write saved locations {}", this.path, e);
		}
	}

	/** The serialized form of the whole file, keyed by {@link WorldKey}. */
	private static final class StoredFile {
		private Map<String, StoredWorld> worlds = new LinkedHashMap<>();
	}

	private static final class StoredWorld {
		private @Nullable StoredLocation home;
		private @Nullable StoredLocation temp;
		private @Nullable StoredSpawn spawn;
	}

	private static final class StoredLocation {
		private String dimension = Level.OVERWORLD.identifier().toString();
		private double x;
		private double y;
		private double z;
		private float yRot;
		private float xRot;

		private static StoredLocation of(final SavedLocation location) {
			StoredLocation stored = new StoredLocation();
			stored.dimension = location.dimension().identifier().toString();
			stored.x = location.position().x;
			stored.y = location.position().y;
			stored.z = location.position().z;
			stored.yRot = location.yRot();
			stored.xRot = location.xRot();
			return stored;
		}

		private @Nullable SavedLocation toLocation() {
			Identifier id = this.dimension != null ? Identifier.tryParse(this.dimension) : null;
			if (id == null) {
				return null;
			}
			return new SavedLocation(ResourceKey.create(Registries.DIMENSION, id), new Vec3(this.x, this.y, this.z), this.yRot, this.xRot);
		}
	}

	private static final class StoredSpawn {
		private @Nullable StoredLocation location;
		private @Nullable StoredBlock block;

		private static StoredSpawn of(final SpawnPoint spawn) {
			StoredSpawn stored = new StoredSpawn();
			stored.location = StoredLocation.of(spawn.location());
			if (spawn.block() != null) {
				stored.block = new StoredBlock();
				stored.block.x = spawn.block().getX();
				stored.block.y = spawn.block().getY();
				stored.block.z = spawn.block().getZ();
			}
			return stored;
		}

		private @Nullable SpawnPoint toSpawnPoint() {
			SavedLocation location = this.location != null ? this.location.toLocation() : null;
			if (location == null) {
				return null;
			}
			BlockPos blockPos = this.block != null ? new BlockPos(this.block.x, this.block.y, this.block.z) : null;
			return new SpawnPoint(location, blockPos);
		}
	}

	private static final class StoredBlock {
		private int x;
		private int y;
		private int z;
	}
}
