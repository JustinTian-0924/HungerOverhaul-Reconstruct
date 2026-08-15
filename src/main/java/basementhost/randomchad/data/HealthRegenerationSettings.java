package basementhost.randomchad.data;

// Holds validated global scheduling and charging rules for custom health regeneration.
public final class HealthRegenerationSettings {
	private final boolean enabled;
	private final long intervalTicks;
	private final double minimumChargedHealHp;

	public HealthRegenerationSettings(
			boolean enabled,
			long intervalTicks,
			double minimumChargedHealHp
	) {
		if (intervalTicks <= 0L) {
			throw new IllegalArgumentException("intervalTicks must be greater than zero");
		}

		if (!Double.isFinite(minimumChargedHealHp) || minimumChargedHealHp < 0.0) {
			throw new IllegalArgumentException("minimumChargedHealHp must be finite and not negative");
		}

		this.enabled = enabled;
		this.intervalTicks = intervalTicks;
		this.minimumChargedHealHp = minimumChargedHealHp;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getIntervalTicks() {
		return intervalTicks;
	}

	public double getMinimumChargedHealHp() {
		return minimumChargedHealHp;
	}
}
