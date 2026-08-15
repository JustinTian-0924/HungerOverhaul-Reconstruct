package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.FoodConsumptionResult;
import basementhost.randomchad.data.FoodSettings;
import basementhost.randomchad.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

// Applies food changes consistently for item foods and cake slices.
public final class FoodConsumptionManager {
	private final HungerOverhaul plugin;
	private final EatingManager eatingManager;

	public FoodConsumptionManager(HungerOverhaul plugin, EatingManager eatingManager) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.eatingManager = Objects.requireNonNull(eatingManager, "eatingManager");
	}

	public boolean hasAvailableData(Player player) {
		Objects.requireNonNull(player, "player");
		if (plugin.getPersistenceManager().isPlayerDataLoading(player.getUniqueId())) {
			eatingManager.notifyDataBlocked(player, true);
			return false;
		}

		if (plugin.getPlayerDataManager().findPlayerData(player).isEmpty()) {
			eatingManager.notifyDataBlocked(player, false);
			return false;
		}

		return true;
	}

	public Optional<FoodConsumptionResult> applyFood(Player player, FoodSettings foodSettings) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(foodSettings, "foodSettings");

		PlayerData playerData = plugin.getPlayerDataManager().findPlayerData(player).orElse(null);
		if (playerData == null) {
			return Optional.empty();
		}

		plugin.getPlayerDataManager().refreshGroupSettings(player);
		double nutritionBefore = playerData.getNutrition();
		double fullnessBefore = playerData.getFullness();
		if (!playerData.isNutritionFrozen()) {
			playerData.changeNutrition(foodSettings.getNutritionChange());
		}

		if (!playerData.isFullnessFrozen()) {
			playerData.changeFullness(foodSettings.getFullnessChange());
		}

		plugin.getPersistenceManager().savePlayerData(player);
		double nutritionChange = playerData.getNutrition() - nutritionBefore;
		double fullnessChange = playerData.getFullness() - fullnessBefore;
		boolean nutritionClamped = !playerData.isNutritionFrozen()
				&& foodSettings.getNutritionChange() > 0.0
				&& nutritionChange < foodSettings.getNutritionChange();
		boolean fullnessClamped = !playerData.isFullnessFrozen()
				&& foodSettings.getFullnessChange() > 0.0
				&& fullnessChange < foodSettings.getFullnessChange();
		return Optional.of(new FoodConsumptionResult(
				nutritionChange,
				fullnessChange,
				nutritionClamped,
				fullnessClamped
		));
	}
}
