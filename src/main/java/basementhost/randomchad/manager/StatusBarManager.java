package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StatusBarSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Shares the action bar between the status display and higher-priority eating feedback.
public final class StatusBarManager {
	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final Set<UUID> showingStatus = new HashSet<>();
	private final Map<UUID, Integer> resultHoldUntil = new HashMap<>();
	private BukkitTask refreshTask;

	public StatusBarManager(HungerOverhaul plugin, PlayerDataManager playerDataManager) {
		this.plugin = plugin;
		this.playerDataManager = playerDataManager;
	}

	public void updateSettings(StatusBarSettings settings, boolean eatingFeedbackEnabled) {
		cancelRefreshTask();
		if (!eatingFeedbackEnabled) {
			resultHoldUntil.clear();
		}
		if (!settings.enabled()) {
			clearStatusBars();
			return;
		}

		refreshTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::refreshOnlinePlayers,
				0L,
				settings.updateIntervalTicks()
		);
	}

	public void showEatingFeedback(Player player, Component message, int holdTicks) {
		UUID playerId = player.getUniqueId();
		showingStatus.remove(playerId);
		if (holdTicks > 0) {
			resultHoldUntil.put(playerId, plugin.getServer().getCurrentTick() + holdTicks);
		} else {
			resultHoldUntil.remove(playerId);
		}
		player.sendActionBar(message);
	}

	public void removePlayer(UUID playerId) {
		showingStatus.remove(playerId);
		resultHoldUntil.remove(playerId);
	}

	public void shutdown() {
		cancelRefreshTask();
		clearStatusBars();
		resultHoldUntil.clear();
	}

	private void refreshOnlinePlayers() {
		int currentTick = plugin.getServer().getCurrentTick();
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			UUID playerId = player.getUniqueId();
			if (plugin.getEatingManager().isProgressFeedbackActive(playerId) || hasResultHold(playerId, currentTick)) {
				continue;
			}

			PlayerData playerData = playerDataManager.findPlayerData(playerId).orElse(null);
			if (playerData == null) {
				continue;
			}

			player.sendActionBar(plugin.getLanguageManager().getMessage("actionbar.format", Map.of(
					"nutrition", formatValue(playerData.getNutrition()),
					"max_nutrition", formatValue(playerData.getMaxNutrition()),
					"fullness", formatValue(playerData.getFullness()),
					"max_fullness", formatValue(playerData.getMaxFullness())
			)));
			showingStatus.add(playerId);
		}
	}

	private boolean hasResultHold(UUID playerId, int currentTick) {
		Integer holdUntil = resultHoldUntil.get(playerId);
		if (holdUntil == null) {
			return false;
		}
		// Compare the difference so a short hold also works across the server tick counter wrap.
		if (currentTick - holdUntil < 0) {
			return true;
		}
		resultHoldUntil.remove(playerId);
		return false;
	}

	private void clearStatusBars() {
		for (UUID playerId : showingStatus) {
			Player player = plugin.getServer().getPlayer(playerId);
			if (player != null && player.isOnline()) {
				player.sendActionBar(Component.empty());
			}
		}
		showingStatus.clear();
	}

	private void cancelRefreshTask() {
		if (refreshTask != null) {
			refreshTask.cancel();
			refreshTask = null;
		}
	}

	private static String formatValue(double value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}
}
