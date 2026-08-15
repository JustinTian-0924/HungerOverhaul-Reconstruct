package basementhost.randomchad.data;

import java.util.Objects;

// Holds global overflow and digestion cooldown defaults.
public final class EatingSettings {
	private final FoodOverflowPolicy overflowPolicy;
	private final long cooldownTicks;
	private final EatingFeedbackSettings feedbackSettings;
	private final EatingInterruptSettings interruptSettings;

	public EatingSettings(
			FoodOverflowPolicy overflowPolicy,
			long cooldownTicks,
			EatingFeedbackSettings feedbackSettings,
			EatingInterruptSettings interruptSettings
	) {
		if (cooldownTicks < 0L) {
			throw new IllegalArgumentException("cooldownTicks must not be negative");
		}

		this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy");
		this.cooldownTicks = cooldownTicks;
		this.feedbackSettings = Objects.requireNonNull(feedbackSettings, "feedbackSettings");
		this.interruptSettings = Objects.requireNonNull(interruptSettings, "interruptSettings");
	}

	public FoodOverflowPolicy getOverflowPolicy() {
		return overflowPolicy;
	}

	public long getCooldownTicks() {
		return cooldownTicks;
	}

	public EatingFeedbackSettings getFeedbackSettings() {
		return feedbackSettings;
	}

	public EatingInterruptSettings getInterruptSettings() {
		return interruptSettings;
	}
}
