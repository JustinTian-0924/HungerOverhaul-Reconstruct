package basementhost.randomchad.data;

import java.util.Objects;

// Holds one lifecycle value mode and its optional percentage.
public final class LifecycleValueRule {
	private final LifecycleValueMode mode;
	private final Double percentage;

	public LifecycleValueRule(LifecycleValueMode mode, Double percentage) {
		this.mode = Objects.requireNonNull(mode, "mode");
		if (mode.requiresPercentage()) {
			if (percentage == null || !Double.isFinite(percentage) || percentage < 0.0 || percentage > 100.0) {
				throw new IllegalArgumentException("percentage must be a finite number from 0 to 100");
			}
		} else if (percentage != null) {
			throw new IllegalArgumentException("percentage is only valid for percentage-based modes");
		}

		this.percentage = percentage;
	}

	public LifecycleValueMode getMode() {
		return mode;
	}

	public double getPercentage() {
		if (percentage == null) {
			throw new IllegalStateException("This lifecycle value mode does not use a percentage");
		}

		return percentage;
	}
}
