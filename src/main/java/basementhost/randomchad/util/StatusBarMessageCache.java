package basementhost.randomchad.util;

import basementhost.randomchad.data.PlayerData;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// Used only on the main thread; keeps the original rounding and placeholder escaping.
public final class StatusBarMessageCache {
	private final Map<UUID, Entry> entries = new HashMap<>();

	public Component getOrCreate(PlayerData data, Function<Map<String, String>, Component> renderer) {
		UUID playerId = data.getPlayerId();
		Entry previous = entries.get(playerId);
		if (previous != null && previous.matches(data)) {
			return previous.message();
		}
		Map<String, String> values = formatValues(data);
		Component message = previous != null && previous.values().equals(values)
				? previous.message() : renderer.apply(values);
		entries.put(playerId, new Entry(data.getNutrition(), data.getFullness(), data.getMaxNutrition(),
				data.getMaxFullness(), values, message));
		return message;
	}

	public void remove(UUID playerId) {
		entries.remove(playerId);
	}

	public void clear() {
		entries.clear();
	}

	public static Map<String, String> formatValues(PlayerData data) {
		return Map.of("nutrition", format(data.getNutrition()), "max_nutrition", format(data.getMaxNutrition()),
				"fullness", format(data.getFullness()), "max_fullness", format(data.getMaxFullness()));
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}

	private record Entry(double nutrition, double fullness, double maxNutrition, double maxFullness,
			Map<String, String> values, Component message) {
		private boolean matches(PlayerData data) {
			return Double.compare(nutrition, data.getNutrition()) == 0 && Double.compare(fullness, data.getFullness()) == 0
					&& Double.compare(maxNutrition, data.getMaxNutrition()) == 0 && Double.compare(maxFullness, data.getMaxFullness()) == 0;
		}
	}
}
