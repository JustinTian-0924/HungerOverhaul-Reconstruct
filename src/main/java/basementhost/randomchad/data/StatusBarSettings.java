package basementhost.randomchad.data;

// Controls the periodic nutrition and fullness action bar.
public record StatusBarSettings(boolean enabled, int updateIntervalTicks) {
	public StatusBarSettings {
		if (updateIntervalTicks <= 0) {
			throw new IllegalArgumentException("actionbar.update-interval-ticks must be greater than zero.");
		}
	}
}
