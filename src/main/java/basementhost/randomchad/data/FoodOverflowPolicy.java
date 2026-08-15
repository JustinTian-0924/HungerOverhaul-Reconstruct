package basementhost.randomchad.data;

import java.util.Locale;

// Defines whether a food is denied or capped when either player value would overflow.
public enum FoodOverflowPolicy {
	DENY,
	CLAMP;

	public static FoodOverflowPolicy fromConfigValue(String configuredValue) {
		if (configuredValue == null) {
			throw new IllegalArgumentException("overflow policy must not be null");
		}

		return switch (configuredValue.trim().toLowerCase(Locale.ROOT)) {
			case "deny" -> DENY;
			case "clamp" -> CLAMP;
			default -> throw new IllegalArgumentException("overflow policy must be 'deny' or 'clamp'");
		};
	}
}
