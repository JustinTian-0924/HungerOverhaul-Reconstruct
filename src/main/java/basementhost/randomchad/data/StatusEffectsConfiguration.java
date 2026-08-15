package basementhost.randomchad.data;

import java.util.Comparator;
import java.util.List;

// Holds the complete validated status range table used at runtime.
public final class StatusEffectsConfiguration {
	private final boolean enabled;
	private final long updateIntervalTicks;
	private final double minimumAttributeModifier;
	private final double maximumAttributeModifier;
	private final List<StatusEffectDefinition> fullnessEffects;
	private final List<StatusEffectDefinition> nutritionEffects;

	public StatusEffectsConfiguration(
			boolean enabled,
			long updateIntervalTicks,
			double minimumAttributeModifier,
			double maximumAttributeModifier,
			List<StatusEffectDefinition> fullnessEffects,
			List<StatusEffectDefinition> nutritionEffects
	) {
		if (updateIntervalTicks <= 0L) {
			throw new IllegalArgumentException("updateIntervalTicks must be greater than zero");
		}

		if (!Double.isFinite(minimumAttributeModifier)
				|| minimumAttributeModifier <= -1.0
				|| minimumAttributeModifier > 0.0) {
			throw new IllegalArgumentException("minimumAttributeModifier must be greater than -1 and at most zero");
		}

		if (!Double.isFinite(maximumAttributeModifier) || maximumAttributeModifier < 0.0) {
			throw new IllegalArgumentException("maximumAttributeModifier must be finite and not negative");
		}

		this.enabled = enabled;
		this.updateIntervalTicks = updateIntervalTicks;
		this.minimumAttributeModifier = minimumAttributeModifier;
		this.maximumAttributeModifier = maximumAttributeModifier;
		this.fullnessEffects = prepareEffects(fullnessEffects, "fullness");
		this.nutritionEffects = prepareEffects(nutritionEffects, "nutrition");
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getUpdateIntervalTicks() {
		return updateIntervalTicks;
	}

	public double getMinimumAttributeModifier() {
		return minimumAttributeModifier;
	}

	public double getMaximumAttributeModifier() {
		return maximumAttributeModifier;
	}

	public List<StatusEffectDefinition> getFullnessEffects() {
		return fullnessEffects;
	}

	public List<StatusEffectDefinition> getNutritionEffects() {
		return nutritionEffects;
	}

	public StatusEffectDefinition findFullnessEffect(double percentage) {
		return findEffect(fullnessEffects, percentage);
	}

	public StatusEffectDefinition findNutritionEffect(double percentage) {
		return findEffect(nutritionEffects, percentage);
	}

	public double clampAttributeModifier(double modifier) {
		return Math.max(minimumAttributeModifier, Math.min(modifier, maximumAttributeModifier));
	}

	private List<StatusEffectDefinition> prepareEffects(
			List<StatusEffectDefinition> configuredEffects,
			String valueName
	) {
		if (configuredEffects == null || configuredEffects.isEmpty()) {
			throw new IllegalArgumentException(valueName + " effects must contain at least one range");
		}

		List<StatusEffectDefinition> sortedEffects = configuredEffects.stream()
				.sorted(Comparator.comparingDouble(StatusEffectDefinition::getMinimumPercentage))
				.toList();
		validateCompleteCoverage(sortedEffects, valueName);
		return List.copyOf(sortedEffects);
	}

	private void validateCompleteCoverage(List<StatusEffectDefinition> effects, String valueName) {
		if (Double.compare(effects.get(0).getMinimumPercentage(), 0.0) != 0) {
			throw new IllegalArgumentException(valueName + " effects must begin at zero percent");
		}

		for (int index = 1; index < effects.size(); index++) {
			double previousMaximum = effects.get(index - 1).getMaximumPercentage();
			double currentMinimum = effects.get(index).getMinimumPercentage();
			if (Double.compare(previousMaximum, currentMinimum) != 0) {
				throw new IllegalArgumentException(valueName + " effects must not contain gaps or overlaps");
			}
		}

		if (Double.compare(effects.get(effects.size() - 1).getMaximumPercentage(), 100.0) != 0) {
			throw new IllegalArgumentException(valueName + " effects must end at 100 percent");
		}
	}

	private StatusEffectDefinition findEffect(List<StatusEffectDefinition> effects, double percentage) {
		for (StatusEffectDefinition effect : effects) {
			if (effect.contains(percentage)) {
				return effect;
			}
		}

		throw new IllegalStateException("No status effect range contains percentage " + percentage);
	}
}
