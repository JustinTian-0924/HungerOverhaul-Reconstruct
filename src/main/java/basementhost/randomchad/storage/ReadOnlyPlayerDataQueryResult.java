package basementhost.randomchad.storage;

import basementhost.randomchad.data.StoredPlayerData;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Describes a read-only lookup without turning failures into writable recovery actions.
public final class ReadOnlyPlayerDataQueryResult {
	public enum Status {
		FOUND,
		NOT_FOUND,
		CORRUPT,
		FAILED,
		UNAVAILABLE
	}

	private final Status status;
	private final UUID playerId;
	private final StoredPlayerData playerData;

	private ReadOnlyPlayerDataQueryResult(Status status, UUID playerId, StoredPlayerData playerData) {
		this.status = Objects.requireNonNull(status, "status");
		this.playerId = playerId;
		this.playerData = playerData;
	}

	public static ReadOnlyPlayerDataQueryResult found(UUID playerId, StoredPlayerData playerData) {
		return new ReadOnlyPlayerDataQueryResult(
				Status.FOUND,
				Objects.requireNonNull(playerId, "playerId"),
				Objects.requireNonNull(playerData, "playerData")
		);
	}

	public static ReadOnlyPlayerDataQueryResult of(Status status) {
		if (status == Status.FOUND) {
			throw new IllegalArgumentException("FOUND requires player data");
		}
		return new ReadOnlyPlayerDataQueryResult(status, null, null);
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
