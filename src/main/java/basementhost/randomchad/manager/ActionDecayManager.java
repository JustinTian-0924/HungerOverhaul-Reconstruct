package basementhost.randomchad.manager;

import basementhost.randomchad.data.ActionCost;
import basementhost.randomchad.data.ActionDecaySettings;
import basementhost.randomchad.data.DecaySettings;
import basementhost.randomchad.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Objects;

// Applies configured one-time costs for successful player actions.
public final class ActionDecayManager {
	private final GroupManager groupManager;
	private final PlayerDataManager playerDataManager;
	private DecaySettings settings;

	public ActionDecayManager(
			GroupManager groupManager,
			PlayerDataManager playerDataManager,
			DecaySettings settings
	) {
		this.groupManager = Objects.requireNonNull(groupManager, "groupManager");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void updateSettings(DecaySettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void applyActionCost(Player player, ActionType actionType) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(actionType, "actionType");
		if (!settings.isActionCostsEnabled()
				|| player.isDead()
				|| !isAffectedGameMode(player.getGameMode())) {
			return;
		}

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		ActionDecaySettings actionSettings = groupManager.getSettings(player).getActionDecaySettings();
		ActionCost actionCost = getActionCost(actionSettings, actionType);
		if (!playerData.isNutritionFrozen()) {
			playerData.removeNutrition(actionCost.getNutritionCost());
		}

		if (!playerData.isFullnessFrozen()) {
			playerData.removeFullness(actionCost.getFullnessCost());
		}
	}

	private ActionCost getActionCost(ActionDecaySettings settings, ActionType actionType) {
		return switch (actionType) {
			case JUMPING -> settings.getJumping();
			case ATTACKING -> settings.getAttacking();
			case TAKING_DAMAGE -> settings.getTakingDamage();
			case BREAKING_BLOCK -> settings.getBreakingBlock();
			case PLACING_BLOCK -> settings.getPlacingBlock();
		};
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}

	public enum ActionType {
		JUMPING,
		ATTACKING,
		TAKING_DAMAGE,
		BREAKING_BLOCK,
		PLACING_BLOCK
	}
}
