package basementhost.randomchad.data;

import java.util.Objects;

// Describes how one consumed food changes HungerOverhaul values.
public final class FoodSettings {
	private final String id;
	private final double nutritionChange;
	private final double fullnessChange;
	private final Integer eatingTimeTicks;
	private final Long cooldownTicks;
	private final FoodOverflowPolicy overflowPolicy;
	private final FoodInterruptSettings interruptSettings;
	private final FoodPostConsumeSettings postConsumeSettings;

	public FoodSettings(
			String id,
			double nutritionChange,
			double fullnessChange,
			Integer eatingTimeTicks,
			Long cooldownTicks,
			FoodOverflowPolicy overflowPolicy,
			FoodInterruptSettings interruptSettings,
			FoodPostConsumeSettings postConsumeSettings
	) {
		this.id = requireId(id);
		this.nutritionChange = requireFinite(nutritionChange, "nutritionChange");
		this.fullnessChange = requireFinite(fullnessChange, "fullnessChange");
		if (eatingTimeTicks != null && eatingTimeTicks <= 0) {
			throw new IllegalArgumentException("eatingTimeTicks must be greater than zero when configured");
		}
		if (cooldownTicks != null && cooldownTicks < 0L) {
			throw new IllegalArgumentException("cooldownTicks must not be negative when configured");
		}

		this.eatingTimeTicks = eatingTimeTicks;
		this.cooldownTicks = cooldownTicks;
		this.overflowPolicy = overflowPolicy;
		this.interruptSettings = Objects.requireNonNull(interruptSettings, "interruptSettings");
		this.postConsumeSettings = Objects.requireNonNull(postConsumeSettings, "postConsumeSettings");
	}

	public String getId() {
		return id;
	}

	public double getNutritionChange() {
		return nutritionChange;
	}

	public double getFullnessChange() {
		return fullnessChange;
	}

	public Integer getEatingTimeTicks() {
		return eatingTimeTicks;
	}

	public Long getCooldownTicks() {
		return cooldownTicks;
	}

	public FoodOverflowPolicy getOverflowPolicy() {
		return overflowPolicy;
	}

	public FoodInterruptSettings getInterruptSettings() {
		return interruptSettings;
	}

	public FoodPostConsumeSettings getPostConsumeSettings() {
		return postConsumeSettings;
	}

	private static String requireId(String id) {
		Objects.requireNonNull(id, "id");
		if (id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}

		return id;
	}

	private static double requireFinite(double value, String valueName) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(valueName + " must be a finite number");
		}

		return value;
	}
}
