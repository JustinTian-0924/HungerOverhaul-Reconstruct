package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.HealthRegenerationGroupSettings;
import basementhost.randomchad.data.HealthRegenerationSettings;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StatusEffectDefinition;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

// Restores health on a fixed schedule and charges eligible HungerOverhaul values.
public final class HealthRegenerationManager {
	private static final double HEALTH_EPSILON = 0.001;

	private final HungerOverhaul plugin;
	private final GroupManager groupManager;
	private final PlayerDataManager playerDataManager;
	private final StatusEffectManager statusEffectManager;
	private final HungerEffectManager hungerEffectManager;

	private HealthRegenerationSettings settings;
	private BukkitTask regenerationTask;
	private boolean started;

	public HealthRegenerationManager(
			HungerOverhaul plugin,
			GroupManager groupManager,
			PlayerDataManager playerDataManager,
			StatusEffectManager statusEffectManager,
			HungerEffectManager hungerEffectManager,
			HealthRegenerationSettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.groupManager = Objects.requireNonNull(groupManager, "groupManager");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.statusEffectManager = Objects.requireNonNull(statusEffectManager, "statusEffectManager");
		this.hungerEffectManager = Objects.requireNonNull(hungerEffectManager, "hungerEffectManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("HealthRegenerationManager has already started");
		}

		started = true;
		scheduleRegenerationTask();
	}

	public void updateSettings(HealthRegenerationSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		if (started) {
			scheduleRegenerationTask();
		}
	}

	public void shutdown() {
		started = false;
		cancelRegenerationTask();
	}

	public boolean isEnabled() {
		return settings.isEnabled();
	}

	public HealthRegenerationSettings getSettings() {
		return settings;
	}

	private double getRegenerationMultiplier(PlayerData playerData, double hungerMultiplier) {
		if (hungerMultiplier == 0.0) {
			return 0.0;
		}
		StatusEffectDefinition nutritionEffect = statusEffectManager.getNutritionEffect(
				playerData.getNutritionPercentage()
		);
		StatusEffectDefinition fullnessEffect = statusEffectManager.getFullnessEffect(
				playerData.getFullnessPercentage()
		);
		double modifier = 1.0;
		if (nutritionEffect != null) {
			modifier += nutritionEffect.getHealthRegenerationModifier();
		}
		if (fullnessEffect != null) {
			modifier += fullnessEffect.getHealthRegenerationModifier();
		}

		double combined = Math.max(0.0, modifier) * hungerMultiplier;
		return Double.isFinite(combined) ? combined : Double.MAX_VALUE;
	}

	public RegenerationCycle previewCycle(Player player, PlayerData playerData) {
		return calculateCycle(player, playerData, hungerEffectManager.getRegenerationMultiplier(player));
	}

	private RegenerationCycle calculateCycle(Player player, PlayerData playerData, double hungerMultiplier) {
		HealthRegenerationGroupSettings group = groupManager.getSettings(player).getHealthRegenerationSettings();
		double multiplier = getRegenerationMultiplier(playerData, hungerMultiplier);
		double adjustedHeal = group.getHealHpPerCycle() * multiplier;
		return new RegenerationCycle(group, multiplier, Double.isFinite(adjustedHeal) ? adjustedHeal : Double.MAX_VALUE);
	}

	private void scheduleRegenerationTask() {
		cancelRegenerationTask();
		if (!settings.isEnabled()) {
			return;
		}

		long intervalTicks = settings.getIntervalTicks();
		regenerationTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::regenerateOnlinePlayers,
				intervalTicks,
				intervalTicks
		);
	}

	private void cancelRegenerationTask() {
		if (regenerationTask == null) {
			return;
		}

		regenerationTask.cancel();
		regenerationTask = null;
	}

	private void regenerateOnlinePlayers() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			regeneratePlayer(player);
		}
	}

	private void regeneratePlayer(Player player) {
		if (player.isDead() || !isAffectedGameMode(player.getGameMode())) {
			return;
		}

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		double currentHealth = player.getHealth();
		double missingHealth = Math.max(0.0, maxHealthAttribute.getValue() - currentHealth);
		if (missingHealth <= HEALTH_EPSILON) {
			return;
		}

		double hungerMultiplier = hungerEffectManager.getRegenerationMultiplier(player);
		if (hungerMultiplier == 0.0) {
			return;
		}

		RegenerationCycle cycle = calculateCycle(player, playerData, hungerMultiplier);
		double actualHeal = Math.min(cycle.healHp(), missingHealth);
		if (actualHeal <= HEALTH_EPSILON) {
			return;
		}

		HealthRegenerationGroupSettings groupSettings = cycle.group();
		if (actualHeal >= settings.getMinimumChargedHealHp()
				&& !canPayRegenerationCost(playerData, groupSettings)) {
			return;
		}

		if (actualHeal >= settings.getMinimumChargedHealHp()) {
			payRegenerationCost(playerData, groupSettings);
		}

		player.setHealth(Math.min(currentHealth + actualHeal, maxHealthAttribute.getValue()));
	}

	private boolean canPayRegenerationCost(
			PlayerData playerData,
			HealthRegenerationGroupSettings groupSettings
	) {
		if (!playerData.isNutritionFrozen()
				&& playerData.getNutrition() < groupSettings.getNutritionCost()) {
			return false;
		}

		return playerData.isFullnessFrozen()
				|| playerData.getFullness() >= groupSettings.getFullnessCost();
	}

	private void payRegenerationCost(
			PlayerData playerData,
			HealthRegenerationGroupSettings groupSettings
	) {
		if (!playerData.isNutritionFrozen()) {
			playerData.removeNutrition(groupSettings.getNutritionCost());
		}

		if (!playerData.isFullnessFrozen()) {
			playerData.removeFullness(groupSettings.getFullnessCost());
		}
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}

	public record RegenerationCycle(HealthRegenerationGroupSettings group, double multiplier, double healHp) {
	}
}
