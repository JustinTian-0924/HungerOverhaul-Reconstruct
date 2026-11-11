package basementhost.randomchad.util;

import basementhost.randomchad.data.FoodPostConsumeSettings;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Validates optional per-food actions before a configuration can become active.
public final class FoodPostConsumeParser {
	private FoodPostConsumeParser() {
	}

	public static FoodPostConsumeSettings read(ConfigurationSection foodSection, String foodId) {
		if (foodSection.get("post-consume") == null) {
			return FoodPostConsumeSettings.EMPTY;
		}
		String path = foodId + ".post-consume";
		ConfigurationSection section = foodSection.getConfigurationSection("post-consume");
		if (section == null) {
			throw configurationError("'" + path + "' must be a section.");
		}
		requireKnownFields(section, Set.of("effects", "commands"), path);

		List<PotionEffect> effects = new ArrayList<>();
		List<?> configuredEffects = readOptionalList(section, "effects", path);
		for (int index = 0; index < configuredEffects.size(); index++) {
			String effectPath = path + ".effects[" + index + "]";
			ConfigurationSection effect = readListSection(configuredEffects.get(index), effectPath);
			requireKnownFields(effect, Set.of("type", "duration", "amplifier", "ambient", "particles", "icon"), effectPath);
			Object typeValue = effect.get("type");
			NamespacedKey typeKey = typeValue instanceof String typeName ? NamespacedKey.fromString(typeName) : null;
			if (typeKey == null) {
				throw configurationError("'" + effectPath + ".type' must be an effect ID such as minecraft:regeneration.");
			}
			Integer duration = readOptionalInteger(effect, "duration", effectPath);
			Integer amplifier = readOptionalInteger(effect, "amplifier", effectPath);
			if (duration == null || duration <= 0) {
				throw configurationError("'" + effectPath + ".duration' must be a positive whole number of ticks.");
			}
			if (amplifier != null && (amplifier < 0 || amplifier > 255)) {
				throw configurationError("'" + effectPath + ".amplifier' must be between 0 and 255 (0 means level I).");
			}
			Boolean ambient = readOptionalBoolean(effect, "ambient", effectPath);
			Boolean particles = readOptionalBoolean(effect, "particles", effectPath);
			Boolean icon = readOptionalBoolean(effect, "icon", effectPath);
			PotionEffectType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(typeKey);
			if (type == null) {
				throw configurationError("Unknown potion effect '" + typeValue + "' at '" + effectPath + ".type'.");
			}
			effects.add(new PotionEffect(type, duration, amplifier == null ? 0 : amplifier,
					Boolean.TRUE.equals(ambient), !Boolean.FALSE.equals(particles), !Boolean.FALSE.equals(icon)));
		}

		List<FoodPostConsumeSettings.Command> commands = new ArrayList<>();
		List<?> configuredCommands = readOptionalList(section, "commands", path);
		for (int index = 0; index < configuredCommands.size(); index++) {
			String commandPath = path + ".commands[" + index + "]";
			Object value = configuredCommands.get(index);
			FoodPostConsumeSettings.Sender sender = FoodPostConsumeSettings.Sender.CONSOLE;
			String command;
			if (value instanceof String text) {
				command = text;
			} else {
				ConfigurationSection entry = readListSection(value, commandPath);
				requireKnownFields(entry, Set.of("sender", "command"), commandPath);
				if (!(entry.get("sender") instanceof String senderValue)) {
					throw configurationError("'" + commandPath + ".sender' must be console or player.");
				}
				sender = switch (senderValue) {
					case "console" -> FoodPostConsumeSettings.Sender.CONSOLE;
					case "player" -> FoodPostConsumeSettings.Sender.PLAYER;
					default -> throw configurationError("'" + commandPath + ".sender' must be console or player.");
				};
				if (!(entry.get("command") instanceof String text)) {
					throw configurationError("'" + commandPath + ".command' must be text.");
				}
				command = text;
			}
			try {
				commands.add(new FoodPostConsumeSettings.Command(sender, command));
			} catch (IllegalArgumentException exception) {
				throw configurationError("Invalid '" + commandPath + "': " + exception.getMessage(), exception);
			}
		}
		return new FoodPostConsumeSettings(effects, commands);
	}

	private static List<?> readOptionalList(ConfigurationSection section, String name, String path) {
		Object value = section.get(name);
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof List<?> list)) {
			throw configurationError("'" + path + "." + name + "' must be a list.");
		}
		return list;
	}

	private static ConfigurationSection readListSection(Object value, String path) {
		if (!(value instanceof Map<?, ?> map)) {
			throw configurationError("'" + path + "' must be a section.");
		}
		MemoryConfiguration section = new MemoryConfiguration();
		section.options().pathSeparator('\0');
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String name)) {
				throw configurationError("'" + path + "' must use text field names.");
			}
			section.set(name, entry.getValue());
		}
		return section;
	}

	private static void requireKnownFields(ConfigurationSection section, Set<String> fields, String path) {
		for (String field : section.getKeys(false)) {
			if (!fields.contains(field)) {
				throw configurationError("Unknown field '" + path + "." + field + "'.");
			}
		}
	}

	private static Integer readOptionalInteger(ConfigurationSection section, String name, String path) {
		Object value = section.get(name);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			try {
				return new BigDecimal(number.toString()).intValueExact();
			} catch (NumberFormatException | ArithmeticException ignored) {
				// Report invalid whole numbers with their exact configuration path.
			}
		}
		throw configurationError("'" + path + "." + name + "' must be a whole number within the integer range.");
	}

	private static Boolean readOptionalBoolean(ConfigurationSection section, String name, String path) {
		Object value = section.get(name);
		if (value == null || value instanceof Boolean) {
			return (Boolean) value;
		}
		throw configurationError("'" + path + "." + name + "' must be true or false.");
	}

	private static IllegalStateException configurationError(String message) {
		return new IllegalStateException("Invalid foods.yml: " + message);
	}

	private static IllegalStateException configurationError(String message, Throwable cause) {
		return new IllegalStateException("Invalid foods.yml: " + message, cause);
	}
}
