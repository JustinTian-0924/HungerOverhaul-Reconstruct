package basementhost.randomchad.data;

// Holds the timing and damage amount for one starvation range.
public final class StarvationDamageRule {
	private final boolean enabled;
	private final long intervalTicks;
	private final double damageHp;

	public StarvationDamageRule(boolean enabled, long intervalTicks, double damageHp) {
		if (intervalTicks <= 0L) {
			throw new IllegalArgumentException("intervalTicks must be greater than zero");
		}

		if (!Double.isFinite(damageHp) || damageHp <= 0.0) {
			throw new IllegalArgumentException("damageHp must be a finite number greater than zero");
		}

		this.enabled = enabled;
		this.intervalTicks = intervalTicks;
		this.damageHp = damageHp;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getIntervalTicks() {
		return intervalTicks;
	}

	public double getDamageHp() {
		return damageHp;
	}
}
