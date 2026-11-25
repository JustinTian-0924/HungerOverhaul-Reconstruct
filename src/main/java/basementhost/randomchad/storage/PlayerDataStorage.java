package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;

import java.util.Optional;
import java.util.UUID;

public interface PlayerDataStorage extends AutoCloseable {
	// Initialization and cleanup run on the same serial storage worker as reads and writes.
	default void initialize() throws PlayerDataStorageException {
	}

	@Override
	default void close() throws PlayerDataStorageException {
	}

	Optional<StoredPlayerData> load(UUID playerId) throws PlayerDataStorageException;

	// Reads an existing record without creating directories, files, or replacement data.
	Optional<StoredPlayerData> loadReadOnly(UUID playerId) throws PlayerDataStorageException;

	// Resolves the newest exact last-known-name match without changing storage.
	Optional<UUID> findPlayerIdByNameReadOnly(String playerName) throws PlayerDataStorageException;

	void save(UUID playerId, StoredPlayerData playerData) throws PlayerDataStorageException;
}
