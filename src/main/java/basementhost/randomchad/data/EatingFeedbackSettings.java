package basementhost.randomchad.data;

// Holds action bar feedback timing and blocked-message throttling settings.
public final class EatingFeedbackSettings {
	private final boolean progressActionBarEnabled;
	private final long progressUpdateIntervalTicks;
	private final boolean completionActionBarEnabled;
	private final boolean interruptionActionBarEnabled;
	private final long blockedMessageIntervalTicks;
	private final long postConsumptionSilentTicks;

	public EatingFeedbackSettings(
			boolean progressActionBarEnabled,
			long progressUpdateIntervalTicks,
			boolean completionActionBarEnabled,
			boolean interruptionActionBarEnabled,
			long blockedMessageIntervalTicks,
			long postConsumptionSilentTicks
	) {
		if (progressUpdateIntervalTicks <= 0L) {
			throw new IllegalArgumentException("progressUpdateIntervalTicks must be greater than zero");
		}
		if (blockedMessageIntervalTicks < 0L) {
			throw new IllegalArgumentException("blockedMessageIntervalTicks must not be negative");
		}
		if (postConsumptionSilentTicks < 0L) {
			throw new IllegalArgumentException("postConsumptionSilentTicks must not be negative");
		}

		this.progressActionBarEnabled = progressActionBarEnabled;
		this.progressUpdateIntervalTicks = progressUpdateIntervalTicks;
		this.completionActionBarEnabled = completionActionBarEnabled;
		this.interruptionActionBarEnabled = interruptionActionBarEnabled;
		this.blockedMessageIntervalTicks = blockedMessageIntervalTicks;
		this.postConsumptionSilentTicks = postConsumptionSilentTicks;
	}

	public boolean isProgressActionBarEnabled() {
		return progressActionBarEnabled;
	}

	public long getProgressUpdateIntervalTicks() {
		return progressUpdateIntervalTicks;
	}

	public boolean isCompletionActionBarEnabled() {
		return completionActionBarEnabled;
	}

	public boolean isInterruptionActionBarEnabled() {
		return interruptionActionBarEnabled;
	}

	public long getBlockedMessageIntervalTicks() {
		return blockedMessageIntervalTicks;
	}

	public long getPostConsumptionSilentTicks() {
		return postConsumptionSilentTicks;
	}
}
