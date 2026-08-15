package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;

import java.util.Optional;
import java.util.UUID;

public interface PlayerDataStorage {
	Optional<StoredPlayerData> load(UUID playerId) throws PlayerDataStorageException;

	void save(UUID playerId, StoredPlayerData playerData) throws PlayerDataStorageException;
}
