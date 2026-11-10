package basementhost.randomchad.data;

import java.util.UUID;

// Captures an admin change before asynchronous saves can observe newer player values.
public record AdminAuditEntry(
		Action action,
		UUID playerId,
		String playerName,
		TargetMode targetMode,
		Values before,
		Values after
) {
	public enum TargetMode {
		ONLINE,
		OFFLINE_CACHE,
		OFFLINE_FILE
	}

	public enum Outcome {
		APPLIED,
		SAVED,
		CACHE_ONLY
	}

	public record Action(
			String actorName,
			String actorType,
			UUID actorId,
			String operation,
			String valueTarget,
			Double amount
	) {
	}

	public record Values(double nutrition, double fullness, boolean nutritionFrozen, boolean fullnessFrozen) {
		public static Values from(PlayerData playerData) {
			return new Values(
					playerData.getNutrition(),
					playerData.getFullness(),
					playerData.isNutritionFrozen(),
					playerData.isFullnessFrozen()
			);
		}

		public static Values from(StoredPlayerData playerData) {
			return new Values(
					playerData.getNutrition(),
					playerData.getFullness(),
					playerData.isNutritionFrozen(),
					playerData.isFullnessFrozen()
			);
		}
	}
}
