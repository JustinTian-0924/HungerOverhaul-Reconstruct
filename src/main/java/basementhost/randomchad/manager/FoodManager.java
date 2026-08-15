package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.FoodOverflowPolicy;
import basementhost.randomchad.data.FoodInterruptSettings;
import basementhost.randomchad.data.FoodSettings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Loads foods.yml and finds the settings used by a consumed item.
public final class FoodManager {
	private static final String FOOD_FILE_NAME = "foods.yml";
	private static final String FALLBACK_ID = "fallback";

	private final HungerOverhaul plugin;

	private Map<Material, FoodSettings> settingsByMaterial = Map.of();
	private FoodSettings fallbackSettings;

	public FoodManager(HungerOverhaul plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public void validateConfiguration() {
		File foodFile = prepareFoodFile();
		YamlConfiguration configuration = loadFoodConfiguration(foodFile);
		parseConfiguration(configuration);
	}

	// Existing settings are replaced only after the entire new file passes validation.
	public void reload() {
		File foodFile = prepareFoodFile();
		YamlConfiguration configuration = loadFoodConfiguration(foodFile);
		ParsedFoodConfiguration parsedConfiguration = parseConfiguration(configuration);

		settingsByMaterial = parsedConfiguration.getSettingsByMaterial();
		fallbackSettings = parsedConfiguration.getFallbackSettings();
		plugin.getLogger().info(
				"Loaded " + settingsByMaterial.size() + " explicit HungerOverhaul foods"
						+ (fallbackSettings == null ? "." : " with fallback enabled.")
		);
	}

	public Optional<FoodSettings> findFoodSettings(ItemStack item) {
		if (item == null || item.getType().isAir() || item.getType() == Material.CAKE) {
			return Optional.empty();
		}

		return findFoodSettings(item.getType());
	}

	public Optional<FoodSettings> findFoodSettings(Material material) {
		Objects.requireNonNull(material, "material");
		FoodSettings explicitSettings = settingsByMaterial.get(material);
		if (explicitSettings != null) {
			return Optional.of(explicitSettings);
		}

		if (fallbackSettings != null && shouldUseFallback(material)) {
			return Optional.of(fallbackSettings);
		}

		return Optional.empty();
	}

	private File prepareFoodFile() {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
			throw new IllegalStateException("Could not create plugin data folder: " + dataFolder.getPath());
		}

		File foodFile = new File(dataFolder, FOOD_FILE_NAME);
		if (foodFile.isFile()) {
			return foodFile;
		}

		if (foodFile.exists()) {
			throw new IllegalStateException("Food configuration path is not a file: " + foodFile.getPath());
		}

		try {
			plugin.saveResource(FOOD_FILE_NAME, false);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Could not copy the bundled " + FOOD_FILE_NAME + ".", exception);
		}

		if (!foodFile.isFile()) {
			throw new IllegalStateException("Could not create food configuration: " + foodFile.getPath());
		}

		return foodFile;
	}

	private YamlConfiguration loadFoodConfiguration(File foodFile) {
		YamlConfiguration configuration = new YamlConfiguration();
		try {
			configuration.load(foodFile);
			return configuration;
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not load " + foodFile.getPath() + ".", exception);
		}
	}

	private ParsedFoodConfiguration parseConfiguration(YamlConfiguration configuration) {
		FoodSettings parsedFallback = parseFallback(configuration);
		ConfigurationSection foodsSection = configuration.getConfigurationSection("foods");
		if (foodsSection == null) {
			throw configurationError("The required 'foods' section is missing or is not a section.");
		}

		Map<Material, FoodSettings> parsedSettings = new LinkedHashMap<>();
		for (String configuredId : foodsSection.getKeys(false)) {
			ConfigurationSection foodSection = foodsSection.getConfigurationSection(configuredId);
			if (foodSection == null) {
				throw configurationError("Food '" + configuredId + "' must be a configuration section.");
			}

			Material material = parseMaterial(configuredId);
			FoodSettings settings = readFoodSettings(configuredId, foodSection);
			if (parsedSettings.put(material, settings) != null) {
				throw configurationError("Food '" + configuredId + "' is configured more than once.");
			}
		}

		return new ParsedFoodConfiguration(Map.copyOf(parsedSettings), parsedFallback);
	}

	private FoodSettings parseFallback(YamlConfiguration configuration) {
		ConfigurationSection fallbackSection = configuration.getConfigurationSection(FALLBACK_ID);
		if (fallbackSection == null) {
			throw configurationError("The required 'fallback' section is missing or is not a section.");
		}

		boolean enabled = readBoolean(fallbackSection, "enabled", FALLBACK_ID);
		return enabled ? readFoodSettings(FALLBACK_ID, fallbackSection) : null;
	}

	private FoodSettings readFoodSettings(
			String foodId,
			ConfigurationSection foodSection
	) {
		double nutrition = readFiniteNumber(foodSection, "nutrition", foodId);
		double fullness = readFiniteNumber(foodSection, "fullness", foodId);
		Integer eatingTimeTicks = readOptionalInteger(foodSection, "eating-time-ticks", foodId);
		Long cooldownTicks = readOptionalLong(foodSection, "cooldown-ticks", foodId);
		FoodOverflowPolicy overflowPolicy = readOptionalOverflowPolicy(foodSection, foodId);
		FoodInterruptSettings interruptSettings = readInterruptSettings(foodSection, foodId);

		try {
			return new FoodSettings(
					foodId,
					nutrition,
					fullness,
					eatingTimeTicks,
					cooldownTicks,
					overflowPolicy,
					interruptSettings
			);
		} catch (IllegalArgumentException exception) {
			throw configurationError("Invalid values for food '" + foodId + "': " + exception.getMessage(), exception);
		}
	}

	private FoodInterruptSettings readInterruptSettings(
			ConfigurationSection foodSection,
			String foodId
	) {
		Object configuredInterrupt = foodSection.get("interrupt");
		if (configuredInterrupt == null) {
			return new FoodInterruptSettings(null, null, null);
		}

		ConfigurationSection interruptSection = foodSection.getConfigurationSection("interrupt");
		if (interruptSection == null) {
			throw configurationError("Value '" + foodId + ".interrupt' must be a section.");
		}

		return new FoodInterruptSettings(
				readOptionalBoolean(interruptSection, "on-damage", foodId + ".interrupt"),
				readOptionalBoolean(interruptSection, "on-move", foodId + ".interrupt"),
				readOptionalBoolean(interruptSection, "on-sneak-toggle", foodId + ".interrupt")
		);
	}

	private Material parseMaterial(String configuredId) {
		String normalizedId = configuredId.trim().toLowerCase(Locale.ROOT);
		if (!configuredId.equals(normalizedId)) {
			throw configurationError("Food ID '" + configuredId + "' must use lowercase letters without extra spaces.");
		}

		NamespacedKey namespacedKey = NamespacedKey.fromString(normalizedId);
		if (namespacedKey == null || !namespacedKey.getNamespace().equals(NamespacedKey.MINECRAFT)) {
			throw configurationError("Food ID '" + configuredId + "' must use the minecraft namespace.");
		}

		Material material = Material.matchMaterial(normalizedId);
		if (material == null || material.isLegacy() || !material.getKey().equals(namespacedKey)) {
			throw configurationError("Food ID '" + configuredId + "' is not a valid current Minecraft material.");
		}

		if (!material.isEdible() && material != Material.HONEY_BOTTLE && material != Material.CAKE) {
			throw configurationError("Material '" + configuredId + "' is not a supported food item.");
		}

		return material;
	}

	private boolean readBoolean(ConfigurationSection section, String path, String foodId) {
		Object configuredValue = section.get(path);
		if (!(configuredValue instanceof Boolean booleanValue)) {
			throw configurationError("Value '" + foodId + "." + path + "' must be true or false.");
		}

		return booleanValue;
	}

	private Boolean readOptionalBoolean(ConfigurationSection section, String path, String foodId) {
		Object configuredValue = section.get(path);
		if (configuredValue == null) {
			return null;
		}

		if (!(configuredValue instanceof Boolean booleanValue)) {
			throw configurationError("Value '" + foodId + "." + path + "' must be true or false.");
		}

		return booleanValue;
	}

	private double readFiniteNumber(ConfigurationSection section, String path, String foodId) {
		Object configuredValue = section.get(path);
		if (!(configuredValue instanceof Number number)) {
			throw configurationError("Value '" + foodId + "." + path + "' must be a number.");
		}

		double value = number.doubleValue();
		if (!Double.isFinite(value)) {
			throw configurationError("Value '" + foodId + "." + path + "' must be a finite number.");
		}

		return value;
	}

	private Integer readOptionalInteger(ConfigurationSection section, String path, String foodId) {
		Object configuredValue = section.get(path);
		if (configuredValue == null) {
			return null;
		}

		if (!(configuredValue instanceof Number number)) {
			throw configurationError("Value '" + foodId + "." + path + "' must be a whole number.");
		}

		try {
			return new BigDecimal(number.toString()).intValueExact();
		} catch (NumberFormatException | ArithmeticException exception) {
			throw configurationError(
					"Value '" + foodId + "." + path + "' must be a whole number within the integer range.",
					exception
			);
		}
	}

	private Long readOptionalLong(ConfigurationSection section, String path, String foodId) {
		Object configuredValue = section.get(path);
		if (configuredValue == null) {
			return null;
		}

		if (!(configuredValue instanceof Number number)) {
			throw configurationError("Value '" + foodId + "." + path + "' must be a whole number.");
		}

		try {
			return new BigDecimal(number.toString()).longValueExact();
		} catch (NumberFormatException | ArithmeticException exception) {
			throw configurationError(
					"Value '" + foodId + "." + path + "' must be a whole number within the long range.",
					exception
			);
		}
	}

	private FoodOverflowPolicy readOptionalOverflowPolicy(ConfigurationSection section, String foodId) {
		Object configuredValue = section.get("overflow-policy");
		if (configuredValue == null) {
			return null;
		}

		if (!(configuredValue instanceof String stringValue)) {
			throw configurationError("Value '" + foodId + ".overflow-policy' must be text.");
		}

		try {
			return FoodOverflowPolicy.fromConfigValue(stringValue);
		} catch (IllegalArgumentException exception) {
			throw configurationError(
					"Invalid overflow policy for food '" + foodId + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private boolean shouldUseFallback(Material material) {
		if (!material.isEdible()) {
			return false;
		}

		return material != Material.POTION
				&& material != Material.SPLASH_POTION
				&& material != Material.LINGERING_POTION
				&& material != Material.MILK_BUCKET;
	}

	private IllegalStateException configurationError(String message) {
		return new IllegalStateException("Invalid " + FOOD_FILE_NAME + ": " + message);
	}

	private IllegalStateException configurationError(String message, Throwable cause) {
		return new IllegalStateException("Invalid " + FOOD_FILE_NAME + ": " + message, cause);
	}

	private static final class ParsedFoodConfiguration {
		private final Map<Material, FoodSettings> settingsByMaterial;
		private final FoodSettings fallbackSettings;

		private ParsedFoodConfiguration(
				Map<Material, FoodSettings> settingsByMaterial,
				FoodSettings fallbackSettings
		) {
			this.settingsByMaterial = settingsByMaterial;
			this.fallbackSettings = fallbackSettings;
		}

		private Map<Material, FoodSettings> getSettingsByMaterial() {
			return settingsByMaterial;
		}

		private FoodSettings getFallbackSettings() {
			return fallbackSettings;
		}
	}
}
