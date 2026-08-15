package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.LowValueWarningSettings;
import basementhost.randomchad.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Warns eligible online players when nutrition or fullness is low.
public final class LowValueWarningManager {
	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final Map<UUID, Long> lastNutritionWarnings = new HashMap<>();
	private final Map<UUID, Long> lastFullnessWarnings = new HashMap<>();

	private LowValueWarningSettings settings;
	private BukkitTask warningTask;
	private boolean started;

	public LowValueWarningManager(
			HungerOverhaul plugin,
			PlayerDataManager playerDataManager,
			LowValueWarningSettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("LowValueWarningManager has already started");
		}

		started = true;
		scheduleWarningTask();
	}

	public void updateSettings(LowValueWarningSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		clearWarningHistory();
		if (started) {
			scheduleWarningTask();
		}
	}

	public void shutdown() {
		started = false;
		cancelWarningTask();
		clearWarningHistory();
	}

	private void scheduleWarningTask() {
		cancelWarningTask();
		if (!settings.isEnabled()) {
			return;
		}

		long intervalTicks = settings.getCheckIntervalTicks();
		warningTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::checkOnlinePlayers,
				intervalTicks,
				intervalTicks
		);
	}

	private void cancelWarningTask() {
		if (warningTask == null) {
			return;
		}

		warningTask.cancel();
		warningTask = null;
	}

	private void checkOnlinePlayers() {
		long currentTime = System.nanoTime();
		Set<UUID> onlinePlayerIds = new HashSet<>();
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			onlinePlayerIds.add(player.getUniqueId());
			checkPlayer(player, currentTime);
		}

		lastNutritionWarnings.keySet().retainAll(onlinePlayerIds);
		lastFullnessWarnings.keySet().retainAll(onlinePlayerIds);
	}

	private void checkPlayer(Player player, long currentTime) {
		UUID playerId = player.getUniqueId();
		if (player.isDead() || !isAffectedGameMode(player.getGameMode())) {
			clearPlayerWarningHistory(playerId);
			return;
		}

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			clearPlayerWarningHistory(playerId);
			return;
		}

		checkNutrition(player, playerData, currentTime);
		checkFullness(player, playerData, currentTime);
	}

	private void checkNutrition(Player player, PlayerData playerData, long currentTime) {
		double percentage = playerData.getNutritionPercentage();
		if (!settings.isNutritionWarningEnabled()
				|| !isLowValue(percentage, settings.getNutritionThresholdPercentage())) {
			lastNutritionWarnings.remove(player.getUniqueId());
			return;
		}

		if (isWarningReady(lastNutritionWarnings, player.getUniqueId(), currentTime)) {
			player.sendMessage(plugin.getLanguageManager().getMessage(
					"warning.low-nutrition",
					createWarningPlaceholders(percentage, settings.getNutritionThresholdPercentage())
			));
		}
	}

	private void checkFullness(Player player, PlayerData playerData, long currentTime) {
		double percentage = playerData.getFullnessPercentage();
		if (!settings.isFullnessWarningEnabled()
				|| !isLowValue(percentage, settings.getFullnessThresholdPercentage())) {
			lastFullnessWarnings.remove(player.getUniqueId());
			return;
		}

		if (isWarningReady(lastFullnessWarnings, player.getUniqueId(), currentTime)) {
			player.sendMessage(plugin.getLanguageManager().getMessage(
					"warning.low-fullness",
					createWarningPlaceholders(percentage, settings.getFullnessThresholdPercentage())
			));
		}
	}

	private boolean isLowValue(double percentage, double thresholdPercentage) {
		return percentage > 0.0 && percentage < thresholdPercentage;
	}

	private boolean isWarningReady(Map<UUID, Long> warningHistory, UUID playerId, long currentTime) {
		Long lastWarningTime = warningHistory.get(playerId);
		if (lastWarningTime != null && currentTime - lastWarningTime < settings.getRepeatIntervalNanos()) {
			return false;
		}

		warningHistory.put(playerId, currentTime);
		return true;
	}

	private Map<String, Object> createWarningPlaceholders(double percentage, double thresholdPercentage) {
		return Map.of(
				"percentage", formatPercentage(percentage),
				"threshold", formatPercentage(thresholdPercentage)
		);
	}

	private String formatPercentage(double percentage) {
		return String.format(Locale.ROOT, "%.1f", percentage);
	}

	private void clearPlayerWarningHistory(UUID playerId) {
		lastNutritionWarnings.remove(playerId);
		lastFullnessWarnings.remove(playerId);
	}

	private void clearWarningHistory() {
		lastNutritionWarnings.clear();
		lastFullnessWarnings.clear();
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}
}
