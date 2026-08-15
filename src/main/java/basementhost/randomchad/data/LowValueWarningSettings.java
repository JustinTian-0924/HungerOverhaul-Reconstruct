package basementhost.randomchad.data;

// Holds validated scheduling and threshold rules for low-value warnings.
public final class LowValueWarningSettings {
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long MAX_REPEAT_INTERVAL_SECONDS = Long.MAX_VALUE / NANOS_PER_SECOND;

	private final boolean enabled;
	private final long checkIntervalTicks;
	private final long repeatIntervalNanos;
	private final boolean nutritionWarningEnabled;
	private final double nutritionThresholdPercentage;
	private final boolean fullnessWarningEnabled;
	private final double fullnessThresholdPercentage;

	public LowValueWarningSettings(
			boolean enabled,
			long checkIntervalTicks,
			long repeatIntervalSeconds,
			boolean nutritionWarningEnabled,
			double nutritionThresholdPercentage,
			boolean fullnessWarningEnabled,
			double fullnessThresholdPercentage
	) {
		if (checkIntervalTicks <= 0L) {
			throw new IllegalArgumentException("checkIntervalTicks must be greater than zero");
		}

		if (repeatIntervalSeconds <= 0L || repeatIntervalSeconds > MAX_REPEAT_INTERVAL_SECONDS) {
			throw new IllegalArgumentException("repeatIntervalSeconds is outside the supported positive range");
		}

		this.enabled = enabled;
		this.checkIntervalTicks = checkIntervalTicks;
		this.repeatIntervalNanos = repeatIntervalSeconds * NANOS_PER_SECOND;
		this.nutritionWarningEnabled = nutritionWarningEnabled;
		this.nutritionThresholdPercentage = requirePercentage(
				nutritionThresholdPercentage,
				"nutritionThresholdPercentage"
		);
		this.fullnessWarningEnabled = fullnessWarningEnabled;
		this.fullnessThresholdPercentage = requirePercentage(
				fullnessThresholdPercentage,
				"fullnessThresholdPercentage"
		);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getCheckIntervalTicks() {
		return checkIntervalTicks;
	}

	public long getRepeatIntervalNanos() {
		return repeatIntervalNanos;
	}

	public boolean isNutritionWarningEnabled() {
		return nutritionWarningEnabled;
	}

	public double getNutritionThresholdPercentage() {
		return nutritionThresholdPercentage;
	}

	public boolean isFullnessWarningEnabled() {
		return fullnessWarningEnabled;
	}

	public double getFullnessThresholdPercentage() {
		return fullnessThresholdPercentage;
	}

	private static double requirePercentage(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number from zero to 100");
		}

		return value;
	}
}
