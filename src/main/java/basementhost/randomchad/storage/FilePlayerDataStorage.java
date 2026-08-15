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
import java.util.stream.Stream;

// Stores each player in data/<uuid>.yml using UTF-8 and atomic replacement when supported.
public final class FilePlayerDataStorage implements PlayerDataStorage {
	private static final int LEGACY_SCHEMA_VERSION = 1;
	private static final int SCHEMA_VERSION = 2;

	private final Path dataDirectory;

	public FilePlayerDataStorage(Path dataDirectory) {
		this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
	}

	@Override
	public Optional<StoredPlayerData> load(UUID playerId) throws PlayerDataStorageException {
		ensureDataDirectory();
		return readPlayerFile(playerId);
	}

	@Override
	public Optional<StoredPlayerData> loadReadOnly(UUID playerId) throws PlayerDataStorageException {
		if (!prepareReadOnlyDirectory()) {
			return Optional.empty();
		}
		return readPlayerFile(Objects.requireNonNull(playerId, "playerId"));
	}

	@Override
	public Optional<UUID> findPlayerIdByNameReadOnly(String playerName) throws PlayerDataStorageException {
		String searchedName = Objects.requireNonNull(playerName, "playerName").trim();
		if (searchedName.isEmpty() || !prepareReadOnlyDirectory()) {
			return Optional.empty();
		}

		PlayerFileMatch newestMatch = null;
		try (Stream<Path> files = Files.list(dataDirectory)) {
			for (Path path : files.filter(Files::isRegularFile).toList()) {
				UUID playerId = readPlayerId(path);
				if (playerId == null) {
					continue;
				}

				try {
					StoredPlayerData playerData = readPlayerFile(playerId).orElse(null);
					if (playerData == null
							|| !playerData.getLastKnownName().equalsIgnoreCase(searchedName)) {
						continue;
					}

					if (newestMatch == null || playerData.getLastSeen() > newestMatch.lastSeen()) {
						newestMatch = new PlayerFileMatch(playerId, playerData.getLastSeen());
					}
				} catch (PlayerDataStorageException ignored) {
					// An unrelated unreadable file must not prevent querying other offline players.
				}
			}
		} catch (IOException exception) {
			throw new PlayerDataStorageException(
					"Could not list player data files in " + dataDirectory,
					exception
			);
		}

		return newestMatch == null ? Optional.empty() : Optional.of(newestMatch.playerId());
	}

	private Optional<StoredPlayerData> readPlayerFile(UUID playerId) throws PlayerDataStorageException {
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
		if (!playerData.hasGroupSnapshot()) {
			throw new PlayerDataStorageException("Schema-version 2 saves require a complete group snapshot");
		}
		ensureDataDirectory();
		Path playerFile = getPlayerFile(playerId);
		Path temporaryFile = dataDirectory.resolve(playerId + ".yml.tmp");

		YamlConfiguration configuration = new YamlConfiguration();
		configuration.set("schema-version", SCHEMA_VERSION);
		configuration.set("last-known-name", playerData.getLastKnownName());
		configuration.set("last-known-group", playerData.getLastKnownGroupId().orElseThrow());
		configuration.set("max-nutrition", playerData.getMaxNutrition().orElseThrow());
		configuration.set("max-fullness", playerData.getMaxFullness().orElseThrow());
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
		return switch (schemaVersion) {
			case LEGACY_SCHEMA_VERSION -> new StoredPlayerData(
					readText(configuration, "last-known-name"),
					readDouble(configuration, "nutrition"),
					readDouble(configuration, "fullness"),
					readBoolean(configuration, "nutrition-frozen"),
					readBoolean(configuration, "fullness-frozen"),
					readLong(configuration, "last-seen")
			);
			case SCHEMA_VERSION -> new StoredPlayerData(
					readText(configuration, "last-known-name"),
					readText(configuration, "last-known-group"),
					readDouble(configuration, "max-nutrition"),
					readDouble(configuration, "max-fullness"),
					readDouble(configuration, "nutrition"),
					readDouble(configuration, "fullness"),
					readBoolean(configuration, "nutrition-frozen"),
					readBoolean(configuration, "fullness-frozen"),
					readLong(configuration, "last-seen")
			);
			default -> throw new IllegalArgumentException("Unsupported schema-version: " + schemaVersion);
		};
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

	private boolean prepareReadOnlyDirectory() throws PlayerDataStorageException {
		if (Files.notExists(dataDirectory)) {
			return false;
		}

		if (!Files.isDirectory(dataDirectory)) {
			throw new PlayerDataStorageException("Player data path is not a directory: " + dataDirectory);
		}
		return true;
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

	private UUID readPlayerId(Path playerFile) {
		String fileName = playerFile.getFileName().toString();
		if (!fileName.endsWith(".yml")) {
			return null;
		}

		try {
			return UUID.fromString(fileName.substring(0, fileName.length() - ".yml".length()));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private record PlayerFileMatch(UUID playerId, long lastSeen) {
	}
}
