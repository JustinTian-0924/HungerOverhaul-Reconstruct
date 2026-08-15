package basementhost.randomchad.data;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

// Represents one validated player data file.
public final class StoredPlayerData {
	private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z0-9_-]+");

	private final String lastKnownName;
	private final String lastKnownGroupId;
	private final Double maxNutrition;
	private final Double maxFullness;
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
		this(
				lastKnownName,
				null,
				null,
				null,
				nutrition,
				fullness,
				nutritionFrozen,
				fullnessFrozen,
				lastSeen
		);
	}

	public StoredPlayerData(
			String lastKnownName,
			String lastKnownGroupId,
			double maxNutrition,
			double maxFullness,
			double nutrition,
			double fullness,
			boolean nutritionFrozen,
			boolean fullnessFrozen,
			long lastSeen
	) {
		this(
				lastKnownName,
				requireGroupId(lastKnownGroupId),
				Double.valueOf(requirePositiveFinite(maxNutrition, "maxNutrition")),
				Double.valueOf(requirePositiveFinite(maxFullness, "maxFullness")),
				nutrition,
				fullness,
				nutritionFrozen,
				fullnessFrozen,
				lastSeen
		);
	}

	private StoredPlayerData(
			String lastKnownName,
			String lastKnownGroupId,
			Double maxNutrition,
			Double maxFullness,
			double nutrition,
			double fullness,
			boolean nutritionFrozen,
			boolean fullnessFrozen,
			long lastSeen
	) {
		this.lastKnownName = requireName(lastKnownName);
		this.nutrition = requireNonNegativeFinite(nutrition, "nutrition");
		this.fullness = requireNonNegativeFinite(fullness, "fullness");
		if ((lastKnownGroupId == null) != (maxNutrition == null)
				|| (lastKnownGroupId == null) != (maxFullness == null)) {
			throw new IllegalArgumentException("group snapshot fields must either all be present or all be absent");
		}

		if (maxNutrition != null && this.nutrition > maxNutrition) {
			throw new IllegalArgumentException("nutrition must not exceed maxNutrition");
		}
		if (maxFullness != null && this.fullness > maxFullness) {
			throw new IllegalArgumentException("fullness must not exceed maxFullness");
		}
		this.lastKnownGroupId = lastKnownGroupId;
		this.maxNutrition = maxNutrition;
		this.maxFullness = maxFullness;
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

	public boolean hasGroupSnapshot() {
		return lastKnownGroupId != null;
	}

	public Optional<String> getLastKnownGroupId() {
		return Optional.ofNullable(lastKnownGroupId);
	}

	public OptionalDouble getMaxNutrition() {
		return maxNutrition == null ? OptionalDouble.empty() : OptionalDouble.of(maxNutrition);
	}

	public OptionalDouble getMaxFullness() {
		return maxFullness == null ? OptionalDouble.empty() : OptionalDouble.of(maxFullness);
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

	private static String requireGroupId(String groupId) {
		String validGroupId = Objects.requireNonNull(groupId, "lastKnownGroupId").trim();
		if (!GROUP_ID_PATTERN.matcher(validGroupId).matches()) {
			throw new IllegalArgumentException("lastKnownGroupId must contain only lowercase letters, numbers, _ or -");
		}
		return validGroupId;
	}

	private static double requirePositiveFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number greater than zero");
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
