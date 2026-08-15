package basementhost.randomchad.data;

// Holds validated settings for player data saving and memory caching.
public final class StorageSettings {
	private static final long MAX_SCHEDULER_SECONDS = Math.min(Long.MAX_VALUE / 20L, Long.MAX_VALUE / 1000L);

	private final boolean autosaveEnabled;
	private final long autosaveIntervalSeconds;
	private final long cacheTtlSeconds;

	public StorageSettings(
			boolean autosaveEnabled,
			long autosaveIntervalSeconds,
			long cacheTtlSeconds
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
}
