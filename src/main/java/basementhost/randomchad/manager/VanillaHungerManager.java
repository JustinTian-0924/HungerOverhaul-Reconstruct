package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.VanillaHungerSettings;
import basementhost.randomchad.listener.OnlinePlayerRoster;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

// Uses the vanilla hunger bar only as a display for HungerOverhaul data.
public final class VanillaHungerManager {
	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final OnlinePlayerRoster onlinePlayerRoster;
	private final Consumer<Player> playerUpdater = this::updatePlayer;
	private final Set<UUID> allowedPluginStarvationDamage = new HashSet<>();

	private VanillaHungerSettings settings;
	private BukkitTask displayTask;
	private boolean started;

	public VanillaHungerManager(
			HungerOverhaul plugin,
			PlayerDataManager playerDataManager,
			VanillaHungerSettings settings,
			OnlinePlayerRoster onlinePlayerRoster
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.onlinePlayerRoster = Objects.requireNonNull(onlinePlayerRoster, "onlinePlayerRoster");
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("VanillaHungerManager has already started");
		}

		started = true;
		scheduleDisplayTask(0L);
	}

	private void scheduleDisplayTask(long delayTicks) {
		displayTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::updateOnlinePlayers,
				delayTicks,
				settings.getUpdateIntervalTicks()
		);
	}

	public void updateSettings(VanillaHungerSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		if (!started) {
			return;
		}
		cancelDisplayTask();
		// Reflect source/range changes immediately, then resume using the new interval.
		updateOnlinePlayers();
		scheduleDisplayTask(settings.getUpdateIntervalTicks());
	}

	public boolean isManaging(Player player) {
		Objects.requireNonNull(player, "player");
		return playerDataManager.hasPlayerData(player);
	}

	public boolean isNaturalRegenerationBlocked() {
		return settings.isNaturalRegenerationBlocked();
	}

	public void applyPluginStarvationDamage(Player player, double amount, DamageSource damageSource) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(damageSource, "damageSource");
		UUID playerId = player.getUniqueId();
		allowedPluginStarvationDamage.add(playerId);
		try {
			player.damage(amount, damageSource);
		} finally {
			allowedPluginStarvationDamage.remove(playerId);
		}
	}

	public boolean isPluginStarvationDamage(Player player) {
		Objects.requireNonNull(player, "player");
		return allowedPluginStarvationDamage.contains(player.getUniqueId());
	}

	public void shutdown() {
		started = false;
		allowedPluginStarvationDamage.clear();
		cancelDisplayTask();
	}

	private void cancelDisplayTask() {
		if (displayTask == null) {
			return;
		}

		displayTask.cancel();
		displayTask = null;
	}

	private void updateOnlinePlayers() {
		onlinePlayerRoster.forEachOnlinePlayer(playerUpdater);
	}

	private void updatePlayer(Player player) {
		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		double percentage = switch (settings.getDisplaySource()) {
			case FULLNESS -> playerData.getFullnessPercentage();
			case NUTRITION -> playerData.getNutritionPercentage();
		};
		int foodLevel = mapPercentageToFoodLevel(percentage);
		boolean skipUnchanged = settings.isSkipUnchangedWrites();
		// Compare real server values, not a cached last write, so external changes are corrected.
		if (!skipUnchanged || player.getFoodLevel() != foodLevel) {
			player.setFoodLevel(foodLevel);
		}
		if (!skipUnchanged || Float.compare(player.getSaturation(), 0.0F) != 0) {
			player.setSaturation(0.0F);
		}
		if (!skipUnchanged || Float.compare(player.getExhaustion(), 0.0F) != 0) {
			player.setExhaustion(0.0F);
		}
	}

	private int mapPercentageToFoodLevel(double percentage) {
		double normalizedPercentage = Math.max(0.0, Math.min(100.0, percentage)) / 100.0;
		int minimum = settings.getMinimumFoodLevel();
		int maximum = settings.getMaximumFoodLevel();
		return minimum + (int) Math.round(normalizedPercentage * (maximum - minimum));
	}
}
