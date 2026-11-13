package basementhost.randomchad.data;

// Optional optimizations that preserve the existing task intervals and gameplay rules.
public record PerformanceSettings(
		boolean skipIdleEatingChecks,
		boolean cacheStatusbarMessages,
		boolean reusePlaceholderSnapshots,
		boolean cachePlaceholderStatusNames
) {
	public static final PerformanceSettings DEFAULT = new PerformanceSettings(true, true, true, true);
}
