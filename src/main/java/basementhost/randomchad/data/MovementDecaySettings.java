package basementhost.randomchad.data;

// Holds passive decay multipliers for movement states.
public final class MovementDecaySettings {
	private final double sprintingNutritionMultiplier;
	private final double sprintingFullnessMultiplier;
	private final double swimmingNutritionMultiplier;
	private final double swimmingFullnessMultiplier;

	public MovementDecaySettings(
			double sprintingNutritionMultiplier,
			double sprintingFullnessMultiplier,
			double swimmingNutritionMultiplier,
			double swimmingFullnessMultiplier
	) {
		this.sprintingNutritionMultiplier = requireNonNegativeFinite(
				sprintingNutritionMultiplier,
				"sprintingNutritionMultiplier"
		);
		this.sprintingFullnessMultiplier = requireNonNegativeFinite(
				sprintingFullnessMultiplier,
				"sprintingFullnessMultiplier"
		);
		this.swimmingNutritionMultiplier = requireNonNegativeFinite(
				swimmingNutritionMultiplier,
				"swimmingNutritionMultiplier"
		);
		this.swimmingFullnessMultiplier = requireNonNegativeFinite(
				swimmingFullnessMultiplier,
				"swimmingFullnessMultiplier"
		);
	}

	public double getSprintingNutritionMultiplier() {
		return sprintingNutritionMultiplier;
	}

	public double getSprintingFullnessMultiplier() {
		return sprintingFullnessMultiplier;
	}

	public double getSwimmingNutritionMultiplier() {
		return swimmingNutritionMultiplier;
	}

	public double getSwimmingFullnessMultiplier() {
		return swimmingFullnessMultiplier;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number that is not negative");
		}

		return value;
	}
}
