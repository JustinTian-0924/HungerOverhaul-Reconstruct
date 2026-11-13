package basementhost.randomchad.compat.placeholderapi;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.PerformanceSettings;
import basementhost.randomchad.data.StatusEffectDefinition;
import basementhost.randomchad.lang.LanguageManager;
import basementhost.randomchad.manager.StatusEffectManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Publishes main-thread snapshots for both synchronous and asynchronous placeholder reads.
public final class PlaceholderApiHook implements Listener {
	private static final long SNAPSHOT_INTERVAL_TICKS = 5L;
	private static final Map<String, Function<PlayerSnapshot, String>> PLACEHOLDERS = Map.of(
			"nutrition", snapshot -> formatValue(snapshot.nutrition()),
			"fullness", snapshot -> formatValue(snapshot.fullness()),
			"nutrition_max", snapshot -> formatValue(snapshot.maxNutrition()),
			"fullness_max", snapshot -> formatValue(snapshot.maxFullness()),
			"nutrition_percent", snapshot -> formatPercentage(snapshot.nutritionPercentage()),
			"fullness_percent", snapshot -> formatPercentage(snapshot.fullnessPercentage()),
			"nutrition_status", PlayerSnapshot::nutritionStatus,
			"fullness_status", PlayerSnapshot::fullnessStatus
	);

	private final HungerOverhaul plugin;
	private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
	private final Expansion expansion;
	private final Map<StatusNameKey, String> statusNames = new HashMap<>();
	private PerformanceSettings performanceSettings;
	private BukkitTask snapshotTask;
	private boolean registered;

	public PlaceholderApiHook(HungerOverhaul plugin, PerformanceSettings performanceSettings) {
		this.plugin = plugin;
		this.performanceSettings = Objects.requireNonNull(performanceSettings, "performanceSettings");
		this.expansion = new Expansion();
	}

	public void updatePerformanceSettings(PerformanceSettings settings) {
		performanceSettings = Objects.requireNonNull(settings, "settings");
		statusNames.clear();
		if (registered) {
			// Replace snapshots in place, avoiding a transient empty map for async readers.
			publishSnapshots(true);
		}
	}

	public void start() {
		registered = expansion.register();
		if (!registered) {
			plugin.getLogger().warning("Could not register the hungeroverhaul expansion with PlaceholderAPI.");
			return;
		}

		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		refreshSnapshots();
		snapshotTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin, this::refreshSnapshots, SNAPSHOT_INTERVAL_TICKS, SNAPSHOT_INTERVAL_TICKS
		);
	}

	public void stop() {
		if (snapshotTask != null) {
			snapshotTask.cancel();
			snapshotTask = null;
		}
		HandlerList.unregisterAll(this);
		snapshots.clear();
		statusNames.clear();

		if (registered && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			expansion.unregister();
		}
		registered = false;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		snapshots.remove(event.getPlayer().getUniqueId());
	}

	private void refreshSnapshots() {
		publishSnapshots(false);
	}

	private void publishSnapshots(boolean force) {
		StatusEffectManager statusEffectManager = plugin.getStatusEffectManager();
		LanguageManager languageManager = plugin.getLanguageManager();
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			UUID playerId = player.getUniqueId();
			PlayerData playerData = plugin.getPlayerDataManager().findPlayerData(playerId).orElse(null);
			if (playerData == null || plugin.getPersistenceManager().isPlayerDataLoading(playerId)) {
				snapshots.remove(playerId);
				continue;
			}

			if (!force && performanceSettings.reusePlaceholderSnapshots()) {
				PlayerSnapshot previous = snapshots.get(playerId);
				if (previous != null && previous.matches(playerData)) {
					continue;
				}
			}

			double nutritionPercentage = playerData.getNutritionPercentage();
			double fullnessPercentage = playerData.getFullnessPercentage();
			snapshots.put(playerId, new PlayerSnapshot(
					playerData.getNutrition(),
					playerData.getFullness(),
					playerData.getMaxNutrition(),
					playerData.getMaxFullness(),
					nutritionPercentage,
					fullnessPercentage,
					getStatusName(languageManager, "nutrition", statusEffectManager.getNutritionEffect(nutritionPercentage)),
					getStatusName(languageManager, "fullness", statusEffectManager.getFullnessEffect(fullnessPercentage))
			));
		}
	}

	private String getStatusName(LanguageManager languageManager, String type, StatusEffectDefinition effect) {
		String effectId = effect == null ? "normal" : effect.getId();
		if (!performanceSettings.cachePlaceholderStatusNames()) {
			return languageManager.getTextOrDefault("status-effect." + type + "." + effectId, effectId);
		}
		return statusNames.computeIfAbsent(new StatusNameKey(type, effectId),
				key -> languageManager.getTextOrDefault("status-effect." + key.type() + "." + key.effectId(), key.effectId()));
	}

	private static String formatValue(double value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}

	private static String formatPercentage(double percentage) {
		return String.format(Locale.ROOT, "%.1f", percentage);
	}

	private final class Expansion extends PlaceholderExpansion {
		@Override
		public @NotNull String getIdentifier() {
			return "hungeroverhaul";
		}

		@Override
		public @NotNull String getAuthor() {
			return "Random_Chad";
		}

		@Override
		public @NotNull String getVersion() {
			return plugin.getPluginMeta().getVersion();
		}

		@Override
		public boolean persist() {
			return true;
		}

		@Override
		public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
			Function<PlayerSnapshot, String> reader = PLACEHOLDERS.get(params.toLowerCase(Locale.ROOT));
			if (reader == null) {
				return null;
			}

			PlayerSnapshot snapshot = player == null ? null : snapshots.get(player.getUniqueId());
			return snapshot == null ? "" : reader.apply(snapshot);
		}
	}

	private record PlayerSnapshot(
			double nutrition,
			double fullness,
			double maxNutrition,
			double maxFullness,
			double nutritionPercentage,
			double fullnessPercentage,
			String nutritionStatus,
			String fullnessStatus
	) {
		private boolean matches(PlayerData data) {
			return Double.compare(nutrition, data.getNutrition()) == 0 && Double.compare(fullness, data.getFullness()) == 0
					&& Double.compare(maxNutrition, data.getMaxNutrition()) == 0 && Double.compare(maxFullness, data.getMaxFullness()) == 0;
		}
	}

	private record StatusNameKey(String type, String effectId) {
	}
}
