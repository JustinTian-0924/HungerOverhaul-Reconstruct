package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.DecaySettings;
import basementhost.randomchad.data.GroupSettings;
import basementhost.randomchad.data.MovementDecaySettings;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StatusEffectDefinition;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

// Applies passive nutrition and fullness decay to loaded online players.
public final class ValueDecayManager {
	private static final double TICKS_PER_SECOND = 20.0;

	private final HungerOverhaul plugin;
	private final GroupManager groupManager;
	private final PlayerDataManager playerDataManager;
	private final StatusEffectManager statusEffectManager;

	private DecaySettings settings;
	private BukkitTask decayTask;
	private boolean started;

	public ValueDecayManager(
			HungerOverhaul plugin,
			GroupManager groupManager,
			PlayerDataManager playerDataManager,
			StatusEffectManager statusEffectManager,
			DecaySettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.groupManager = Objects.requireNonNull(groupManager, "groupManager");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.statusEffectManager = Objects.requireNonNull(statusEffectManager, "statusEffectManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("ValueDecayManager has already started");
		}

		started = true;
		scheduleDecayTask();
	}

	public void updateSettings(DecaySettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		if (started) {
			scheduleDecayTask();
		}
	}

	public void shutdown() {
		started = false;
		cancelDecayTask();
	}

	private void scheduleDecayTask() {
		cancelDecayTask();
		if (!settings.isEnabled()) {
			return;
		}

		long intervalTicks = settings.getIntervalTicks();
		decayTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::decayOnlinePlayers,
				intervalTicks,
				intervalTicks
		);
	}

	private void cancelDecayTask() {
		if (decayTask == null) {
			return;
		}

		decayTask.cancel();
		decayTask = null;
	}

	private void decayOnlinePlayers() {
		double elapsedSeconds = settings.getIntervalTicks() / TICKS_PER_SECOND;
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			decayPlayer(player, elapsedSeconds);
		}
	}

	private void decayPlayer(Player player, double elapsedSeconds) {
		if (player.isDead() || !isAffectedGameMode(player.getGameMode())) {
			return;
		}

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		GroupSettings groupSettings = groupManager.getSettings(player);
		MovementDecaySettings movementSettings = groupSettings.getMovementDecaySettings();
		StatusEffectDefinition nutritionEffect = statusEffectManager.getNutritionEffect(
				playerData.getNutritionPercentage()
		);
		StatusEffectDefinition fullnessEffect = statusEffectManager.getFullnessEffect(
				playerData.getFullnessPercentage()
		);
		if (!playerData.isNutritionFrozen()) {
			playerData.removeNutrition(calculateDecayAmount(
					groupSettings.getNutritionDecayPerSecond(),
					getNutritionMovementMultiplier(player, movementSettings),
					getDecayMultiplier(nutritionEffect),
					elapsedSeconds
			));
		}

		if (!playerData.isFullnessFrozen()) {
			playerData.removeFullness(calculateDecayAmount(
					groupSettings.getFullnessDecayPerSecond(),
					getFullnessMovementMultiplier(player, movementSettings),
					getDecayMultiplier(fullnessEffect),
					elapsedSeconds
			));
		}
	}

	private double getNutritionMovementMultiplier(Player player, MovementDecaySettings settings) {
		if (player.isSwimming()) {
			return settings.getSwimmingNutritionMultiplier();
		}

		if (player.isSprinting()) {
			return settings.getSprintingNutritionMultiplier();
		}

		return 1.0;
	}

	private double getFullnessMovementMultiplier(Player player, MovementDecaySettings settings) {
		if (player.isSwimming()) {
			return settings.getSwimmingFullnessMultiplier();
		}

		if (player.isSprinting()) {
			return settings.getSprintingFullnessMultiplier();
		}

		return 1.0;
	}

	private double getDecayMultiplier(StatusEffectDefinition effect) {
		return effect == null ? 1.0 : effect.getDecayMultiplier();
	}

	private double calculateDecayAmount(
			double ratePerSecond,
			double movementMultiplier,
			double statusMultiplier,
			double elapsedSeconds
	) {
		if (ratePerSecond == 0.0 || movementMultiplier == 0.0 || statusMultiplier == 0.0) {
			return 0.0;
		}

		double decayAmount = ratePerSecond * movementMultiplier * statusMultiplier * elapsedSeconds;
		return Double.isFinite(decayAmount) ? decayAmount : Double.MAX_VALUE;
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}
}
