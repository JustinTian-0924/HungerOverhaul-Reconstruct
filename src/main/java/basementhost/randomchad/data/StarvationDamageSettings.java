package basementhost.randomchad.data;

import java.util.Objects;

// Holds validated global rules for critical and empty fullness damage.
public final class StarvationDamageSettings {
	private final boolean enabled;
	private final long checkIntervalTicks;
	private final boolean damageInPeaceful;
	private final double criticalMaximumPercentage;
	private final StarvationDamageRule criticalRule;
	private final StarvationDamageRule emptyRule;

	public StarvationDamageSettings(
			boolean enabled,
			long checkIntervalTicks,
			boolean damageInPeaceful,
			double criticalMaximumPercentage,
			StarvationDamageRule criticalRule,
			StarvationDamageRule emptyRule
	) {
		if (checkIntervalTicks <= 0L) {
			throw new IllegalArgumentException("checkIntervalTicks must be greater than zero");
		}

		if (!Double.isFinite(criticalMaximumPercentage)
				|| criticalMaximumPercentage < 0.0
				|| criticalMaximumPercentage > 100.0) {
			throw new IllegalArgumentException("criticalMaximumPercentage must be from zero to 100");
		}

		this.criticalRule = Objects.requireNonNull(criticalRule, "criticalRule");
		this.emptyRule = Objects.requireNonNull(emptyRule, "emptyRule");
		validateInterval(this.criticalRule, checkIntervalTicks, "criticalRule");
		validateInterval(this.emptyRule, checkIntervalTicks, "emptyRule");

		this.enabled = enabled;
		this.checkIntervalTicks = checkIntervalTicks;
		this.damageInPeaceful = damageInPeaceful;
		this.criticalMaximumPercentage = criticalMaximumPercentage;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getCheckIntervalTicks() {
		return checkIntervalTicks;
	}

	public boolean isDamageInPeaceful() {
		return damageInPeaceful;
	}

	public double getCriticalMaximumPercentage() {
		return criticalMaximumPercentage;
	}

	public StarvationDamageRule getCriticalRule() {
		return criticalRule;
	}

	public StarvationDamageRule getEmptyRule() {
		return emptyRule;
	}

	private void validateInterval(StarvationDamageRule rule, long checkIntervalTicks, String ruleName) {
		if (rule.getIntervalTicks() < checkIntervalTicks
				|| rule.getIntervalTicks() % checkIntervalTicks != 0L) {
			throw new IllegalArgumentException(
					ruleName + " intervalTicks must be at least and evenly divisible by checkIntervalTicks"
			);
		}
	}
}
