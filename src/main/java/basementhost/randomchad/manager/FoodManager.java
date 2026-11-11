package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.compat.craftengine.CraftEngineItemLookup;
import basementhost.randomchad.data.CustomModelDataMatch;
import basementhost.randomchad.data.FoodOverflowPolicy;
import basementhost.randomchad.data.FoodInterruptSettings;
import basementhost.randomchad.data.FoodPostConsumeSettings;
import basementhost.randomchad.data.FoodSettings;
import basementhost.randomchad.util.FoodPostConsumeParser;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

// Loads foods.yml and finds the settings used by a consumed item.
public final class FoodManager {
	private static final String FOOD_FILE_NAME = "foods.yml";
	private static final String FALLBACK_ID = "fallback";
	private static final String CUSTOM_FOODS = "custom-foods";
	private static final Set<String> MODEL_DATA_FIELDS = Set.of("floats", "flags", "strings", "colors");

	private final HungerOverhaul plugin;
	private final CraftEngineItemLookup craftEngineItemLookup;

	private Map<Material, FoodSettings> settingsByMaterial = Map.of();
	private Map<String, FoodSettings> craftEngineSettingsById = Map.of();
	private Map<Material, List<CustomFoodRule>> customFoodsByMaterial = Map.of();
	private FoodSettings fallbackSettings;

	public FoodManager(HungerOverhaul plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.craftEngineItemLookup = new CraftEngineItemLookup(plugin.getServer().getPluginManager(), plugin.getLogger());
	}

	public void loadInitialConfiguration() {
		applyConfiguration(parseConfiguration(loadFoodConfiguration(prepareFoodFile())));
	}

	public ParsedFoodConfiguration readConfiguration() {
		return parseConfiguration(loadFoodConfiguration(new File(plugin.getDataFolder(), FOOD_FILE_NAME)));
	}

	public void applyConfiguration(ParsedFoodConfiguration parsedConfiguration) {
		settingsByMaterial = parsedConfiguration.getSettingsByMaterial();
		craftEngineSettingsById = parsedConfiguration.craftEngineSettingsById;
		customFoodsByMaterial = parsedConfiguration.customFoodsByMaterial;
		fallbackSettings = parsedConfiguration.getFallbackSettings();
		craftEngineItemLookup.refresh(!craftEngineSettingsById.isEmpty());
		plugin.getLogger().info(
				"Loaded " + settingsByMaterial.size() + " material rules and "
						+ craftEngineSettingsById.size() + " CraftEngine ID rules and "
						+ customFoodsByMaterial.values().stream().mapToInt(List::size).sum() + " model rules"
						+ (fallbackSettings == null ? "." : " with fallback enabled.")
		);
	}

	public Optional<FoodSettings> findFoodSettings(ItemStack item) {
		if (item == null || item.isEmpty() || !item.hasData(DataComponentTypes.CONSUMABLE)) {
			return Optional.empty();
		}

		if (!craftEngineSettingsById.isEmpty()) {
			String craftEngineId = craftEngineItemLookup.findId(item);
			FoodSettings craftEngineSettings = craftEngineId == null ? null : craftEngineSettingsById.get(craftEngineId);
			if (craftEngineSettings != null) {
				return Optional.of(craftEngineSettings);
			}
		}

		Material material = item.getType();
		List<CustomFoodRule> customRules = customFoodsByMaterial.get(material);
		if (customRules != null) {
			Key itemModel = item.isDataOverridden(DataComponentTypes.ITEM_MODEL)
					? item.getData(DataComponentTypes.ITEM_MODEL)
					: null;
			CustomModelData modelData = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
			for (CustomFoodRule rule : customRules) {
				if (rule.selector().matches(itemModel, modelData)) {
					return Optional.of(rule.settings());
				}
			}
		}

		return material == Material.CAKE ? Optional.empty() : findFoodSettings(material);
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
		// Read namespaced IDs literally, including any dots in the namespace or path.
		configuration.options().pathSeparator('\0');
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
		Map<String, FoodSettings> parsedCraftEngineSettings = new LinkedHashMap<>();
		for (String configuredId : foodsSection.getKeys(false)) {
			ConfigurationSection foodSection = foodsSection.getConfigurationSection(configuredId);
			if (foodSection == null) {
				throw configurationError("Food '" + configuredId + "' must be a configuration section.");
			}

			NamespacedKey itemId = NamespacedKey.fromString(configuredId);
			if (itemId == null) {
				throw configurationError("Food '" + configuredId + "' must use a valid lowercase item ID.");
			}
			if (!NamespacedKey.MINECRAFT.equals(itemId.getNamespace())) {
				parsedCraftEngineSettings.put(itemId.asString(), readFoodSettings(itemId.asString(), foodSection));
				continue;
			}

			Material material = parseMaterial(configuredId, "foods." + configuredId);
			if (!material.isEdible() && material != Material.HONEY_BOTTLE && material != Material.CAKE) {
				throw configurationError("Material '" + configuredId + "' is not a supported food item.");
			}
			FoodSettings settings = readFoodSettings(configuredId, foodSection);
			if (parsedSettings.put(material, settings) != null) {
				throw configurationError("Food '" + configuredId + "' is configured more than once.");
			}
		}

		return new ParsedFoodConfiguration(
				Map.copyOf(parsedSettings),
				Map.copyOf(parsedCraftEngineSettings),
				readCustomFoods(configuration),
				parsedFallback
		);
	}

	private Map<Material, List<CustomFoodRule>> readCustomFoods(YamlConfiguration configuration) {
		Object configured = configuration.get(CUSTOM_FOODS);
		if (configured == null) {
			return Map.of();
		}
		if (!(configured instanceof ConfigurationSection customFoods)) {
			throw configurationError("'" + CUSTOM_FOODS + "' must be a section.");
		}

		Map<Material, Map<CustomFoodSelector, FoodSettings>> rulesByMaterial = new LinkedHashMap<>();
		for (String id : customFoods.getKeys(false)) {
			String path = CUSTOM_FOODS + "." + id;
			if (!id.matches("[a-z0-9_-]+")) {
				throw configurationError("'" + path + "' must use lowercase letters, digits, underscores or hyphens.");
			}
			ConfigurationSection section = customFoods.getConfigurationSection(id);
			if (section == null) {
				throw configurationError("'" + path + "' must be a section.");
			}

			Object materialValue = section.get("material");
			if (!(materialValue instanceof String materialId)) {
				throw configurationError("'" + path + ".material' must be a Minecraft material ID.");
			}
			Material material = parseMaterial(materialId, path + ".material");
			CustomFoodSelector selector = readCustomFoodSelector(section, path);
			FoodSettings settings = readFoodSettings(path, section);
			Map<CustomFoodSelector, FoodSettings> materialRules = rulesByMaterial.computeIfAbsent(
					material, ignored -> new LinkedHashMap<>()
			);
			FoodSettings duplicate = materialRules.putIfAbsent(selector, settings);
			if (duplicate != null) {
				throw configurationError("'" + path + "' has the same selector as '" + duplicate.getId() + "'.");
			}
		}

		Map<Material, List<CustomFoodRule>> parsed = new LinkedHashMap<>();
		rulesByMaterial.forEach((material, rules) -> parsed.put(material, rules.entrySet().stream()
				.map(entry -> new CustomFoodRule(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparingInt(rule -> rule.selector().priority()))
				.toList()));
		return Map.copyOf(parsed);
	}

	private CustomFoodSelector readCustomFoodSelector(ConfigurationSection section, String path) {
		Object modelValue = section.get("item-model");
		NamespacedKey itemModel = null;
		if (modelValue != null) {
			if (!(modelValue instanceof String modelId) || !modelId.contains(":")) {
				throw configurationError("'" + path + ".item-model' must be a namespace:id.");
			}
			itemModel = NamespacedKey.fromString(modelId);
			if (itemModel == null || !itemModel.asString().equals(modelId)) {
				throw configurationError("'" + path + ".item-model' must be a valid lowercase namespace:id.");
			}
		}

		CustomModelDataMatch modelData = readCustomModelData(section.get("custom-model-data"), path + ".custom-model-data");
		if (itemModel == null && modelData == null) {
			throw configurationError("'" + path + "' needs item-model, custom-model-data, or both.");
		}
		return new CustomFoodSelector(itemModel, modelData);
	}

	private CustomModelDataMatch readCustomModelData(Object value, String path) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return new CustomModelDataMatch(readModelFloat(value, path), null, null, null, null);
		}
		if (!(value instanceof ConfigurationSection section)) {
			throw configurationError("'" + path + "' must be a number or a section.");
		}
		for (String field : section.getKeys(false)) {
			if (!MODEL_DATA_FIELDS.contains(field)) {
				throw configurationError("Unknown CustomModelData field '" + path + "." + field + "'.");
			}
		}

		List<Float> floats = readModelList(section, "floats", path, this::readModelFloat);
		List<Boolean> flags = readModelList(section, "flags", path, (entry, field) -> {
			if (entry instanceof Boolean flag) {
				return flag;
			}
			throw configurationError("'" + field + "' must be true or false.");
		});
		List<String> strings = readModelList(section, "strings", path, (entry, field) -> {
			if (entry instanceof String text) {
				return text;
			}
			throw configurationError("'" + field + "' must be text.");
		});
		List<Color> colors = readModelList(section, "colors", path, this::readModelColor);
		if (Stream.of(floats, flags, strings, colors).noneMatch(list -> list != null && !list.isEmpty())) {
			throw configurationError("'" + path + "' needs at least one non-empty model data list.");
		}
		return new CustomModelDataMatch(null, floats, flags, strings, colors);
	}

	private <T> List<T> readModelList(
			ConfigurationSection section,
			String name,
			String path,
			BiFunction<Object, String, T> readEntry
	) {
		Object configured = section.get(name);
		if (configured == null) {
			return null;
		}
		String listPath = path + "." + name;
		if (!(configured instanceof List<?> list)) {
			throw configurationError("'" + listPath + "' must be a list.");
		}

		List<T> values = new ArrayList<>(list.size());
		for (int index = 0; index < list.size(); index++) {
			values.add(readEntry.apply(list.get(index), listPath + "[" + index + "]"));
		}
		return List.copyOf(values);
	}

	private float readModelFloat(Object value, String path) {
		if (value instanceof Number number && Float.isFinite(number.floatValue())) {
			return number.floatValue();
		}
		throw configurationError("'" + path + "' must be a finite Minecraft float.");
	}

	private Color readModelColor(Object value, String path) {
		if (value instanceof String text && text.matches("#[0-9a-fA-F]{6}")) {
			return Color.fromRGB(Integer.parseInt(text.substring(1), 16));
		}
		if (value instanceof Number number) {
			try {
				int rgb = new BigDecimal(number.toString()).intValueExact();
				if (rgb >= 0 && rgb <= 0xFFFFFF) {
					return Color.fromRGB(rgb);
				}
			} catch (NumberFormatException | ArithmeticException ignored) {
				// Report invalid integer and out-of-range RGB values with the same field path.
			}
		}
		throw configurationError("'" + path + "' must be an RGB integer or a quoted #RRGGBB color.");
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
		FoodPostConsumeSettings postConsumeSettings = FoodPostConsumeParser.read(foodSection, foodId);

		try {
			return new FoodSettings(
					foodId,
					nutrition,
					fullness,
					eatingTimeTicks,
					cooldownTicks,
					overflowPolicy,
					interruptSettings,
					postConsumeSettings
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

	private Material parseMaterial(String configuredId, String path) {
		String normalizedId = configuredId.trim().toLowerCase(Locale.ROOT);
		if (!configuredId.equals(normalizedId)) {
			throw configurationError("'" + path + "' must use lowercase material IDs without extra spaces.");
		}

		NamespacedKey namespacedKey = NamespacedKey.fromString(normalizedId);
		if (namespacedKey == null || !namespacedKey.getNamespace().equals(NamespacedKey.MINECRAFT)) {
			throw configurationError("'" + path + "' must use the minecraft namespace.");
		}

		Material material = Material.matchMaterial(normalizedId);
		if (material == null || material.isLegacy() || !material.isItem() || !material.getKey().equals(namespacedKey)) {
			throw configurationError("'" + path + "' is not a current Minecraft item material: " + configuredId);
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

	public static final class ParsedFoodConfiguration {
		private final Map<Material, FoodSettings> settingsByMaterial;
		private final Map<String, FoodSettings> craftEngineSettingsById;
		private final Map<Material, List<CustomFoodRule>> customFoodsByMaterial;
		private final FoodSettings fallbackSettings;

		private ParsedFoodConfiguration(
				Map<Material, FoodSettings> settingsByMaterial,
				Map<String, FoodSettings> craftEngineSettingsById,
				Map<Material, List<CustomFoodRule>> customFoodsByMaterial,
				FoodSettings fallbackSettings
		) {
			this.settingsByMaterial = settingsByMaterial;
			this.craftEngineSettingsById = craftEngineSettingsById;
			this.customFoodsByMaterial = customFoodsByMaterial;
			this.fallbackSettings = fallbackSettings;
		}

		private Map<Material, FoodSettings> getSettingsByMaterial() {
			return settingsByMaterial;
		}

		private FoodSettings getFallbackSettings() {
			return fallbackSettings;
		}
	}

	private record CustomFoodRule(CustomFoodSelector selector, FoodSettings settings) {
	}

	private record CustomFoodSelector(NamespacedKey itemModel, CustomModelDataMatch modelData) {
		private int priority() {
			if (itemModel == null) {
				return 2;
			}
			return modelData == null ? 1 : 0;
		}

		private boolean matches(Key actualModel, CustomModelData actualModelData) {
			return (itemModel == null || (actualModel != null && itemModel.asString().equals(actualModel.asString())))
					&& (modelData == null || modelData.matches(actualModelData));
		}
	}
}
