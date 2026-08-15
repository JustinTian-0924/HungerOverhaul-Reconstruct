package basementhost.randomchad.data;

import java.util.Objects;

// Describes one percentage range and the gameplay modifiers active inside it.
public final class StatusEffectDefinition {
	private final String id;
	private final double minimumPercentage;
	private final double maximumPercentage;
	private final double decayMultiplier;
	private final double healthRegenerationModifier;
	private final double movementSpeedModifier;
	private final double blockBreakSpeedModifier;
	private final double attackDamageModifier;

	public StatusEffectDefinition(
			String id,
			double minimumPercentage,
			double maximumPercentage,
			double decayMultiplier,
			double healthRegenerationModifier,
			double movementSpeedModifier,
			double blockBreakSpeedModifier,
			double attackDamageModifier,
			double minimumAttributeModifier,
			double maximumAttributeModifier
	) {
		this.id = Objects.requireNonNull(id, "id");
		this.minimumPercentage = requirePercentage(minimumPercentage, "minimumPercentage");
		this.maximumPercentage = requirePercentage(maximumPercentage, "maximumPercentage");
		if (this.minimumPercentage >= this.maximumPercentage) {
			throw new IllegalArgumentException("minimumPercentage must be lower than maximumPercentage");
		}

		this.decayMultiplier = requireNonNegativeFinite(decayMultiplier, "decayMultiplier");
		this.healthRegenerationModifier = requireAttributeModifier(
				healthRegenerationModifier,
				minimumAttributeModifier,
				maximumAttributeModifier,
				"healthRegenerationModifier"
		);
		this.movementSpeedModifier = requireAttributeModifier(
				movementSpeedModifier,
				minimumAttributeModifier,
				maximumAttributeModifier,
				"movementSpeedModifier"
		);
		this.blockBreakSpeedModifier = requireAttributeModifier(
				blockBreakSpeedModifier,
				minimumAttributeModifier,
				maximumAttributeModifier,
				"blockBreakSpeedModifier"
		);
		this.attackDamageModifier = requireAttributeModifier(
				attackDamageModifier,
				minimumAttributeModifier,
				maximumAttributeModifier,
				"attackDamageModifier"
		);
	}

	public String getId() {
		return id;
	}

	public double getMinimumPercentage() {
		return minimumPercentage;
	}

	public double getMaximumPercentage() {
		return maximumPercentage;
	}

	public double getDecayMultiplier() {
		return decayMultiplier;
	}

	public double getHealthRegenerationModifier() {
		return healthRegenerationModifier;
	}

	public double getMovementSpeedModifier() {
		return movementSpeedModifier;
	}

	public double getBlockBreakSpeedModifier() {
		return blockBreakSpeedModifier;
	}

	public double getAttackDamageModifier() {
		return attackDamageModifier;
	}

	public boolean contains(double percentage) {
		if (maximumPercentage == 100.0) {
			return percentage >= minimumPercentage && percentage <= maximumPercentage;
		}

		return percentage >= minimumPercentage && percentage < maximumPercentage;
	}

	private static double requirePercentage(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number from zero to 100");
		}

		return value;
	}

	private static double requireNonNegativeFinite(double value, String valueName) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(valueName + " must be a finite number that is not negative");
		}

		return value;
	}

	private static double requireAttributeModifier(
			double value,
			double minimum,
			double maximum,
			String valueName
	) {
		if (!Double.isFinite(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(
					valueName + " must be a finite number from " + minimum + " to " + maximum
			);
		}

		return value;
	}
}
