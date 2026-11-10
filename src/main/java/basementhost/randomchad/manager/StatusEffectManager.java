package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.StatusEffectDefinition;
import basementhost.randomchad.data.StatusEffectsConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

// Loads and selects the global nutrition and fullness status ranges.
public final class StatusEffectManager {
	private static final String FILE_NAME = "status-effects.yml";
	private static final Pattern EFFECT_ID_PATTERN = Pattern.compile("[a-z0-9_-]+");

	private final HungerOverhaul plugin;
	private StatusEffectsConfiguration configuration;

	public StatusEffectManager(HungerOverhaul plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public void loadInitialConfiguration() {
		applyConfiguration(parseConfiguration(loadEffectFile(prepareEffectFile())));
	}

	public StatusEffectsConfiguration readConfiguration() {
		return parseConfiguration(loadEffectFile(new File(plugin.getDataFolder(), FILE_NAME)));
	}

	public void applyConfiguration(StatusEffectsConfiguration parsedConfiguration) {
		configuration = parsedConfiguration;

		plugin.getLogger().info(
				"Loaded " + configuration.getFullnessEffects().size() + " fullness ranges and "
						+ configuration.getNutritionEffects().size() + " nutrition ranges."
		);
	}

	public boolean isLoaded() {
		return configuration != null;
	}

	public StatusEffectsConfiguration getConfiguration() {
		requireLoaded();
		return configuration;
	}

	public StatusEffectDefinition getFullnessEffect(double percentage) {
		StatusEffectsConfiguration currentConfiguration = getConfiguration();
		return currentConfiguration.isEnabled()
				? currentConfiguration.findFullnessEffect(percentage)
				: null;
	}

	public StatusEffectDefinition getNutritionEffect(double percentage) {
		StatusEffectsConfiguration currentConfiguration = getConfiguration();
		return currentConfiguration.isEnabled()
				? currentConfiguration.findNutritionEffect(percentage)
				: null;
	}

	private File prepareEffectFile() {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
			throw new IllegalStateException("Could not create plugin data folder: " + dataFolder.getPath());
		}

		File effectFile = new File(dataFolder, FILE_NAME);
		if (effectFile.isFile()) {
			return effectFile;
		}

		if (effectFile.exists()) {
			throw new IllegalStateException("Status effect configuration path is not a file: " + effectFile.getPath());
		}

		try {
			plugin.saveResource(FILE_NAME, false);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Could not copy the bundled " + FILE_NAME + ".", exception);
		}

		if (!effectFile.isFile()) {
			throw new IllegalStateException("Could not create status effect configuration: " + effectFile.getPath());
		}

		return effectFile;
	}

	private YamlConfiguration loadEffectFile(File effectFile) {
		YamlConfiguration loadedFile = new YamlConfiguration();
		try {
			loadedFile.load(effectFile);
			return loadedFile;
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not load " + effectFile.getPath() + ".", exception);
		}
	}

	private StatusEffectsConfiguration parseConfiguration(YamlConfiguration loadedFile) {
		ConfigurationSection settingsSection = requireSection(loadedFile, "settings");
		ConfigurationSection limitsSection = requireSection(settingsSection, "attribute-modifier-limits");
		boolean enabled = readBoolean(settingsSection, "enabled");
		long updateIntervalTicks = readLong(settingsSection, "update-interval-ticks");
		double minimumModifier = readDouble(limitsSection, "minimum");
		double maximumModifier = readDouble(limitsSection, "maximum");

		List<StatusEffectDefinition> fullnessEffects = readEffects(
				loadedFile,
				"fullness-effects",
				minimumModifier,
				maximumModifier
		);
		List<StatusEffectDefinition> nutritionEffects = readEffects(
				loadedFile,
				"nutrition-effects",
				minimumModifier,
				maximumModifier
		);

		try {
			return new StatusEffectsConfiguration(
					enabled,
					updateIntervalTicks,
					minimumModifier,
					maximumModifier,
					fullnessEffects,
					nutritionEffects
			);
		} catch (IllegalArgumentException exception) {
			throw configurationError(exception.getMessage(), exception);
		}
	}

	private List<StatusEffectDefinition> readEffects(
			YamlConfiguration loadedFile,
			String categoryName,
			double minimumModifier,
			double maximumModifier
	) {
		ConfigurationSection categorySection = requireSection(loadedFile, categoryName);
		List<StatusEffectDefinition> effects = new ArrayList<>();
		for (Map.Entry<String, Object> entry : categorySection.getValues(false).entrySet()) {
			String configuredId = entry.getKey();
			String effectId = configuredId.toLowerCase(Locale.ROOT);
			if (!configuredId.equals(effectId) || !EFFECT_ID_PATTERN.matcher(effectId).matches()) {
				throw configurationError(
						"Effect name '" + configuredId + "' in '" + categoryName
								+ "' may contain only lowercase letters, numbers, _ and -."
				);
			}

			ConfigurationSection effectSection = categorySection.getConfigurationSection(configuredId);
			if (effectSection == null) {
				throw configurationError("Effect '" + categoryName + "." + configuredId + "' must be a section.");
			}

			effects.add(readEffect(
					effectId,
					effectSection,
					categoryName,
					minimumModifier,
					maximumModifier
			));
		}

		return effects;
	}

	private StatusEffectDefinition readEffect(
			String effectId,
			ConfigurationSection effectSection,
			String categoryName,
			double minimumModifier,
			double maximumModifier
	) {
		ConfigurationSection attributesSection = requireSection(effectSection, "attributes");
		double minimumPercentage = readDouble(effectSection, "minimum-percentage");
		double maximumPercentage = readDouble(effectSection, "maximum-percentage");
		double decayMultiplier = readDouble(effectSection, "decay-multiplier");
		double healthRegenerationModifier = readDouble(effectSection, "health-regeneration-modifier");
		double movementSpeedModifier = readDouble(attributesSection, "movement-speed");
		double blockBreakSpeedModifier = readDouble(attributesSection, "block-break-speed");
		double attackDamageModifier = readDouble(attributesSection, "attack-damage");

		try {
			return new StatusEffectDefinition(
					effectId,
					minimumPercentage,
					maximumPercentage,
					decayMultiplier,
					healthRegenerationModifier,
					movementSpeedModifier,
					blockBreakSpeedModifier,
					attackDamageModifier,
					minimumModifier,
					maximumModifier
			);
		} catch (IllegalArgumentException exception) {
			throw configurationError(
					"Invalid effect '" + categoryName + "." + effectId + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private ConfigurationSection requireSection(ConfigurationSection parent, String path) {
		ConfigurationSection section = parent.getConfigurationSection(path);
		if (section != null) {
			return section;
		}

		if (parent.contains(path)) {
			throw configurationError("Value '" + joinPath(parent.getCurrentPath(), path) + "' must be a section.");
		}

		throw configurationError("Required section '" + joinPath(parent.getCurrentPath(), path) + "' is missing.");
	}

	private boolean readBoolean(ConfigurationSection section, String path) {
		Object value = section.get(path);
		if (!(value instanceof Boolean booleanValue)) {
			throw configurationError("Value '" + joinPath(section.getCurrentPath(), path) + "' must be true or false.");
		}

		return booleanValue;
	}

	private long readLong(ConfigurationSection section, String path) {
		Object value = section.get(path);
		if (!(value instanceof Number number)) {
			throw configurationError("Value '" + joinPath(section.getCurrentPath(), path) + "' must be a whole number.");
		}

		try {
			return new BigDecimal(number.toString()).longValueExact();
		} catch (NumberFormatException | ArithmeticException exception) {
			throw configurationError(
					"Value '" + joinPath(section.getCurrentPath(), path) + "' must be a whole number in range.",
					exception
			);
		}
	}

	private double readDouble(ConfigurationSection section, String path) {
		Object value = section.get(path);
		if (!(value instanceof Number number)) {
			throw configurationError("Value '" + joinPath(section.getCurrentPath(), path) + "' must be a number.");
		}

		return number.doubleValue();
	}

	private String joinPath(String parentPath, String childPath) {
		return parentPath == null || parentPath.isBlank() ? childPath : parentPath + "." + childPath;
	}

	private void requireLoaded() {
		if (!isLoaded()) {
			throw new IllegalStateException("StatusEffectManager has not loaded " + FILE_NAME + " yet.");
		}
	}

	private IllegalStateException configurationError(String message) {
		return new IllegalStateException("Invalid " + FILE_NAME + ": " + message);
	}

	private IllegalStateException configurationError(String message, Throwable cause) {
		return new IllegalStateException("Invalid " + FILE_NAME + ": " + message, cause);
	}
}
