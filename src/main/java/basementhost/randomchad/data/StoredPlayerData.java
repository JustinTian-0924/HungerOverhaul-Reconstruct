package basementhost.randomchad.data;

import java.util.Objects;

// Represents one validated player data file without storing group-controlled maximum values.
public final class StoredPlayerData {
	private final String lastKnownName;
	private final double nutrition;
	private final double fullness;
	private final boolean nutritionFrozen;
	private final boolean fullnessFrozen;
	private final long lastSeen;

	public StoredPlayerData(
			String lastKnownName,
			double nutrition,
			double fullness,
			boolean nutritionFrozen,
			boolean fullnessFrozen,
			long lastSeen
	) {
		this.lastKnownName = requireName(lastKnownName);
		this.nutrition = requireNonNegativeFinite(nutrition, "nutrition");
		this.fullness = requireNonNegativeFinite(fullness, "fullness");
		this.nutritionFrozen = nutritionFrozen;
		this.fullnessFrozen = fullnessFrozen;

		if (lastSeen < 0L) {
			throw new IllegalArgumentException("lastSeen must not be negative");
		}
		this.lastSeen = lastSeen;
	}

	public String getLastKnownName() {
		return lastKnownName;
	}

	public double getNutrition() {
		return nutrition;
	}

	public double getFullness() {
		return fullness;
	}

	public boolean isNutritionFrozen() {
		return nutritionFrozen;
	}

	public boolean isFullnessFrozen() {
		return fullnessFrozen;
	}

	public long getLastSeen() {
		return lastSeen;
	}

	private static String requireName(String name) {
		String validName = Objects.requireNonNull(name, "lastKnownName").trim();
		if (validName.isEmpty()) {
			throw new IllegalArgumentException("lastKnownName must not be empty");
		}

		return validName;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number that is not negative");
		}

		return value;
	}
}
