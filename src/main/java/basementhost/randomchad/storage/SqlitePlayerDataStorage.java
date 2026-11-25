package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

// One local SQLite connection owned exclusively by the existing serial storage worker.
public final class SqlitePlayerDataStorage implements PlayerDataStorage {
	private static final int DATABASE_VERSION = 1;
	private static final int APPLICATION_ID = 0x484F5631;
	private static final String COLUMNS = "uuid, schema_version, last_known_name, name_key, last_known_group, "
			+ "max_nutrition, max_fullness, nutrition, fullness, nutrition_frozen, fullness_frozen, last_seen";
	private static final String INSERT = "INSERT INTO player_data (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";
	private static final String UPSERT = INSERT + "ON CONFLICT(uuid) DO UPDATE SET "
			+ "schema_version=excluded.schema_version, last_known_name=excluded.last_known_name, name_key=excluded.name_key, "
			+ "last_known_group=excluded.last_known_group, max_nutrition=excluded.max_nutrition, max_fullness=excluded.max_fullness, "
			+ "nutrition=excluded.nutrition, fullness=excluded.fullness, nutrition_frozen=excluded.nutrition_frozen, "
			+ "fullness_frozen=excluded.fullness_frozen, last_seen=excluded.last_seen";
	private static final String MIGRATION_KEY = "yaml-import-v1";

	private final Path databaseFile;
	private final Path yamlDirectory;
	private final boolean migrateYaml;
	private final Logger logger;
	private Connection connection;
	private Thread owner;

	public SqlitePlayerDataStorage(Path databaseFile, Path yamlDirectory, boolean migrateYaml, Logger logger) {
		this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile").toAbsolutePath().normalize();
		this.yamlDirectory = Objects.requireNonNull(yamlDirectory, "yamlDirectory").toAbsolutePath().normalize();
		this.migrateYaml = migrateYaml;
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	@Override
	public void initialize() throws PlayerDataStorageException {
		if (connection != null) {
			throw new PlayerDataStorageException("SQLite storage is already initialized");
		}
		try {
			Files.createDirectories(databaseFile.getParent());
			if (Files.exists(databaseFile) && !Files.isRegularFile(databaseFile)) {
				throw new IOException("Database path is not a regular file: " + databaseFile);
			}
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
			owner = Thread.currentThread();
			try (Statement sql = connection.createStatement()) {
				sql.execute("PRAGMA busy_timeout=5000");
				int version = pragmaInt("user_version");
				int application = pragmaInt("application_id");
				if (version == 0 && application == 0) {
					try (ResultSet tables = sql.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT GLOB 'sqlite_*' LIMIT 1")) {
						if (tables.next()) {
							throw new SQLException("Refusing to initialize an unrecognized non-empty database");
						}
					}
				} else if (version != DATABASE_VERSION || application != APPLICATION_ID) {
					throw new SQLException("Unsupported database application/schema: " + application + "/" + version);
				}
				sql.execute("PRAGMA journal_mode=WAL");
				sql.execute("PRAGMA synchronous=FULL");
				if (version == 0) {
					createSchema();
				}
				try (ResultSet result = sql.executeQuery("PRAGMA quick_check(1)")) {
					if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
						throw new SQLException("SQLite integrity check failed; the database will not be replaced");
					}
				}
				// Check expected columns even for an existing database claiming the current version.
				try (ResultSet ignored = sql.executeQuery("SELECT " + COLUMNS + " FROM player_data LIMIT 0")) {
					// No data is changed by schema validation.
				}
				try (ResultSet ignored = sql.executeQuery("SELECT key,value FROM storage_metadata LIMIT 0")) {
					// Migration metadata is part of this schema even when imports are disabled.
				}
			}
			if (migrateYaml) {
				migrateYamlOnce();
			} else {
				logger.info("SQLite YAML migration is disabled; original YAML files will not be imported.");
			}
			logger.info("SQLite storage initialized with JDBC " + connection.getMetaData().getDriverVersion() + " in " + databaseFile.getFileName());
		} catch (IOException | ClassNotFoundException | SQLException | PlayerDataStorageException | RuntimeException | LinkageError exception) {
			try {
				close();
			} catch (PlayerDataStorageException closeFailure) {
				exception.addSuppressed(closeFailure);
			}
			throw new PlayerDataStorageException("Could not initialize SQLite storage " + databaseFile + ". Original YAML and existing database records were not replaced.", exception);
		}
	}

	private int pragmaInt(String name) throws SQLException {
		try (Statement sql = connection.createStatement(); ResultSet result = sql.executeQuery("PRAGMA " + name)) {
			if (!result.next()) {
				throw new SQLException("Missing SQLite pragma result: " + name);
			}
			return result.getInt(1);
		}
	}

	private void createSchema() throws SQLException {
		connection.setAutoCommit(false);
		boolean finished = false;
		try (Statement sql = connection.createStatement()) {
			sql.execute("""
					CREATE TABLE player_data (
					  uuid TEXT PRIMARY KEY NOT NULL CHECK(length(uuid)=36),
					  schema_version INTEGER NOT NULL CHECK(schema_version IN (1,2)),
					  last_known_name TEXT NOT NULL CHECK(length(trim(last_known_name))>0),
					  name_key TEXT NOT NULL,
					  last_known_group TEXT,
					  max_nutrition REAL,
					  max_fullness REAL,
					  nutrition REAL NOT NULL CHECK(nutrition>=0),
					  fullness REAL NOT NULL CHECK(fullness>=0),
					  nutrition_frozen INTEGER NOT NULL CHECK(nutrition_frozen IN (0,1)),
					  fullness_frozen INTEGER NOT NULL CHECK(fullness_frozen IN (0,1)),
					  last_seen INTEGER NOT NULL CHECK(last_seen>=0),
					  CHECK((schema_version=1 AND last_known_group IS NULL AND max_nutrition IS NULL AND max_fullness IS NULL)
					     OR (schema_version=2 AND last_known_group IS NOT NULL AND max_nutrition IS NOT NULL AND max_fullness IS NOT NULL
					         AND max_nutrition>0 AND max_fullness>0 AND nutrition<=max_nutrition AND fullness<=max_fullness))
					)
					""");
			sql.execute("CREATE INDEX player_data_name ON player_data(name_key, last_seen DESC, uuid ASC)");
			sql.execute("CREATE TABLE storage_metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
			sql.execute("PRAGMA application_id=" + APPLICATION_ID);
			sql.execute("PRAGMA user_version=" + DATABASE_VERSION);
			connection.commit();
			finished = true;
		} catch (SQLException exception) {
			try {
				connection.rollback();
				finished = true;
			} catch (SQLException rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		} finally {
			if (finished) {
				connection.setAutoCommit(true);
			}
		}
	}

	@Override
	public Optional<StoredPlayerData> load(UUID playerId) throws PlayerDataStorageException {
		return loadReadOnly(playerId);
	}

	@Override
	public Optional<StoredPlayerData> loadReadOnly(UUID playerId) throws PlayerDataStorageException {
		try (PreparedStatement sql = currentConnection().prepareStatement("SELECT " + COLUMNS + " FROM player_data WHERE uuid=?")) {
			sql.setString(1, Objects.requireNonNull(playerId, "playerId").toString());
			try (ResultSet row = sql.executeQuery()) {
				return row.next() ? Optional.of(readRow(row)) : Optional.empty();
			}
		} catch (SQLException exception) {
			throw new PlayerDataStorageException("Could not read SQLite player data for " + playerId, exception);
		}
	}

	@Override
	public Optional<UUID> findPlayerIdByNameReadOnly(String playerName) throws PlayerDataStorageException {
		String name = Objects.requireNonNull(playerName, "playerName").trim();
		if (name.isEmpty()) {
			return Optional.empty();
		}
		try (PreparedStatement sql = currentConnection().prepareStatement("SELECT " + COLUMNS
				+ " FROM player_data WHERE name_key=? ORDER BY last_seen DESC, uuid ASC")) {
			sql.setString(1, name.toLowerCase(Locale.ROOT));
			try (ResultSet row = sql.executeQuery()) {
				while (row.next()) {
					try {
						StoredPlayerData stored = readRow(row);
						UUID id = UUID.fromString(row.getString("uuid"));
						if (id.toString().equalsIgnoreCase(row.getString("uuid")) && stored.getLastKnownName().equalsIgnoreCase(name)) {
							return Optional.of(id);
						}
					} catch (CorruptPlayerDataException | IllegalArgumentException ignored) {
						// An unrelated invalid record does not hide other valid matches.
					}
				}
			}
			return Optional.empty();
		} catch (SQLException exception) {
			throw new PlayerDataStorageException("Could not query SQLite player name", exception);
		}
	}

	@Override
	public void save(UUID playerId, StoredPlayerData data) throws PlayerDataStorageException {
		if (!Objects.requireNonNull(data, "playerData").hasGroupSnapshot()) {
			throw new PlayerDataStorageException("Schema-version 2 saves require a complete group snapshot");
		}
		try (PreparedStatement sql = currentConnection().prepareStatement(UPSERT)) {
			bind(sql, playerId, data);
			sql.executeUpdate();
		} catch (SQLException exception) {
			throw new PlayerDataStorageException("Could not save SQLite player data for " + playerId, exception);
		}
	}

	private void bind(PreparedStatement sql, UUID playerId, StoredPlayerData data) throws SQLException {
		sql.setString(1, Objects.requireNonNull(playerId, "playerId").toString());
		sql.setInt(2, data.hasGroupSnapshot() ? 2 : 1);
		sql.setString(3, data.getLastKnownName());
		sql.setString(4, data.getLastKnownName().toLowerCase(Locale.ROOT));
		if (data.hasGroupSnapshot()) {
			sql.setString(5, data.getLastKnownGroupId().orElseThrow());
			sql.setDouble(6, data.getMaxNutrition().orElseThrow());
			sql.setDouble(7, data.getMaxFullness().orElseThrow());
		} else {
			sql.setNull(5, Types.VARCHAR);
			sql.setNull(6, Types.DOUBLE);
			sql.setNull(7, Types.DOUBLE);
		}
		sql.setDouble(8, data.getNutrition());
		sql.setDouble(9, data.getFullness());
		sql.setInt(10, data.isNutritionFrozen() ? 1 : 0);
		sql.setInt(11, data.isFullnessFrozen() ? 1 : 0);
		sql.setLong(12, data.getLastSeen());
	}

	private StoredPlayerData readRow(ResultSet row) throws SQLException, CorruptPlayerDataException {
		try {
			int version = Math.toIntExact(wholeNumber(row, "schema_version"));
			String name = row.getString("last_known_name");
			double nutrition = number(row, "nutrition");
			double fullness = number(row, "fullness");
			boolean nutritionFrozen = flag(row, "nutrition_frozen");
			boolean fullnessFrozen = flag(row, "fullness_frozen");
			long lastSeen = wholeNumber(row, "last_seen");
			return switch (version) {
				case 1 -> new StoredPlayerData(name, nutrition, fullness, nutritionFrozen, fullnessFrozen, lastSeen);
				case 2 -> new StoredPlayerData(name, row.getString("last_known_group"), number(row, "max_nutrition"),
						number(row, "max_fullness"), nutrition, fullness, nutritionFrozen, fullnessFrozen, lastSeen);
				default -> throw new IllegalArgumentException("Unsupported player record version: " + version);
			};
		} catch (IllegalArgumentException | ArithmeticException | NullPointerException exception) {
			throw new CorruptPlayerDataException("Invalid SQLite player record", exception);
		}
	}

	private double number(ResultSet row, String column) throws SQLException {
		if (row.getObject(column) instanceof Number value) {
			return value.doubleValue();
		}
		throw new IllegalArgumentException(column + " must be numeric");
	}

	private long wholeNumber(ResultSet row, String column) throws SQLException {
		Object value = row.getObject(column);
		if (!(value instanceof Number)) {
			throw new IllegalArgumentException(column + " must be a whole number");
		}
		return new BigDecimal(value.toString()).longValueExact();
	}

	private boolean flag(ResultSet row, String column) throws SQLException {
		long value = wholeNumber(row, column);
		if (value != 0 && value != 1) {
			throw new IllegalArgumentException(column + " must be 0 or 1");
		}
		return value == 1;
	}

	private void migrateYamlOnce() throws SQLException, PlayerDataStorageException, IOException {
		try (PreparedStatement sql = connection.prepareStatement("SELECT value FROM storage_metadata WHERE key=?")) {
			sql.setString(1, MIGRATION_KEY);
			try (ResultSet row = sql.executeQuery()) {
				if (row.next()) {
					logger.info("SQLite YAML migration was already completed; original YAML will not be reimported.");
					return;
				}
			}
		}
		if (Files.exists(yamlDirectory) && !Files.isDirectory(yamlDirectory)) {
			throw new IOException("YAML migration source is not a directory: " + yamlDirectory);
		}
		int imported = 0;
		int skipped = 0;
		logger.info("Starting one-time YAML-to-SQLite migration; existing database records will be kept.");
		FilePlayerDataStorage yaml = new FilePlayerDataStorage(yamlDirectory);
		connection.setAutoCommit(false);
		boolean finished = false;
		try {
			if (Files.isDirectory(yamlDirectory)) {
				try (Stream<Path> paths = Files.list(yamlDirectory);
					 PreparedStatement insert = connection.prepareStatement(INSERT + "ON CONFLICT(uuid) DO NOTHING")) {
					var iterator = paths.filter(Files::isRegularFile).sorted(Comparator.comparing(path -> path.getFileName().toString())).iterator();
					while (iterator.hasNext()) {
						if (Thread.currentThread().isInterrupted()) {
							throw new PlayerDataStorageException("YAML migration was interrupted; import will be rolled back");
						}
						Path file = iterator.next();
						UUID id = migrationPlayerId(file);
						if (id == null) {
							continue;
						}
						if (loadReadOnly(id).isPresent()) {
							skipped++;
							continue;
						}
						bind(insert, id, yaml.readMigrationFile(file));
						imported += insert.executeUpdate();
					}
				}
			}
			try (PreparedStatement marker = connection.prepareStatement("INSERT INTO storage_metadata(key,value) VALUES (?,?)")) {
				marker.setString(1, MIGRATION_KEY);
				marker.setString(2, Instant.now() + "; imported=" + imported + "; existing=" + skipped);
				marker.executeUpdate();
			}
			connection.commit();
			finished = true;
			logger.info("SQLite YAML migration completed: imported " + imported + ", kept existing " + skipped + ". Original YAML files were not changed.");
		} catch (SQLException | IOException | PlayerDataStorageException | RuntimeException exception) {
			try {
				connection.rollback();
				finished = true;
			} catch (SQLException rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		} finally {
			if (finished) {
				connection.setAutoCommit(true);
			}
		}
	}

	private UUID migrationPlayerId(Path file) {
		String name = file.getFileName().toString();
		if (!name.endsWith(".yml")) {
			return null;
		}
		String text = name.substring(0, name.length() - 4);
		try {
			UUID id = UUID.fromString(text);
			return id.toString().equalsIgnoreCase(text) ? id : null;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private Connection currentConnection() throws PlayerDataStorageException {
		if (connection == null || owner != Thread.currentThread()) {
			throw new PlayerDataStorageException("SQLite must be initialized and accessed on its owning storage worker");
		}
		return connection;
	}

	@Override
	public void close() throws PlayerDataStorageException {
		if (connection == null) {
			return;
		}
		Connection current = currentConnection();
		try {
			current.close();
		} catch (SQLException exception) {
			throw new PlayerDataStorageException("Could not close SQLite connection", exception);
		} finally {
			connection = null;
			owner = null;
		}
	}
}
