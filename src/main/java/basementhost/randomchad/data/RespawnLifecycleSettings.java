package basementhost.randomchad.data;

import java.util.Objects;

// Holds value recovery behavior applied after respawn.
public final class RespawnLifecycleSettings {
	private final LifecycleValueRule nutritionRule;
	private final LifecycleValueRule fullnessRule;
	private final boolean affectFrozenValues;
	private final boolean saveData;

	public RespawnLifecycleSettings(
			LifecycleValueRule nutritionRule,
			LifecycleValueRule fullnessRule,
			boolean affectFrozenValues,
			boolean saveData
	) {
		this.nutritionRule = Objects.requireNonNull(nutritionRule, "nutritionRule");
		this.fullnessRule = Objects.requireNonNull(fullnessRule, "fullnessRule");
		this.affectFrozenValues = affectFrozenValues;
		this.saveData = saveData;
	}

	public LifecycleValueRule getNutritionRule() {
		return nutritionRule;
	}

	public LifecycleValueRule getFullnessRule() {
		return fullnessRule;
	}

	public boolean isAffectFrozenValues() {
		return affectFrozenValues;
	}

	public boolean isSaveData() {
		return saveData;
	}
}
