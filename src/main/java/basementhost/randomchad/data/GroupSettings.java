package basementhost.randomchad.data;

import java.util.Objects;

// Holds the final resolved hunger values for one permission group.
public final class GroupSettings {
	private final double maxNutrition;
	private final double maxFullness;
	private final double initialNutrition;
	private final double initialFullness;
	private final double nutritionDecayPerSecond;
	private final double fullnessDecayPerSecond;
	private final MovementDecaySettings movementDecaySettings;
	private final ActionDecaySettings actionDecaySettings;
	private final HealthRegenerationGroupSettings healthRegenerationSettings;
	private final EatingGroupSettings eatingSettings;

	public GroupSettings(
			double maxNutrition,
			double maxFullness,
			double initialNutrition,
			double initialFullness,
			double nutritionDecayPerSecond,
			double fullnessDecayPerSecond,
			MovementDecaySettings movementDecaySettings,
			ActionDecaySettings actionDecaySettings,
			HealthRegenerationGroupSettings healthRegenerationSettings,
			EatingGroupSettings eatingSettings
	) {
		this.maxNutrition = requirePositiveFinite(maxNutrition, "maxNutrition");
		this.maxFullness = requirePositiveFinite(maxFullness, "maxFullness");
		this.initialNutrition = requireInRange(
				initialNutrition,
				this.maxNutrition,
				"initialNutrition"
		);
		this.initialFullness = requireInRange(
				initialFullness,
				this.maxFullness,
				"initialFullness"
		);
		this.nutritionDecayPerSecond = requireNonNegativeFinite(
				nutritionDecayPerSecond,
				"nutritionDecayPerSecond"
		);
		this.fullnessDecayPerSecond = requireNonNegativeFinite(
				fullnessDecayPerSecond,
				"fullnessDecayPerSecond"
		);
		this.movementDecaySettings = Objects.requireNonNull(movementDecaySettings, "movementDecaySettings");
		this.actionDecaySettings = Objects.requireNonNull(actionDecaySettings, "actionDecaySettings");
		this.healthRegenerationSettings = Objects.requireNonNull(
				healthRegenerationSettings,
				"healthRegenerationSettings"
		);
		this.eatingSettings = Objects.requireNonNull(eatingSettings, "eatingSettings");
	}

	public double getMaxNutrition() {
		return maxNutrition;
	}

	public double getMaxFullness() {
		return maxFullness;
	}

	public double getInitialNutrition() {
		return initialNutrition;
	}

	public double getInitialFullness() {
		return initialFullness;
	}

	public double getNutritionDecayPerSecond() {
		return nutritionDecayPerSecond;
	}

	public double getFullnessDecayPerSecond() {
		return fullnessDecayPerSecond;
	}

	public MovementDecaySettings getMovementDecaySettings() {
		return movementDecaySettings;
	}

	public ActionDecaySettings getActionDecaySettings() {
		return actionDecaySettings;
	}

	public HealthRegenerationGroupSettings getHealthRegenerationSettings() {
		return healthRegenerationSettings;
	}

	public EatingGroupSettings getEatingSettings() {
		return eatingSettings;
	}

	private static double requirePositiveFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number greater than zero");
		}

		return value;
	}

	private static double requireInRange(double value, double maximum, String valueName) {
		if (!Double.isFinite(value) || value < 0.0 || value > maximum) {
			throw new IllegalArgumentException(
					valueName + " must be a finite number between zero and its maximum value"
			);
		}

		return value;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number that is not negative");
		}

		return value;
	}
}
