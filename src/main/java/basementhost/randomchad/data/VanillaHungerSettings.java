package basementhost.randomchad.data;

import java.util.Locale;
import java.util.Objects;

// Holds validated rules for taking over vanilla hunger behavior and display.
public final class VanillaHungerSettings {
	private static final int MINIMUM_ALLOWED_FOOD_LEVEL = 0;
	private static final int MAXIMUM_ALLOWED_FOOD_LEVEL = 20;

	private final DisplaySource displaySource;
	private final int minimumFoodLevel;
	private final int maximumFoodLevel;
	private final boolean naturalRegenerationBlocked;

	public VanillaHungerSettings(
			DisplaySource displaySource,
			int minimumFoodLevel,
			int maximumFoodLevel,
			boolean naturalRegenerationBlocked
	) {
		this.displaySource = Objects.requireNonNull(displaySource, "displaySource");
		if (minimumFoodLevel < MINIMUM_ALLOWED_FOOD_LEVEL
				|| minimumFoodLevel > MAXIMUM_ALLOWED_FOOD_LEVEL) {
			throw new IllegalArgumentException("minimumFoodLevel must be between 0 and 20");
		}

		if (maximumFoodLevel < MINIMUM_ALLOWED_FOOD_LEVEL
				|| maximumFoodLevel > MAXIMUM_ALLOWED_FOOD_LEVEL) {
			throw new IllegalArgumentException("maximumFoodLevel must be between 0 and 20");
		}

		if (minimumFoodLevel > maximumFoodLevel) {
			throw new IllegalArgumentException("minimumFoodLevel must not be greater than maximumFoodLevel");
		}

		this.minimumFoodLevel = minimumFoodLevel;
		this.maximumFoodLevel = maximumFoodLevel;
		this.naturalRegenerationBlocked = naturalRegenerationBlocked;
	}

	public DisplaySource getDisplaySource() {
		return displaySource;
	}

	public int getMinimumFoodLevel() {
		return minimumFoodLevel;
	}

	public int getMaximumFoodLevel() {
		return maximumFoodLevel;
	}

	public boolean isNaturalRegenerationBlocked() {
		return naturalRegenerationBlocked;
	}

	public enum DisplaySource {
		FULLNESS,
		NUTRITION;

		public static DisplaySource fromConfigValue(String configuredValue) {
			Objects.requireNonNull(configuredValue, "configuredValue");
			return switch (configuredValue.trim().toLowerCase(Locale.ROOT)) {
				case "fullness" -> FULLNESS;
				case "nutrition" -> NUTRITION;
				default -> throw new IllegalArgumentException(
						"display source must be 'fullness' or 'nutrition'"
				);
			};
		}
	}
}
