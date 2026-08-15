package basementhost.randomchad.data;

// Holds validated global scheduling rules for passive value decay.
public final class DecaySettings {
	private final boolean enabled;
	private final long intervalTicks;
	private final boolean actionCostsEnabled;

	public DecaySettings(boolean enabled, long intervalTicks, boolean actionCostsEnabled) {
		if (intervalTicks <= 0L) {
			throw new IllegalArgumentException("intervalTicks must be greater than zero");
		}

		this.enabled = enabled;
		this.intervalTicks = intervalTicks;
		this.actionCostsEnabled = actionCostsEnabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getIntervalTicks() {
		return intervalTicks;
	}

	public boolean isActionCostsEnabled() {
		return actionCostsEnabled;
	}
}
