package basementhost.randomchad.data;

// Holds action bar feedback timing and blocked-message throttling settings.
public final class EatingFeedbackSettings {
	private final boolean actionBarEnabled;
	private final boolean progressActionBarEnabled;
	private final long progressUpdateIntervalTicks;
	private final boolean completionActionBarEnabled;
	private final boolean interruptionActionBarEnabled;
	private final int resultHoldTicks;
	private final boolean cooldownActionBarEnabled;
	private final int cooldownMessageDelayTicks;
	private final long blockedMessageIntervalTicks;
	private final long postConsumptionSilentTicks;

	public EatingFeedbackSettings(
			boolean actionBarEnabled,
			boolean progressActionBarEnabled,
			long progressUpdateIntervalTicks,
			boolean completionActionBarEnabled,
			boolean interruptionActionBarEnabled,
			int resultHoldTicks,
			boolean cooldownActionBarEnabled,
			int cooldownMessageDelayTicks,
			long blockedMessageIntervalTicks,
			long postConsumptionSilentTicks
	) {
		if (progressUpdateIntervalTicks <= 0L) {
			throw new IllegalArgumentException("progressUpdateIntervalTicks must be greater than zero");
		}
		if (resultHoldTicks < 0) {
			throw new IllegalArgumentException("eating.feedback.result-hold-ticks must not be negative");
		}
		if (cooldownMessageDelayTicks < 0) {
			throw new IllegalArgumentException("eating.feedback.cooldown-message-delay-ticks must not be negative");
		}
		if (blockedMessageIntervalTicks < 0L) {
			throw new IllegalArgumentException("blockedMessageIntervalTicks must not be negative");
		}
		if (postConsumptionSilentTicks < 0L) {
			throw new IllegalArgumentException("postConsumptionSilentTicks must not be negative");
		}

		this.actionBarEnabled = actionBarEnabled;
		this.progressActionBarEnabled = progressActionBarEnabled;
		this.progressUpdateIntervalTicks = progressUpdateIntervalTicks;
		this.completionActionBarEnabled = completionActionBarEnabled;
		this.interruptionActionBarEnabled = interruptionActionBarEnabled;
		this.resultHoldTicks = resultHoldTicks;
		this.cooldownActionBarEnabled = cooldownActionBarEnabled;
		this.cooldownMessageDelayTicks = cooldownMessageDelayTicks;
		this.blockedMessageIntervalTicks = blockedMessageIntervalTicks;
		this.postConsumptionSilentTicks = postConsumptionSilentTicks;
	}

	public boolean isActionBarEnabled() {
		return actionBarEnabled;
	}

	public boolean isProgressActionBarEnabled() {
		return actionBarEnabled && progressActionBarEnabled;
	}

	public long getProgressUpdateIntervalTicks() {
		return progressUpdateIntervalTicks;
	}

	public boolean isCompletionActionBarEnabled() {
		return actionBarEnabled && completionActionBarEnabled;
	}

	public boolean isInterruptionActionBarEnabled() {
		return actionBarEnabled && interruptionActionBarEnabled;
	}

	public int getResultHoldTicks() {
		return resultHoldTicks;
	}

	public boolean isCooldownActionBarEnabled() {
		return actionBarEnabled && cooldownActionBarEnabled;
	}

	public int getCooldownMessageDelayTicks() {
		return cooldownMessageDelayTicks;
	}

	public long getBlockedMessageIntervalTicks() {
		return blockedMessageIntervalTicks;
	}

	public long getPostConsumptionSilentTicks() {
		return postConsumptionSilentTicks;
	}
}
