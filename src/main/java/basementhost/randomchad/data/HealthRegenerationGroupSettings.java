package basementhost.randomchad.data;

// Holds one permission group's healing amount and fixed per-cycle costs.
public final class HealthRegenerationGroupSettings {
	private final double healHpPerCycle;
	private final double nutritionCost;
	private final double fullnessCost;

	public HealthRegenerationGroupSettings(
			double healHpPerCycle,
			double nutritionCost,
			double fullnessCost
	) {
		this.healHpPerCycle = requireNonNegativeFinite(healHpPerCycle, "healHpPerCycle");
		this.nutritionCost = requireNonNegativeFinite(nutritionCost, "nutritionCost");
		this.fullnessCost = requireNonNegativeFinite(fullnessCost, "fullnessCost");
	}

	public double getHealHpPerCycle() {
		return healHpPerCycle;
	}

	public double getNutritionCost() {
		return nutritionCost;
	}

	public double getFullnessCost() {
		return fullnessCost;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(valueName + " must be finite and not negative");
		}

		return value;
	}
}
