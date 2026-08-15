package basementhost.randomchad.data;

import java.util.Locale;

// Defines how one player value is transformed during a lifecycle event.
public enum LifecycleValueMode {
	KEEP,
	MINIMUM_PERCENTAGE,
	SET_PERCENTAGE,
	GROUP_INITIAL,
	PRESERVE_PERCENTAGE;

	public static LifecycleValueMode fromConfigValue(String configuredValue) {
		if (configuredValue == null) {
			throw new IllegalArgumentException("lifecycle value mode must not be null");
		}

		return switch (configuredValue.trim().toLowerCase(Locale.ROOT)) {
			case "keep" -> KEEP;
			case "minimum-percentage" -> MINIMUM_PERCENTAGE;
			case "set-percentage" -> SET_PERCENTAGE;
			case "group-initial" -> GROUP_INITIAL;
			case "preserve-percentage" -> PRESERVE_PERCENTAGE;
			default -> throw new IllegalArgumentException(
					"unsupported lifecycle value mode '" + configuredValue + "'"
			);
		};
	}

	public boolean requiresPercentage() {
		return this == MINIMUM_PERCENTAGE || this == SET_PERCENTAGE;
	}
}
