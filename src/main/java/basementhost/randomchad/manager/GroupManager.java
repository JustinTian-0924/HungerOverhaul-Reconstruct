package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.ActionCost;
import basementhost.randomchad.data.ActionDecaySettings;
import basementhost.randomchad.data.EatingGroupSettings;
import basementhost.randomchad.data.GroupSettings;
import basementhost.randomchad.data.HealthRegenerationGroupSettings;
import basementhost.randomchad.data.MovementDecaySettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// Loads permission groups and returns the highest-priority group available to a player.
public final class GroupManager {
	private static final String GROUP_FILE_NAME = "group.yml";
	private static final String DEFAULT_GROUP_ID = "default";
	private static final String GENERATED_PERMISSION_PREFIX = "hungeroverhaul.group.";
	private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z0-9_-]+");

	private static final Comparator<Group> GROUP_SELECTION_ORDER = Comparator
			.comparingInt(Group::getPriority)
			.reversed()
			.thenComparing(Group::getId);

	private final HungerOverhaul plugin;
	private final Set<String> warnedPriorityTies = new HashSet<>();

	private Map<String, Group> groupsById = Map.of();
	private Group defaultGroup;

	public GroupManager(HungerOverhaul plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	// Existing settings are replaced only after the entire new file passes validation.
	public void reload() {
		File groupFile = prepareGroupFile();
		YamlConfiguration groupConfiguration = loadGroupConfiguration(groupFile);
		ParsedGroups parsedGroups = parseGroups(groupConfiguration);

		groupsById = parsedGroups.getGroupsById();
		defaultGroup = parsedGroups.getDefaultGroup();
		warnedPriorityTies.clear();

		plugin.getLogger().info("Loaded " + groupsById.size() + " HungerOverhaul permission groups.");
	}

	public boolean isLoaded() {
		return defaultGroup != null;
	}

	public Group getGroup(Player player) {
		Objects.requireNonNull(player, "player");
		requireLoaded();

		List<Group> matchingGroups = groupsById.values().stream()
				.filter(group -> !group.getId().equals(DEFAULT_GROUP_ID))
				.filter(group -> player.hasPermission(group.getPermission()))
				.sorted(GROUP_SELECTION_ORDER)
				.toList();

		if (matchingGroups.isEmpty()) {
			return defaultGroup;
		}

		Group selectedGroup = matchingGroups.get(0);
		warnAboutPriorityTie(player, matchingGroups, selectedGroup.getPriority());
		return selectedGroup;
	}

	public GroupSettings getSettings(Player player) {
		return getGroup(player).getSettings();
	}

	public Group getDefaultGroup() {
		requireLoaded();
		return defaultGroup;
	}

	public Collection<Group> getGroups() {
		requireLoaded();
		return groupsById.values().stream()
				.sorted(Comparator.comparing(Group::getId))
				.toList();
	}

	public Optional<Group> findGroup(String groupId) {
		requireLoaded();
		return Optional.ofNullable(groupsById.get(Objects.requireNonNull(groupId, "groupId")));
	}

	private File prepareGroupFile() {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
			throw new IllegalStateException("Could not create plugin data folder: " + dataFolder.getPath());
		}

		File groupFile = new File(dataFolder, GROUP_FILE_NAME);
		if (groupFile.isFile()) {
			return groupFile;
		}

		if (groupFile.exists()) {
			throw new IllegalStateException("Group configuration path is not a file: " + groupFile.getPath());
		}

		try {
			plugin.saveResource(GROUP_FILE_NAME, false);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Could not copy the bundled " + GROUP_FILE_NAME + ".", exception);
		}

		if (!groupFile.isFile()) {
			throw new IllegalStateException("Could not create group configuration: " + groupFile.getPath());
		}

		return groupFile;
	}

	private YamlConfiguration loadGroupConfiguration(File groupFile) {
		YamlConfiguration configuration = new YamlConfiguration();
		try {
			configuration.load(groupFile);
			return configuration;
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not load " + groupFile.getPath() + ".", exception);
		}
	}

	private ParsedGroups parseGroups(YamlConfiguration configuration) {
		ConfigurationSection groupsSection = configuration.getConfigurationSection("groups");
		if (groupsSection == null) {
			throw configurationError("The 'groups' section is missing or is not a section.");
		}

		Map<String, ConfigurationSection> sectionsById = collectGroupSections(groupsSection);
		ConfigurationSection defaultSection = sectionsById.get(DEFAULT_GROUP_ID);
		if (defaultSection == null) {
			throw configurationError("The required 'groups.default' section is missing.");
		}

		GroupSettings defaultSettings = readGroupSettings(DEFAULT_GROUP_ID, defaultSection, null);
		int defaultPriority = readPriority(defaultSection, DEFAULT_GROUP_ID, 0);
		Group parsedDefaultGroup = new Group(
				DEFAULT_GROUP_ID,
				defaultPriority,
				null,
				defaultSettings
		);

		Map<String, Group> parsedGroups = new LinkedHashMap<>();
		parsedGroups.put(DEFAULT_GROUP_ID, parsedDefaultGroup);

		for (Map.Entry<String, ConfigurationSection> entry : sectionsById.entrySet()) {
			String groupId = entry.getKey();
			if (groupId.equals(DEFAULT_GROUP_ID)) {
				continue;
			}

			ConfigurationSection groupSection = entry.getValue();
			GroupSettings settings = readGroupSettings(groupId, groupSection, defaultSettings);
			int priority = readPriority(groupSection, groupId, 0);
			String permission = readPermission(groupSection, groupId);
			parsedGroups.put(groupId, new Group(groupId, priority, permission, settings));
		}

		return new ParsedGroups(Map.copyOf(parsedGroups), parsedDefaultGroup);
	}

	private Map<String, ConfigurationSection> collectGroupSections(ConfigurationSection groupsSection) {
		Map<String, ConfigurationSection> sectionsById = new LinkedHashMap<>();

		for (String configuredId : groupsSection.getKeys(false)) {
			String groupId = configuredId.toLowerCase(Locale.ROOT);
			if (!GROUP_ID_PATTERN.matcher(groupId).matches()) {
				throw configurationError(
						"Group name '" + configuredId + "' may contain only lowercase letters, numbers, _ and -."
				);
			}

			if (!configuredId.equals(groupId)) {
				throw configurationError("Group name '" + configuredId + "' must use lowercase letters.");
			}

			ConfigurationSection groupSection = groupsSection.getConfigurationSection(configuredId);
			if (groupSection == null) {
				throw configurationError("Group '" + configuredId + "' is not a configuration section.");
			}

			if (sectionsById.put(groupId, groupSection) != null) {
				throw configurationError("Group '" + groupId + "' is configured more than once.");
			}
		}

		return sectionsById;
	}

	private GroupSettings readGroupSettings(
			String groupId,
			ConfigurationSection groupSection,
			GroupSettings fallback
	) {
		ConfigurationSection valuesSection = readGroupSubsection(groupSection, "values", groupId, fallback == null);
		ConfigurationSection decaySection = readGroupSubsection(groupSection, "decay", groupId, fallback == null);

		double maxNutrition = readValue(
				valuesSection,
				"values",
				"max-nutrition",
				fallback == null ? null : fallback.getMaxNutrition(),
				groupId
		);
		double maxFullness = readValue(
				valuesSection,
				"values",
				"max-fullness",
				fallback == null ? null : fallback.getMaxFullness(),
				groupId
		);
		double initialNutrition = readValue(
				valuesSection,
				"values",
				"initial-nutrition",
				fallback == null ? null : fallback.getInitialNutrition(),
				groupId
		);
		double initialFullness = readValue(
				valuesSection,
				"values",
				"initial-fullness",
				fallback == null ? null : fallback.getInitialFullness(),
				groupId
		);
		double nutritionDecayPerSecond = readValue(
				decaySection,
				"decay",
				"nutrition-per-second",
				fallback == null ? null : fallback.getNutritionDecayPerSecond(),
				groupId
		);
		double fullnessDecayPerSecond = readValue(
				decaySection,
				"decay",
				"fullness-per-second",
				fallback == null ? null : fallback.getFullnessDecayPerSecond(),
				groupId
		);
		MovementDecaySettings movementDecaySettings = readMovementDecaySettings(
				groupId,
				decaySection,
				fallback == null ? null : fallback.getMovementDecaySettings()
		);
		ActionDecaySettings actionDecaySettings = readActionDecaySettings(
				groupId,
				decaySection,
				fallback == null ? null : fallback.getActionDecaySettings()
		);
		HealthRegenerationGroupSettings healthRegenerationSettings = readHealthRegenerationSettings(
				groupId,
				groupSection,
				fallback == null ? null : fallback.getHealthRegenerationSettings()
		);
		EatingGroupSettings eatingSettings = readEatingSettings(
				groupId,
				groupSection,
				fallback == null ? null : fallback.getEatingSettings()
		);

		try {
			return new GroupSettings(
					maxNutrition,
					maxFullness,
					initialNutrition,
					initialFullness,
					nutritionDecayPerSecond,
					fullnessDecayPerSecond,
					movementDecaySettings,
					actionDecaySettings,
					healthRegenerationSettings,
					eatingSettings
			);
		} catch (IllegalArgumentException exception) {
			throw configurationError("Invalid values for group '" + groupId + "': " + exception.getMessage(), exception);
		}
	}

	private EatingGroupSettings readEatingSettings(
			String groupId,
			ConfigurationSection groupSection,
			EatingGroupSettings fallback
	) {
		ConfigurationSection eatingSection = readGroupSubsection(
				groupSection,
				"eating",
				groupId,
				fallback == null
		);
		double speedMultiplier = readValue(
				eatingSection,
				"eating",
				"speed-multiplier",
				fallback == null ? null : fallback.getSpeedMultiplier(),
				groupId
		);

		try {
			return new EatingGroupSettings(speedMultiplier);
		} catch (IllegalArgumentException exception) {
			throw configurationError(
					"Invalid eating values for group '" + groupId + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private HealthRegenerationGroupSettings readHealthRegenerationSettings(
			String groupId,
			ConfigurationSection groupSection,
			HealthRegenerationGroupSettings fallback
	) {
		ConfigurationSection regenerationSection = readGroupSubsection(
				groupSection,
				"health-regeneration",
				groupId,
				fallback == null
		);
		double healHpPerCycle = readValue(
				regenerationSection,
				"health-regeneration",
				"heal-hp-per-cycle",
				fallback == null ? null : fallback.getHealHpPerCycle(),
				groupId
		);
		double nutritionCost = readValue(
				regenerationSection,
				"health-regeneration",
				"nutrition-cost",
				fallback == null ? null : fallback.getNutritionCost(),
				groupId
		);
		double fullnessCost = readValue(
				regenerationSection,
				"health-regeneration",
				"fullness-cost",
				fallback == null ? null : fallback.getFullnessCost(),
				groupId
		);

		try {
			return new HealthRegenerationGroupSettings(healHpPerCycle, nutritionCost, fullnessCost);
		} catch (IllegalArgumentException exception) {
			throw configurationError(
					"Invalid health regeneration values for group '" + groupId + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private MovementDecaySettings readMovementDecaySettings(
			String groupId,
			ConfigurationSection decaySection,
			MovementDecaySettings fallback
	) {
		boolean required = fallback == null;
		ConfigurationSection movementSection = readNestedSubsection(
				decaySection,
				"movement",
				"groups." + groupId + ".decay.movement",
				required
		);
		ConfigurationSection sprintingSection = readNestedSubsection(
				movementSection,
				"sprinting",
				"groups." + groupId + ".decay.movement.sprinting",
				required
		);
		ConfigurationSection swimmingSection = readNestedSubsection(
				movementSection,
				"swimming",
				"groups." + groupId + ".decay.movement.swimming",
				required
		);

		double sprintingNutritionMultiplier = readValue(
				sprintingSection,
				"decay.movement.sprinting",
				"nutrition-multiplier",
				fallback == null ? null : fallback.getSprintingNutritionMultiplier(),
				groupId
		);
		double sprintingFullnessMultiplier = readValue(
				sprintingSection,
				"decay.movement.sprinting",
				"fullness-multiplier",
				fallback == null ? null : fallback.getSprintingFullnessMultiplier(),
				groupId
		);
		double swimmingNutritionMultiplier = readValue(
				swimmingSection,
				"decay.movement.swimming",
				"nutrition-multiplier",
				fallback == null ? null : fallback.getSwimmingNutritionMultiplier(),
				groupId
		);
		double swimmingFullnessMultiplier = readValue(
				swimmingSection,
				"decay.movement.swimming",
				"fullness-multiplier",
				fallback == null ? null : fallback.getSwimmingFullnessMultiplier(),
				groupId
		);

		try {
			return new MovementDecaySettings(
					sprintingNutritionMultiplier,
					sprintingFullnessMultiplier,
					swimmingNutritionMultiplier,
					swimmingFullnessMultiplier
			);
		} catch (IllegalArgumentException exception) {
			throw configurationError(
					"Invalid movement decay values for group '" + groupId + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private ActionDecaySettings readActionDecaySettings(
			String groupId,
			ConfigurationSection decaySection,
			ActionDecaySettings fallback
	) {
		boolean required = fallback == null;
		ConfigurationSection actionsSection = readNestedSubsection(
				decaySection,
				"actions",
				"groups." + groupId + ".decay.actions",
				required
		);

		ActionCost jumping = readActionCost(
				groupId,
				actionsSection,
				"jumping",
				fallback == null ? null : fallback.getJumping()
		);
		ActionCost attacking = readActionCost(
				groupId,
				actionsSection,
				"attacking",
				fallback == null ? null : fallback.getAttacking()
		);
		ActionCost takingDamage = readActionCost(
				groupId,
				actionsSection,
				"taking-damage",
				fallback == null ? null : fallback.getTakingDamage()
		);
		ActionCost breakingBlock = readActionCost(
				groupId,
				actionsSection,
				"breaking-block",
				fallback == null ? null : fallback.getBreakingBlock()
		);
		ActionCost placingBlock = readActionCost(
				groupId,
				actionsSection,
				"placing-block",
				fallback == null ? null : fallback.getPlacingBlock()
		);

		return new ActionDecaySettings(jumping, attacking, takingDamage, breakingBlock, placingBlock);
	}

	private ActionCost readActionCost(
			String groupId,
			ConfigurationSection actionsSection,
			String actionName,
			ActionCost fallback
	) {
		ConfigurationSection actionSection = readNestedSubsection(
				actionsSection,
				actionName,
				"groups." + groupId + ".decay.actions." + actionName,
				fallback == null
		);
		double nutritionCost = readValue(
				actionSection,
				"decay.actions." + actionName,
				"nutrition-cost",
				fallback == null ? null : fallback.getNutritionCost(),
				groupId
		);
		double fullnessCost = readValue(
				actionSection,
				"decay.actions." + actionName,
				"fullness-cost",
				fallback == null ? null : fallback.getFullnessCost(),
				groupId
		);

		try {
			return new ActionCost(nutritionCost, fullnessCost);
		} catch (IllegalArgumentException exception) {
			throw configurationError(
					"Invalid " + actionName + " costs for group '" + groupId + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private ConfigurationSection readNestedSubsection(
			ConfigurationSection parentSection,
			String subsectionName,
			String fullPath,
			boolean required
	) {
		if (parentSection != null) {
			ConfigurationSection subsection = parentSection.getConfigurationSection(subsectionName);
			if (subsection != null) {
				return subsection;
			}

			if (parentSection.contains(subsectionName)) {
				throw configurationError("The '" + fullPath + "' value must be a section.");
			}
		}

		if (required) {
			throw configurationError("The required '" + fullPath + "' section is missing.");
		}

		return null;
	}

	private ConfigurationSection readGroupSubsection(
			ConfigurationSection groupSection,
			String subsectionName,
			String groupId,
			boolean required
	) {
		ConfigurationSection subsection = groupSection.getConfigurationSection(subsectionName);
		if (subsection != null) {
			return subsection;
		}

		if (groupSection.contains(subsectionName)) {
			throw configurationError(
					"The 'groups." + groupId + "." + subsectionName + "' value must be a section."
			);
		}

		if (required) {
			throw configurationError(
					"The required 'groups." + groupId + "." + subsectionName + "' section is missing."
			);
		}

		return null;
	}

	private double readValue(
			ConfigurationSection settingsSection,
			String subsectionName,
			String valueName,
			Double fallback,
			String groupId
	) {
		Object configuredValue = settingsSection == null ? null : settingsSection.get(valueName);
		if (configuredValue == null) {
			if (fallback != null) {
				return fallback;
			}

			throw configurationError(
					"Required value 'groups." + groupId + "." + subsectionName + "." + valueName + "' is missing."
			);
		}

		if (!(configuredValue instanceof Number number)) {
			throw configurationError(
					"Value 'groups." + groupId + "." + subsectionName + "." + valueName + "' must be a number."
			);
		}

		return number.doubleValue();
	}

	private int readPriority(ConfigurationSection groupSection, String groupId, int fallback) {
		Object configuredPriority = groupSection.get("priority");
		if (configuredPriority == null) {
			return fallback;
		}

		if (!(configuredPriority instanceof Number number)) {
			throw configurationError("Priority for group '" + groupId + "' must be a whole number.");
		}

		double priority = number.doubleValue();
		if (!Double.isFinite(priority)
				|| priority != Math.rint(priority)
				|| priority < Integer.MIN_VALUE
				|| priority > Integer.MAX_VALUE) {
			throw configurationError("Priority for group '" + groupId + "' must be a whole number.");
		}

		return (int) priority;
	}

	private String readPermission(ConfigurationSection groupSection, String groupId) {
		Object configuredPermission = groupSection.get("permission");
		if (configuredPermission == null) {
			return GENERATED_PERMISSION_PREFIX + groupId;
		}

		if (!(configuredPermission instanceof String permission) || permission.isBlank()) {
			throw configurationError("Permission for group '" + groupId + "' must be non-empty text.");
		}

		return permission.trim();
	}

	private void warnAboutPriorityTie(Player player, List<Group> matchingGroups, int highestPriority) {
		List<String> tiedGroupIds = new ArrayList<>();
		for (Group group : matchingGroups) {
			if (group.getPriority() != highestPriority) {
				break;
			}

			tiedGroupIds.add(group.getId());
		}

		if (tiedGroupIds.size() < 2) {
			return;
		}

		String warningKey = highestPriority + ":" + String.join(",", tiedGroupIds);
		if (!warnedPriorityTies.add(warningKey)) {
			return;
		}

		plugin.getLogger().warning(
				"Player " + player.getName() + " matches equal-priority groups "
						+ String.join(", ", tiedGroupIds) + ". Using " + tiedGroupIds.get(0) + "."
		);
	}

	private void requireLoaded() {
		if (!isLoaded()) {
			throw new IllegalStateException("GroupManager has not loaded group.yml yet.");
		}
	}

	private IllegalStateException configurationError(String message) {
		return new IllegalStateException("Invalid " + GROUP_FILE_NAME + ": " + message);
	}

	private IllegalStateException configurationError(String message, Throwable cause) {
		return new IllegalStateException("Invalid " + GROUP_FILE_NAME + ": " + message, cause);
	}

	private static final class ParsedGroups {
		private final Map<String, Group> groupsById;
		private final Group defaultGroup;

		private ParsedGroups(Map<String, Group> groupsById, Group defaultGroup) {
			this.groupsById = groupsById;
			this.defaultGroup = defaultGroup;
		}

		private Map<String, Group> getGroupsById() {
			return groupsById;
		}

		private Group getDefaultGroup() {
			return defaultGroup;
		}
	}

	public static final class Group {
		private final String id;
		private final int priority;
		private final String permission;
		private final GroupSettings settings;

		private Group(String id, int priority, String permission, GroupSettings settings) {
			this.id = id;
			this.priority = priority;
			this.permission = permission;
			this.settings = settings;
		}

		public String getId() {
			return id;
		}

		public int getPriority() {
			return priority;
		}

		public String getPermission() {
			return permission;
		}

		public GroupSettings getSettings() {
			return settings;
		}
	}
}
