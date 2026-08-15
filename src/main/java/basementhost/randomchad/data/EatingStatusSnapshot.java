package basementhost.randomchad.data;

import java.util.Objects;

// Provides a read-only view of one player's current eating and digestion state.
public final class EatingStatusSnapshot {
	private final Activity activity;
	private final String foodId;
	private final double progressPercentage;
	private final long eatingRemainingTicks;
	private final long digestionRemainingTicks;
	private final long blockedMessageSilenceRemainingTicks;

	public EatingStatusSnapshot(
			Activity activity,
			String foodId,
			double progressPercentage,
			long eatingRemainingTicks,
			long digestionRemainingTicks,
			long blockedMessageSilenceRemainingTicks
	) {
		this.activity = Objects.requireNonNull(activity, "activity");
		if (activity != Activity.IDLE && (foodId == null || foodId.isBlank())) {
			throw new IllegalArgumentException("foodId is required while an eating attempt is active");
		}
		this.foodId = foodId;
		if (!Double.isFinite(progressPercentage)
				|| progressPercentage < 0.0
				|| progressPercentage > 100.0) {
			throw new IllegalArgumentException("progressPercentage must be from 0 to 100");
		}
		if (eatingRemainingTicks < 0L
				|| digestionRemainingTicks < 0L
				|| blockedMessageSilenceRemainingTicks < 0L) {
			throw new IllegalArgumentException("remaining ticks must not be negative");
		}

		this.progressPercentage = progressPercentage;
		this.eatingRemainingTicks = eatingRemainingTicks;
		this.digestionRemainingTicks = digestionRemainingTicks;
		this.blockedMessageSilenceRemainingTicks = blockedMessageSilenceRemainingTicks;
	}

	public Activity getActivity() {
		return activity;
	}

	public String getFoodId() {
		return foodId;
	}

	public double getProgressPercentage() {
		return progressPercentage;
	}

	public long getEatingRemainingTicks() {
		return eatingRemainingTicks;
	}

	public long getDigestionRemainingTicks() {
		return digestionRemainingTicks;
	}

	public long getBlockedMessageSilenceRemainingTicks() {
		return blockedMessageSilenceRemainingTicks;
	}

	public enum Activity {
		IDLE,
		STARTING,
		EATING
	}
}
