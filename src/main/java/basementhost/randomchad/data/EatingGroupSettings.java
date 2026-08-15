package basementhost.randomchad.data;

// Holds one permission group's eating speed multiplier.
public final class EatingGroupSettings {
	private final double speedMultiplier;

	public EatingGroupSettings(double speedMultiplier) {
		if (!Double.isFinite(speedMultiplier) || speedMultiplier <= 0.0) {
			throw new IllegalArgumentException("speedMultiplier must be finite and greater than zero");
		}

		this.speedMultiplier = speedMultiplier;
	}

	public double getSpeedMultiplier() {
		return speedMultiplier;
	}
}
