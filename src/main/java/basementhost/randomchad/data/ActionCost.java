package basementhost.randomchad.data;

// Holds the nutrition and fullness price of one successful player action.
public final class ActionCost {
	private final double nutritionCost;
	private final double fullnessCost;

	public ActionCost(double nutritionCost, double fullnessCost) {
		this.nutritionCost = requireNonNegativeFinite(nutritionCost, "nutritionCost");
		this.fullnessCost = requireNonNegativeFinite(fullnessCost, "fullnessCost");
	}

	public double getNutritionCost() {
		return nutritionCost;
	}

	public double getFullnessCost() {
		return fullnessCost;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number that is not negative");
		}

		return value;
	}
}
