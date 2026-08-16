package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Reports the outcome of one serialized offline read-modify-write operation.
public final class OfflinePlayerDataUpdateResult {
	public enum Status {
		UPDATED,
		NOT_FOUND,
		CORRUPT,
		READ_FAILED,
		WRITE_FAILED,
		UNAVAILABLE
	}

	private final Status status;
	private final UUID playerId;
	private final StoredPlayerData playerData;

	private OfflinePlayerDataUpdateResult(Status status, UUID playerId, StoredPlayerData playerData) {
		this.status = Objects.requireNonNull(status, "status");
		this.playerId = playerId;
		this.playerData = playerData;
	}

	public static OfflinePlayerDataUpdateResult updated(UUID playerId, StoredPlayerData playerData) {
		return new OfflinePlayerDataUpdateResult(
				Status.UPDATED,
				Objects.requireNonNull(playerId, "playerId"),
				Objects.requireNonNull(playerData, "playerData")
		);
	}

	public static OfflinePlayerDataUpdateResult of(Status status) {
		if (status == Status.UPDATED) {
			throw new IllegalArgumentException("UPDATED requires player data");
		}
		return new OfflinePlayerDataUpdateResult(status, null, null);
	}

	public Status getStatus() {
		return status;
	}

	public Optional<UUID> getPlayerId() {
		return Optional.ofNullable(playerId);
	}

	public Optional<StoredPlayerData> getPlayerData() {
		return Optional.ofNullable(playerData);
	}
}
