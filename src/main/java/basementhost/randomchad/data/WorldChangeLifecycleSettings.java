package basementhost.randomchad.data;

import java.util.Objects;

// Holds group refresh and value behavior applied after changing worlds.
public final class WorldChangeLifecycleSettings {
	private final boolean cancelEating;
	private final boolean preserveDigestionCooldown;
	private final boolean refreshGroup;
	private final LifecycleValueRule nutritionRule;
	private final LifecycleValueRule fullnessRule;
	private final boolean affectFrozenValues;
	private final boolean saveData;

	public WorldChangeLifecycleSettings(
			boolean cancelEating,
			boolean preserveDigestionCooldown,
			boolean refreshGroup,
			LifecycleValueRule nutritionRule,
			LifecycleValueRule fullnessRule,
			boolean affectFrozenValues,
			boolean saveData
	) {
		this.cancelEating = cancelEating;
		this.preserveDigestionCooldown = preserveDigestionCooldown;
		this.refreshGroup = refreshGroup;
		this.nutritionRule = Objects.requireNonNull(nutritionRule, "nutritionRule");
		this.fullnessRule = Objects.requireNonNull(fullnessRule, "fullnessRule");
		this.affectFrozenValues = affectFrozenValues;
		this.saveData = saveData;
	}

	public boolean isCancelEating() {
		return cancelEating;
	}

	public boolean isPreserveDigestionCooldown() {
		return preserveDigestionCooldown;
	}

	public boolean isRefreshGroup() {
		return refreshGroup;
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
