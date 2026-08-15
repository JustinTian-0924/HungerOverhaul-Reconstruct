package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;

import java.util.Optional;
import java.util.UUID;

public interface PlayerDataStorage {
	Optional<StoredPlayerData> load(UUID playerId) throws PlayerDataStorageException;

	// Reads an existing file without creating directories, files, or replacement data.
	Optional<StoredPlayerData> loadReadOnly(UUID playerId) throws PlayerDataStorageException;

	// Resolves the newest exact last-known-name match without changing storage.
	Optional<UUID> findPlayerIdByNameReadOnly(String playerName) throws PlayerDataStorageException;

	void save(UUID playerId, StoredPlayerData playerData) throws PlayerDataStorageException;
}
