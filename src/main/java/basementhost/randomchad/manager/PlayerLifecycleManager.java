package basementhost.randomchad.manager;

import basementhost.randomchad.data.DeathLifecycleSettings;
import basementhost.randomchad.data.EatingInterruptReason;
import basementhost.randomchad.data.GroupSettings;
import basementhost.randomchad.data.LifecycleValueRule;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.PlayerLifecycleSettings;
import basementhost.randomchad.data.RespawnLifecycleSettings;
import basementhost.randomchad.data.WorldChangeLifecycleSettings;
import org.bukkit.entity.Player;

import java.util.Objects;

// Applies configured value and eating behavior during player lifecycle events.
public final class PlayerLifecycleManager {
	private final GroupManager groupManager;
	private final PlayerDataManager playerDataManager;
	private final PlayerDataPersistenceManager persistenceManager;
	private final EatingManager eatingManager;

	private PlayerLifecycleSettings settings;

	public PlayerLifecycleManager(
			GroupManager groupManager,
			PlayerDataManager playerDataManager,
			PlayerDataPersistenceManager persistenceManager,
			EatingManager eatingManager,
			PlayerLifecycleSettings settings
	) {
		this.groupManager = Objects.requireNonNull(groupManager, "groupManager");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.persistenceManager = Objects.requireNonNull(persistenceManager, "persistenceManager");
		this.eatingManager = Objects.requireNonNull(eatingManager, "eatingManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void updateSettings(PlayerLifecycleSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public PlayerLifecycleSettings getSettings() {
		return settings;
	}

	public void handleDeath(Player player) {
		Objects.requireNonNull(player, "player");
		DeathLifecycleSettings deathSettings = settings.getDeathSettings();
		eatingManager.handleLifecycleTransition(
				player,
				EatingInterruptReason.DEATH,
				deathSettings.isCancelEating(),
				deathSettings.isPreserveDigestionCooldown(),
				false
		);

		if (deathSettings.isSaveData()) {
			persistenceManager.savePlayerData(player);
		}
	}

	public void handleRespawn(Player player) {
		Objects.requireNonNull(player, "player");
		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		ValueSnapshot snapshot = ValueSnapshot.capture(playerData);
		if (!playerDataManager.refreshGroupSettings(player)) {
			return;
		}

		GroupSettings groupSettings = groupManager.getSettings(player);
		RespawnLifecycleSettings respawnSettings = settings.getRespawnSettings();
		applyRules(
				playerData,
				groupSettings,
				snapshot,
				respawnSettings.getNutritionRule(),
				respawnSettings.getFullnessRule(),
				respawnSettings.isAffectFrozenValues()
		);

		if (respawnSettings.isSaveData()) {
			persistenceManager.savePlayerData(player);
		}
	}

	public void handleWorldChange(Player player) {
		Objects.requireNonNull(player, "player");
		WorldChangeLifecycleSettings worldSettings = settings.getWorldChangeSettings();
		eatingManager.handleLifecycleTransition(
				player,
				EatingInterruptReason.WORLD_CHANGE,
				worldSettings.isCancelEating(),
				worldSettings.isPreserveDigestionCooldown(),
				true
		);

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		ValueSnapshot snapshot = ValueSnapshot.capture(playerData);
		if (worldSettings.isRefreshGroup() && !playerDataManager.refreshGroupSettings(player)) {
			return;
		}

		GroupSettings groupSettings = groupManager.getSettings(player);
		applyRules(
				playerData,
				groupSettings,
				snapshot,
				worldSettings.getNutritionRule(),
				worldSettings.getFullnessRule(),
				worldSettings.isAffectFrozenValues()
		);

		if (worldSettings.isSaveData()) {
			persistenceManager.savePlayerData(player);
		}
	}

	private void applyRules(
			PlayerData playerData,
			GroupSettings groupSettings,
			ValueSnapshot snapshot,
			LifecycleValueRule nutritionRule,
			LifecycleValueRule fullnessRule,
			boolean affectFrozenValues
	) {
		if (affectFrozenValues || !playerData.isNutritionFrozen()) {
			playerData.setNutrition(calculateValue(
					nutritionRule,
					snapshot.nutrition(),
					playerData.getNutrition(),
					playerData.getMaxNutrition(),
					groupSettings.getInitialNutrition(),
					snapshot.nutritionPercentage()
			));
		}

		if (affectFrozenValues || !playerData.isFullnessFrozen()) {
			playerData.setFullness(calculateValue(
					fullnessRule,
					snapshot.fullness(),
					playerData.getFullness(),
					playerData.getMaxFullness(),
					groupSettings.getInitialFullness(),
					snapshot.fullnessPercentage()
			));
		}
	}

	private double calculateValue(
			LifecycleValueRule rule,
			double originalValue,
			double currentValue,
			double maximumValue,
			double groupInitialValue,
			double originalPercentage
	) {
		return switch (rule.getMode()) {
			case KEEP -> originalValue;
			case MINIMUM_PERCENTAGE -> Math.max(
					currentValue,
					maximumValue * rule.getPercentage() / 100.0
			);
			case SET_PERCENTAGE -> maximumValue * rule.getPercentage() / 100.0;
			case GROUP_INITIAL -> groupInitialValue;
			case PRESERVE_PERCENTAGE -> maximumValue * originalPercentage / 100.0;
		};
	}

	private record ValueSnapshot(
			double nutrition,
			double fullness,
			double nutritionPercentage,
			double fullnessPercentage
	) {
		private static ValueSnapshot capture(PlayerData playerData) {
			return new ValueSnapshot(
					playerData.getNutrition(),
					playerData.getFullness(),
					playerData.getNutritionPercentage(),
					playerData.getFullnessPercentage()
			);
		}
	}
}
