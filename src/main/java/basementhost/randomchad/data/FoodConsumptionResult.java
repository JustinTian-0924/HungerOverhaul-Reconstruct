package basementhost.randomchad.data;

// Describes the real value changes after frozen-state and boundary rules are applied.
public final class FoodConsumptionResult {
	private final double nutritionChange;
	private final double fullnessChange;
	private final boolean nutritionClamped;
	private final boolean fullnessClamped;

	public FoodConsumptionResult(
			double nutritionChange,
			double fullnessChange,
			boolean nutritionClamped,
			boolean fullnessClamped
	) {
		this.nutritionChange = requireFinite(nutritionChange, "nutritionChange");
		this.fullnessChange = requireFinite(fullnessChange, "fullnessChange");
		this.nutritionClamped = nutritionClamped;
		this.fullnessClamped = fullnessClamped;
	}

	public double getNutritionChange() {
		return nutritionChange;
	}

	public double getFullnessChange() {
		return fullnessChange;
	}

	public boolean wasAnyValueClamped() {
		return nutritionClamped || fullnessClamped;
	}

	private static double requireFinite(double value, String valueName) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(valueName + " must be a finite number");
		}

		return value;
	}
}
