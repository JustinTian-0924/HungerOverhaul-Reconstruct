package basementhost.randomchad.data;

import java.util.Objects;

// Holds validated settings for player data saving and memory caching.
public final class StorageSettings {
	private static final long MAX_SCHEDULER_SECONDS = Math.min(Long.MAX_VALUE / 20L, Long.MAX_VALUE / 1000L);

	private final boolean autosaveEnabled;
	private final long autosaveIntervalSeconds;
	private final long cacheTtlSeconds;
	private final StorageType type;
	private final boolean migrateYamlOnStartup;

	public StorageSettings(
			boolean autosaveEnabled,
			long autosaveIntervalSeconds,
			long cacheTtlSeconds
	) {
		this(autosaveEnabled, autosaveIntervalSeconds, cacheTtlSeconds, StorageType.YAML, true);
	}

	public StorageSettings(
			boolean autosaveEnabled,
			long autosaveIntervalSeconds,
			long cacheTtlSeconds,
			StorageType type,
			boolean migrateYamlOnStartup
	) {
		if (autosaveIntervalSeconds <= 0L) {
			throw new IllegalArgumentException("autosaveIntervalSeconds must be greater than zero");
		}

		if (autosaveIntervalSeconds > MAX_SCHEDULER_SECONDS) {
			throw new IllegalArgumentException("autosaveIntervalSeconds is too large");
		}

		if (cacheTtlSeconds < 0L) {
			throw new IllegalArgumentException("cacheTtlSeconds must not be negative");
		}

		if (cacheTtlSeconds > MAX_SCHEDULER_SECONDS) {
			throw new IllegalArgumentException("cacheTtlSeconds is too large");
		}

		this.autosaveEnabled = autosaveEnabled;
		this.autosaveIntervalSeconds = autosaveIntervalSeconds;
		this.cacheTtlSeconds = cacheTtlSeconds;
		this.type = Objects.requireNonNull(type, "type");
		this.migrateYamlOnStartup = migrateYamlOnStartup;
	}

	public boolean isAutosaveEnabled() {
		return autosaveEnabled;
	}

	public long getAutosaveIntervalSeconds() {
		return autosaveIntervalSeconds;
	}

	public long getCacheTtlSeconds() {
		return cacheTtlSeconds;
	}

	public StorageType getType() {
		return type;
	}

	public boolean isMigrateYamlOnStartup() {
		return migrateYamlOnStartup;
	}
}
