package basementhost.randomchad.manager;

import basementhost.randomchad.data.GroupSettings;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StoredPlayerData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Keeps player hunger data in memory while the server is running.
public final class PlayerDataManager {
	private final GroupManager groupManager;
	private final Map<UUID, PlayerData> playerDataById = new HashMap<>();

	public PlayerDataManager(GroupManager groupManager) {
		this.groupManager = Objects.requireNonNull(groupManager, "groupManager");
	}

	public PlayerData getOrCreatePlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		return playerDataById.computeIfAbsent(
				player.getUniqueId(),
				playerId -> createPlayerData(playerId, player)
		);
	}

	public PlayerData restorePlayerData(Player player, StoredPlayerData storedPlayerData) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(storedPlayerData, "storedPlayerData");

		return playerDataById.computeIfAbsent(
				player.getUniqueId(),
				playerId -> createPlayerData(playerId, player, storedPlayerData)
		);
	}

	public Optional<PlayerData> findPlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		return findPlayerData(player.getUniqueId());
	}

	public Optional<PlayerData> findPlayerData(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		return Optional.ofNullable(playerDataById.get(playerId));
	}

	public boolean hasPlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		return hasPlayerData(player.getUniqueId());
	}

	public boolean hasPlayerData(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		return playerDataById.containsKey(playerId);
	}

	public boolean unloadPlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		return unloadPlayerData(player.getUniqueId());
	}

	public boolean unloadPlayerData(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		return playerDataById.remove(playerId) != null;
	}

	public void unloadAllPlayerData() {
		playerDataById.clear();
	}

	public int getLoadedPlayerCount() {
		return playerDataById.size();
	}

	public Collection<PlayerData> getLoadedPlayerData() {
		return List.copyOf(playerDataById.values());
	}

	// Returns false when the player does not currently have data in memory.
	public boolean refreshGroupSettings(Player player) {
		Objects.requireNonNull(player, "player");

		PlayerData playerData = playerDataById.get(player.getUniqueId());
		if (playerData == null) {
			return false;
		}

		GroupSettings settings = groupManager.getSettings(player);
		playerData.setMaxNutrition(settings.getMaxNutrition());
		playerData.setMaxFullness(settings.getMaxFullness());
		return true;
	}

	private PlayerData createPlayerData(UUID playerId, Player player) {
		GroupSettings settings = groupManager.getSettings(player);
		return new PlayerData(
				playerId,
				settings.getMaxNutrition(),
				settings.getMaxFullness(),
				settings.getInitialNutrition(),
				settings.getInitialFullness()
		);
	}

	private PlayerData createPlayerData(UUID playerId, Player player, StoredPlayerData storedPlayerData) {
		GroupSettings settings = groupManager.getSettings(player);
		PlayerData playerData = new PlayerData(
				playerId,
				settings.getMaxNutrition(),
				settings.getMaxFullness(),
				storedPlayerData.getNutrition(),
				storedPlayerData.getFullness()
		);
		playerData.setNutritionFrozen(storedPlayerData.isNutritionFrozen());
		playerData.setFullnessFrozen(storedPlayerData.isFullnessFrozen());
		return playerData;
	}
}
