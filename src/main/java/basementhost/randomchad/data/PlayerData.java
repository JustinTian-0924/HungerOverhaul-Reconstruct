package basementhost.randomchad.data;

import java.util.Objects;
import java.util.UUID;

// Holds and protects the HungerOverhaul values (temporary for now).
public final class PlayerData {
	private final UUID playerId;

	private double nutrition;
	private double fullness;
	private double maxNutrition;
	private double maxFullness;
	private boolean nutritionFrozen;
	private boolean fullnessFrozen;

	public PlayerData(
			UUID playerId,
			double maxNutrition,
			double maxFullness,
			double initialNutrition,
			double initialFullness
	) {
		this.playerId = Objects.requireNonNull(playerId, "playerId");
		this.maxNutrition = requirePositiveFinite(maxNutrition, "maxNutrition");
		this.maxFullness = requirePositiveFinite(maxFullness, "maxFullness");
		this.nutrition = clamp(requireFinite(initialNutrition, "initialNutrition"), this.maxNutrition);
		this.fullness = clamp(requireFinite(initialFullness, "initialFullness"), this.maxFullness);
	}

	public UUID getPlayerId() {
		return playerId;
	}

	public double getNutrition() {
		return nutrition;
	}

	public double getFullness() {
		return fullness;
	}

	public double getMaxNutrition() {
		return maxNutrition;
	}

	public double getMaxFullness() {
		return maxFullness;
	}

	public double getNutritionPercentage() {
		return nutrition / maxNutrition * 100.0;
	}

	public double getFullnessPercentage() {
		return fullness / maxFullness * 100.0;
	}

	public boolean isNutritionFrozen() {
		return nutritionFrozen;
	}

	public boolean isFullnessFrozen() {
		return fullnessFrozen;
	}

	public void setNutrition(double nutrition) {
		this.nutrition = clamp(requireFinite(nutrition, "nutrition"), maxNutrition);
	}

	public void setFullness(double fullness) {
		this.fullness = clamp(requireFinite(fullness, "fullness"), maxFullness);
	}

	public void setMaxNutrition(double maxNutrition) {
		this.maxNutrition = requirePositiveFinite(maxNutrition, "maxNutrition");
		nutrition = clamp(nutrition, this.maxNutrition);
	}

	public void setMaxFullness(double maxFullness) {
		this.maxFullness = requirePositiveFinite(maxFullness, "maxFullness");
		fullness = clamp(fullness, this.maxFullness);
	}

	public void setNutritionFrozen(boolean nutritionFrozen) {
		this.nutritionFrozen = nutritionFrozen;
	}

	public void setFullnessFrozen(boolean fullnessFrozen) {
		this.fullnessFrozen = fullnessFrozen;
	}

	public void addNutrition(double amount) {
		changeNutrition(requireNonNegativeFinite(amount, "amount"));
	}

	public void addFullness(double amount) {
		changeFullness(requireNonNegativeFinite(amount, "amount"));
	}

	public void removeNutrition(double amount) {
		changeNutrition(-requireNonNegativeFinite(amount, "amount"));
	}

	public void removeFullness(double amount) {
		changeFullness(-requireNonNegativeFinite(amount, "amount"));
	}

	// Signed changes support configuration values such as nutrition: -50.
	public void changeNutrition(double amount) {
		double validAmount = requireFinite(amount, "amount");
		nutrition = clamp(nutrition + validAmount, maxNutrition);
	}

	// Signed changes support configuration values such as fullness: -50.
	public void changeFullness(double amount) {
		double validAmount = requireFinite(amount, "amount");
		fullness = clamp(fullness + validAmount, maxFullness);
	}

	private static double clamp(double value, double maximum) {
		return Math.max(0.0, Math.min(value, maximum));
	}

	private static double requireFinite(double value, String valueName) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(valueName + " must be a finite number");
		}

		return value;
	}

	private static double requirePositiveFinite(double value, String valueName) {
		requireFinite(value, valueName);
		if (value <= 0.0) {
			throw new IllegalArgumentException(valueName + " must be greater than zero");
		}

		return value;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		requireFinite(value, valueName);
		if (value < 0.0) {
			throw new IllegalArgumentException(valueName + " must not be negative");
		}

		return value;
	}
}
