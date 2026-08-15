package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Stores each player in data/<uuid>.yml using UTF-8 and atomic replacement when supported.
public final class FilePlayerDataStorage implements PlayerDataStorage {
	private static final int SCHEMA_VERSION = 1;

	private final Path dataDirectory;

	public FilePlayerDataStorage(Path dataDirectory) {
		this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
	}

	@Override
	public Optional<StoredPlayerData> load(UUID playerId) throws PlayerDataStorageException {
		ensureDataDirectory();
		Path playerFile = getPlayerFile(playerId);
		if (Files.notExists(playerFile)) {
			return Optional.empty();
		}

		if (!Files.isRegularFile(playerFile)) {
			throw new PlayerDataStorageException("Player data path is not a file: " + playerFile);
		}

		YamlConfiguration configuration = new YamlConfiguration();
		try (BufferedReader reader = Files.newBufferedReader(playerFile, StandardCharsets.UTF_8)) {
			configuration.load(reader);
		} catch (InvalidConfigurationException exception) {
			throw new CorruptPlayerDataException("Player data is not valid YAML: " + playerFile, exception);
		} catch (IOException exception) {
			throw new PlayerDataStorageException("Could not read player data: " + playerFile, exception);
		}

		try {
			return Optional.of(readStoredPlayerData(configuration));
		} catch (IllegalArgumentException exception) {
			throw new CorruptPlayerDataException("Player data contains invalid values: " + playerFile, exception);
		}
	}

	@Override
	public void save(UUID playerId, StoredPlayerData playerData) throws PlayerDataStorageException {
		Objects.requireNonNull(playerData, "playerData");
		ensureDataDirectory();
		Path playerFile = getPlayerFile(playerId);
		Path temporaryFile = dataDirectory.resolve(playerId + ".yml.tmp");

		YamlConfiguration configuration = new YamlConfiguration();
		configuration.set("schema-version", SCHEMA_VERSION);
		configuration.set("last-known-name", playerData.getLastKnownName());
		configuration.set("nutrition", playerData.getNutrition());
		configuration.set("fullness", playerData.getFullness());
		configuration.set("nutrition-frozen", playerData.isNutritionFrozen());
		configuration.set("fullness-frozen", playerData.isFullnessFrozen());
		configuration.set("last-seen", playerData.getLastSeen());

		try {
			Files.writeString(
					temporaryFile,
					configuration.saveToString(),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
			moveIntoPlace(temporaryFile, playerFile);
		} catch (IOException exception) {
			tryDeleteTemporaryFile(temporaryFile);
			throw new PlayerDataStorageException("Could not save player data: " + playerFile, exception);
		}
	}

	private StoredPlayerData readStoredPlayerData(YamlConfiguration configuration) {
		int schemaVersion = readInteger(configuration, "schema-version");
		if (schemaVersion != SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported schema-version: " + schemaVersion);
		}

		return new StoredPlayerData(
				readText(configuration, "last-known-name"),
				readDouble(configuration, "nutrition"),
				readDouble(configuration, "fullness"),
				readBoolean(configuration, "nutrition-frozen"),
				readBoolean(configuration, "fullness-frozen"),
				readLong(configuration, "last-seen")
		);
	}

	private String readText(YamlConfiguration configuration, String path) {
		Object value = configuration.get(path);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(path + " must be non-empty text");
		}

		return text;
	}

	private double readDouble(YamlConfiguration configuration, String path) {
		Object value = configuration.get(path);
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(path + " must be a number");
		}

		return number.doubleValue();
	}

	private boolean readBoolean(YamlConfiguration configuration, String path) {
		Object value = configuration.get(path);
		if (!(value instanceof Boolean booleanValue)) {
			throw new IllegalArgumentException(path + " must be true or false");
		}

		return booleanValue;
	}

	private java.math.BigDecimal readWholeNumber(YamlConfiguration configuration, String path) {
		Object value = configuration.get(path);
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(path + " must be a whole number");
		}

		try {
			return new java.math.BigDecimal(number.toString());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(path + " must be a whole number", exception);
		}
	}

	private int readInteger(YamlConfiguration configuration, String path) {
		try {
			return readWholeNumber(configuration, path).intValueExact();
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException(path + " must be a whole number within the integer range", exception);
		}
	}

	private long readLong(YamlConfiguration configuration, String path) {
		try {
			return readWholeNumber(configuration, path).longValueExact();
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException(path + " must be a whole number within the long range", exception);
		}
	}

	private void moveIntoPlace(Path temporaryFile, Path playerFile) throws IOException {
		try {
			Files.move(
					temporaryFile,
					playerFile,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporaryFile, playerFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void ensureDataDirectory() throws PlayerDataStorageException {
		try {
			Files.createDirectories(dataDirectory);
		} catch (IOException exception) {
			throw new PlayerDataStorageException(
					"Could not create player data directory: " + dataDirectory,
					exception
			);
		}

		if (!Files.isDirectory(dataDirectory)) {
			throw new PlayerDataStorageException("Player data path is not a directory: " + dataDirectory);
		}
	}

	private void tryDeleteTemporaryFile(Path temporaryFile) {
		try {
			Files.deleteIfExists(temporaryFile);
		} catch (IOException ignored) {
			// The original save exception remains the useful error to report.
		}
	}

	private Path getPlayerFile(UUID playerId) {
		return dataDirectory.resolve(Objects.requireNonNull(playerId, "playerId") + ".yml");
	}
}
