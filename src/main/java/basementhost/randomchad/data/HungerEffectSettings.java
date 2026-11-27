package basementhost.randomchad.data;

// Replaces native Hunger exhaustion with fixed and maximum-based value losses.
public record HungerEffectSettings(
		boolean enabled,
		double fullnessLossPerSecondPerLevel,
		double fullnessPercentageLossPerSecondPerLevel,
		boolean fullnessScaleWithHungerLevel,
		double nutritionLossPerSecondPerLevel,
		double nutritionPercentageLossPerSecondPerLevel,
		boolean nutritionScaleWithHungerLevel,
		boolean applyStatusDecayMultiplier,
		double healthRegenerationMultiplier
) {
	public static final HungerEffectSettings DEFAULT = new HungerEffectSettings(true, 10.0, 0.0, true, 10.0, 0.0, true, false, 0.5);
	private static final double TICKS_PER_SECOND = 20.0;

	public HungerEffectSettings {
		validateRate(fullnessLossPerSecondPerLevel, "hunger-effect.fullness-loss-per-second-per-level");
		validateRate(nutritionLossPerSecondPerLevel, "hunger-effect.nutrition-loss-per-second-per-level");
		validateFraction(fullnessPercentageLossPerSecondPerLevel, "hunger-effect.fullness-percentage-loss-per-second-per-level");
		validateFraction(nutritionPercentageLossPerSecondPerLevel, "hunger-effect.nutrition-percentage-loss-per-second-per-level");
		validateFraction(healthRegenerationMultiplier, "hunger-effect.health-regeneration-multiplier");
	}

	public boolean hasDrain() {
		return enabled && (hasFullnessDrain() || hasNutritionDrain());
	}

	public void applyDrainTick(PlayerData data, int amplifier, double fullnessDecayMultiplier, double nutritionDecayMultiplier) {
		if (!enabled || amplifier < 0) {
			return;
		}
		double level = amplifier + 1.0;
		if (drainsFullness(data)) {
			data.removeFullness(lossForTick(fullnessLossPerSecondPerLevel,
					fullnessPercentageLossPerSecondPerLevel, data.getMaxFullness(), fullnessScaleWithHungerLevel ? level : 1.0,
					applyStatusDecayMultiplier ? fullnessDecayMultiplier : 1.0));
		}
		if (drainsNutrition(data)) {
			data.removeNutrition(lossForTick(nutritionLossPerSecondPerLevel,
					nutritionPercentageLossPerSecondPerLevel, data.getMaxNutrition(), nutritionScaleWithHungerLevel ? level : 1.0,
					applyStatusDecayMultiplier ? nutritionDecayMultiplier : 1.0));
		}
	}

	public boolean drainsFullness(PlayerData data) {
		return hasFullnessDrain() && !data.isFullnessFrozen() && data.getFullness() > 0.0;
	}

	public boolean drainsNutrition(PlayerData data) {
		return hasNutritionDrain() && !data.isNutritionFrozen() && data.getNutrition() > 0.0;
	}

	private boolean hasFullnessDrain() {
		return fullnessLossPerSecondPerLevel > 0.0 || fullnessPercentageLossPerSecondPerLevel > 0.0;
	}

	private boolean hasNutritionDrain() {
		return nutritionLossPerSecondPerLevel > 0.0 || nutritionPercentageLossPerSecondPerLevel > 0.0;
	}

	private static double lossForTick(double fixedRate, double maximumFraction, double maximum, double level, double decayMultiplier) {
		// Scale both terms before adding so extreme, but valid, rates do not overflow the sum.
		double loss = (fixedRate / TICKS_PER_SECOND + (maximum * maximumFraction) / TICKS_PER_SECOND) * decayMultiplier * level;
		return Double.isFinite(loss) ? loss : Double.MAX_VALUE;
	}

	private static void validateRate(double value, String path) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(path + " must be finite and not negative.");
		}
	}

	private static void validateFraction(double value, String path) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException(path + " must be between 0 and 1.");
		}
	}
}
